# Contributing

Read the [frozen configuration](src/main/resources/protocol.properties),
[`RunConfig.effective()`](src/main/java/org/puneet/cloudsimplus/hiippo/runtime/RunConfig.java),
[search implementation](src/main/java/org/puneet/cloudsimplus/hiippo/placement/Search.java)
and independent [optimizer oracle](scripts/optimizer_oracle.py) and
[output validator](scripts/statistics_validator.py) before changing behavior.
Report defects with source/JAR version, command, JDK/OS, exit code and retained
run manifest/logs. Do not treat pre-v2 results or partial datasets as evidence.

Use a full Java 21 JDK, Git, Python 3.11+ available as `python3`, and the included
Maven 3.9.16 Wrapper on Linux or Apple Silicon macOS. Follow the
[source setup guide](DEVELOPMENT.md#source-setup). Run a focused test during changes, then:

```sh
./mvnw -B clean verify
python3 -B -m unittest discover -s scripts -p 'test_statistics_validator.py'
java -Xmx4g -jar target/lattora-*.jar --profile smoke --output-dir results/smoke
python3 scripts/statistics_validator.py "results/smoke/<run-directory>"
```

Substitute the printed child directory. `verify` tests the actual shaded JAR;
`mvnw test` only runs unit tests. Meaningful changes need a check that fails
without the fix. Preserve the independent Python oracles and frozen contract;
do not add retries, skip unfavorable cases, tune on test seeds or replace
undefined statistics with artificial jitter.

Run the complete Python suite for launcher, build-cache, resource, cancellation,
validator and terminal-UI changes; the small statistics suite alone does not
cover those workflows:

```sh
./lattora.sh --setup
.cloudsim/venv/bin/python -B -m unittest discover -s scripts -p 'test_*.py'
```

Optional Python branch-coverage reports use a project-local test tool:

```sh
.cloudsim/venv/bin/python -m pip install coverage==7.15.4
.cloudsim/venv/bin/python -m coverage erase
.cloudsim/venv/bin/python -B -m coverage run -m unittest discover -s scripts -p 'test_*.py'
.cloudsim/venv/bin/python -m coverage report
.cloudsim/venv/bin/python -m coverage html
.cloudsim/venv/bin/python -m coverage json
```

Reports are retained in `.cloudsim/coverage/`. This measures production Python
modules exercised inside the unittest process, excluding test code. It does
not instrument child interpreters, copied fixture scripts, the JVM or native
OS libraries. Read per-file missing branches alongside packaged integration
and native-platform results; a high percentage cannot establish correctness.
The suite includes independent numeric oracles, real subprocess cancellation,
resource-limit checks, semantic corruption tests and packaged CLI acceptance.
Use hand-computed expectations or an independent oracle for new tests, assert
the failure reason for rejected inputs, and keep real process tests bounded
with unconditional cleanup. Document a newly discovered bug with its command,
expected/actual behavior and regression criterion before changing production
behavior. Findings and their regression evidence are tracked in [TEST_FINDINGS.md](TEST_FINDINGS.md).

The only executable main is `App`. Current packages are `scenario`, `placement`
and `runtime`. The old runners, algorithm/policy implementations, property files
and legacy tests were retired after caller checks; use Git history to investigate
old results. Preserve ignored user results/logs. `clean verify` cleans Maven's
`target` directory, not user results.

PR source CI runs Linux build/test/packaged Smoke and native Apple Silicon
setup, verification, Python/TUI tests, all frozen profiles and bounded Stress.
Manual dispatch also checks raw-JAR Smoke on Windows with `python` for
validation; it does not claim the Linux test suite passed. Source jobs have
twenty-minute timeouts.

The separate [native archive workflow](DISTRIBUTION.md#acceptance-and-release)
builds and accepts standalone packages on Linux x86-64, Linux ARM64 and Apple
Silicon, with forty-five-minute job timeouts. Tag or manual release preparation
runs the same archive acceptance and can create a draft. Stable publication
requires launch approval; no workflow automatically publishes it.

Full research is available through `./lattora.sh --profile research` or
`./run-research.sh` on Linux and Apple Silicon macOS, with supervised deadlines
and a bounded heap. GNU `timeout` is needed only for the [raw Linux
JAR command](DEVELOPMENT.md#direct-engine-cli). Linux memory checks retain
cgroup-v2 ancestor limits; macOS uses
native free/reclaimable page evidence with the same heap headroom. Mac RSS is
a sampled maximum, not a kernel high-water mark. Intel Macs are outside scope.
Native macOS CI must pass before treating macOS execution as verified.
Stable release requires 450 complete cases, independent output/analysis checks,
artifact/environment provenance and two independent reviewer approvals.
Smoke/explore are beta validation, not full research acceptance. Publication
and bundled-dependency licensing remain separate decisions.

Keep README and companion documentation consistent with tested commands,
configuration keys, outputs and limitations. Explain behavioral/protocol changes
and evidence in the PR; obtain two reviews before integration. Follow the
[code of conduct](CODE_OF_CONDUCT.md).

Standalone Lattora archives and release acceptance: [distribution guide](DISTRIBUTION.md).
