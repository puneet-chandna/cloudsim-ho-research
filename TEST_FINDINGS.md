# Test audit findings

This pass strengthens tests and records defects without changing application or
validator behavior. The findings below remain open; a green default suite does
not mean they have been fixed. Confirmed on Linux on 2026-10-04, after macOS
support commit `14dcbba`. Native Apple Silicon execution requires macOS CI.

## FROZEN-METRICS-1: coherent forged frozen metrics pass validation

**Severity: high for research evidence integrity. Status: confirmed, not fixed.**

`scripts/statistics_validator.py:validate_raw` checks positive energy, joule/kWh
conversion, completion/SLA count consistency and horizon bounds, but does not
independently recompute those metrics from the frozen workload and placement.
Its analysis checks recompute summaries from the supplied raw metrics. Refreshed
SHA-256 values plus internally consistent forged metrics therefore pass.

Three mutations of a valid 40-case Explore run were accepted:

| Coherent mutation | First Micro/HO case before → after | Consistency maintained |
| --- | --- | --- |
| Multiply every energy by 1.5 | 47,152.8125 J → 70,729.21875 J | kWh conversion and summary energy mean/SD |
| Multiply every horizon by 1.5 | 111 s → 166.5 s | Positive horizon below the censor deadline |
| Declare one failed cloudlet in every case | completed 10/failed 0/SLA 0 → completed 9/failed 1/SLA 0.1 | Requested-cloudlet denominator, count totals and summary SLA mean/SD |

Each mutation retains canonical inputs, placements, seeds, optimizer traces and
case identities, and recalculates all recorded file hashes. Validation still
prints `VALID`. This can conceal a regression in native metric computation or
accept coherently altered evidence. It does not show that the current simulator
produces those incorrect values: an independent calculation of the retained
450-case Research run found exact energy agreement, expected horizons and full
completion with zero failures/censoring/SLA violations.

Reproduce on Linux or Apple Silicon with a valid Explore dataset:

```sh
# Use the managed JDK after ./cloudsim.sh --setup, or an equivalent full JDK 21.
.cloudsim/jdk/bin/java -Xmx256m -jar target/cloudsim-ho-research-v2-2.0.0.jar \
  --profile explore --output-dir /tmp/cloudsim-validator-gap
python3 -B scripts/probe_frozen_metric_gaps.py /tmp/cloudsim-validator-gap/<printed-run-directory>
```

The opt-in probe first validates the original dataset, mutates temporary copies
and reports **three expected failures** plus `REPRODUCED FROZEN-METRICS-1`.
That result records acceptance of corrupt evidence; it is not an acceptance
PASS. Missing/invalid baseline data and unexpected errors fail the probe.
A future rejection produces an unexpected success, requiring review of the
finding and conversion of the corresponding probe to an ordinary regression
test. The probe stays outside default unittest discovery so known defects do
not turn the default acceptance suite into a misleading expected-failure gate.

Follow-up acceptance criteria: independently derive the frozen model's horizon,
completion/SLA counts and host energy integral from canonical inputs plus stored
placement; reject all three coherent mutations with specific metric errors,
and retain acceptance of genuine Smoke, Explore and all 450 Research cases.
The separate stress validator already independently checks its static model;
this finding concerns frozen schema-2 validation.

## TEST-ORACLE-1: native cleanup test can mistake a failed probe for exit

**Severity: medium for test reliability. Status: confirmed, not fixed.**

Independent review found this weakness in the newly added
`scripts/test_macos.py:NativeMacCleanupTests.alive` helper: it treats every
nonzero `/bin/ps` exit status as a dead process. A probe failure with status 2
therefore makes `wait_dead` pass, potentially hiding an orphan after cleanup.
This finding concerns the test oracle; it does not demonstrate a failure in
application process cleanup. It remains unchanged under this pass's instruction
to document bugs rather than fix them.

Reproduce without creating or killing a process:

```sh
.cloudsim/venv/bin/python -B - <<'PY'
import sys
from types import SimpleNamespace
from unittest.mock import patch
sys.path.insert(0, 'scripts')
import test_macos
case = test_macos.NativeMacCleanupTests()
with patch.object(test_macos.subprocess, 'run', return_value=SimpleNamespace(
        returncode=2, stdout='', stderr='ps probe failed')):
    case.wait_dead(123)  # Currently passes despite the probe error.
print('REPRODUCED TEST-ORACLE-1')
PY
```

Follow-up acceptance criteria: unexpected `ps` statuses must fail the test,
while a missing PID (status 1), a live process and a zombie remain distinct.
The existing `test_research_runner.process_alive` helper already raises on
statuses other than 0/1 and is a reference for this contract.

## Tests strengthened in this pass

- Packaged integration tests now validate real shaded-JAR Smoke, Explore and
  stress outputs through their actual Python validators. They compare every
  ordered scientific row for packaged serial/parallel Smoke runs, excluding
  only run identifiers and measured allocation time, and exercise invalid
  worker settings before output creation. Output paths include spaces.
- Python statistics tests cover neighboring binary64 values at all three claim
  thresholds in both benefit directions, original-order Holm corrections and
  invalid probabilities, quantile endpoints/invalid coordinates, and specific
  undefined BCa reasons. Expectations are literal or hand-calculated.
- Native macOS tests create real sessions and descendants, kill the owned group,
  exercise cleanup after a reaped leader, reject a mismatching birth identity,
  and preserve an unrelated session. Process survival is checked independently
  through `/bin/ps`; native process tables are not mocked. Finally cleanup is
  registered for every child. Linux skips these explicit native-only checks.

These tests supplement existing independent optimizer/statistics oracles,
placement/resource edge checks, cancellation/concurrency tests, durable-output
checks and bounded-memory tests. Coverage reporting is diagnostic; additional
percentage points alone do not establish scientific or end-to-end correctness.

Focused validation passed all 13 packaged integration tests and all nine Python
statistics tests. Five deliberate mutations in disposable copies changed claim
equality, noninferiority equality, the Holm family multiplier, the quantile index
and the BCa undefined reason. The unmodified baselines passed and the new tests
rejected all five mutants. This is targeted evidence of test sensitivity, not a
whole-project mutation score. The native cleanup tests still require macOS CI.

Final Linux verification ran 217 Python tests: 213 passed and four native macOS
checks were skipped explicitly. `./mvnw -B clean verify` with the managed full
JDK 21 passed 95 Java unit tests and 13 packaged integration tests, with no
failures or skips. Application and validator files were unchanged in this pass.
The optional in-process Python report measured 79.0% of statements and 64.8%
of branches (75.7% combined); child-interpreter, JVM and native-library execution
is outside that report's scope. Neither these results nor the green default
suite close either finding above.
