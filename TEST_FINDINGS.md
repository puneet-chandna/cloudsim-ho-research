# Test audit findings

Both findings below were reproduced on Linux on 2026-10-04 after macOS support
commit `14dcbba`, and then fixed with regressions. The original audit and
reproductions are retained below. Native Apple Silicon execution requires macOS CI.

## FROZEN-METRICS-1: coherent forged frozen metrics pass validation

**Severity: high for research evidence integrity. Status: fixed with regression coverage.**

Before the fix, `scripts/statistics_validator.py:validate_raw` checked positive
energy, joule/kWh conversion, completion/SLA count consistency and horizon bounds, but did not
independently recompute those metrics from the frozen workload and placement.
Its analysis checks recomputed summaries from the supplied raw metrics. Refreshed
SHA-256 values plus internally consistent forged metrics therefore passed.

Three mutations of a valid 40-case Explore run were accepted:

| Coherent mutation | First Micro/HO case before → after | Consistency maintained |
| --- | --- | --- |
| Multiply every energy by 1.5 | 47,152.8125 J → 70,729.21875 J | kWh conversion and summary energy mean/SD |
| Multiply every horizon by 1.5 | 111 s → 166.5 s | Positive horizon below the censor deadline |
| Declare one failed cloudlet in every case | completed 10/failed 0/SLA 0 → completed 9/failed 1/SLA 0.1 | Requested-cloudlet denominator, count totals and summary SLA mean/SD |

Each mutation retains canonical inputs, placements, seeds, optimizer traces and
case identities, and recalculates all recorded file hashes. Before the fix,
validation printed `VALID`. That could conceal a regression in native metric
computation or accept coherently altered evidence. It does not show that the current simulator
produces those incorrect values: an independent calculation of the retained
450-case Research run found exact energy agreement, expected horizons and full
completion with zero failures/censoring/SLA violations.

Run the regression on Linux or Apple Silicon with a valid Explore dataset:

```sh
# Use the managed JDK after ./cloudsim.sh --setup, or an equivalent full JDK 21.
.cloudsim/jdk/bin/java -Xmx256m -jar target/cloudsim-ho-research-v2-2.0.0.jar \
  --profile explore --output-dir /tmp/cloudsim-validator-gap
python3 -B scripts/probe_frozen_metric_gaps.py /tmp/cloudsim-validator-gap/<printed-run-directory>
```

The validator now independently integrates VM CPU work per assigned host and
idle power through the canonical maximum reference duration. Strict reservation,
one full-utilization cloudlet per VM and common release imply full completion
with zero failures, censoring or SLA violations. These checks apply only to the
canonical, feasible frozen schema-2 model; the separate stress validator keeps
its own model and contract.

The regression first accepts the original dataset, then rejects temporary,
coherently rehashed copies for energy, horizon, failed completion/SLA and a
fourth SLA-only forgery, with specific physical-model errors. All four checks
are ordinary assertions, with no expected failures. Maven `verify` runs them
through the existing real Explore dataset acceptance test. A hand-calculated
unit fixture covers multiple VM PEs, unequal host capacities, placement-dependent
energy, idle tails and an unused host. Genuine Smoke, Explore and all 450
Research cases must continue to pass validation.

## TEST-ORACLE-1: native cleanup test can mistake a failed probe for exit

**Severity: medium for test reliability. Status: fixed with regression coverage.**

Independent review found this weakness in the newly added
`scripts/test_macos.py:NativeMacCleanupTests.alive` helper: before the fix, it
treated every nonzero `/bin/ps` exit status as a dead process. A probe failure
with status 2
therefore made `wait_dead` pass, potentially hiding an orphan after cleanup.
This finding concerns the test oracle; it did not demonstrate a failure in
application process cleanup. The helper now raises for unexpected `ps` statuses
other than 0/1, and propagates launch failures and timeouts. Missing PIDs and
zombies remain exited; running processes remain live.

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
    try:
        case.wait_dead(123)
    except RuntimeError:
        print('PASS: ps probe failure propagated')
    else:
        raise AssertionError('Probe failure was mistaken for process exit')
PY
```

Seven cross-platform regression tests exercise error statuses, probe failures
during and before waiting, missing/live/zombie states, query timeout and the
live-process deadline. They run on Linux as well as macOS; only the real native
process-group checks remain macOS-only. The new tests failed before the guard
and passed afterward.

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

The initial test audit's Linux verification ran 217 Python tests (four native
macOS skips) and 108 Java unit/packaged tests. Its optional in-process Python
report measured 79.0% of statements and 64.8% of branches (75.7% combined);
child-interpreter, JVM and native-library execution is outside that report's
scope. Fresh Linux verification of both fixes passed through the ordinary
`./cloudsim.sh --test --heap-mib 512 --plain` path: 225 Python tests (221 passed,
four explicit native macOS skips) and 108 Java unit/packaged tests (zero failures
or skips). A fresh ordinary-launcher Research run completed and independently
validated all 450 cases; its scientific raw rows, placements, optimizer traces
and effective configuration matched the retained pre-fix run, excluding run
identifiers and measured allocation time. The fixed regressions reject four
coherently rehashed forgeries; removing any of the three new physical-model
guards makes those regressions fail. No expected failures remain in that suite.
Native macOS process execution is still pending Apple Silicon CI.
