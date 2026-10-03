<p align="center"><img src="logo/dark.svg" alt="CloudSim HO Research V2" width="500"></p>

# CloudSim HO Research V2

A bounded, reproducible comparison of Hippopotamus Optimization (HO), GA,
FirstFit and BestFit for static VM placement using CloudSim Plus 8.5.7.
The current source version is **2.0.0**. Historical pre-v2 results are
invalid research evidence. No algorithm winner or real datacenter saving is claimed.

These instructions describe the local recovery checkout. The v2 changes and
artifact have not been published; use the reviewed local source for now.
The public default branch and live documentation may still describe legacy code.

The executable contract is the [frozen configuration](src/main/resources/protocol.properties)
and [`RunConfig.effective()`](src/main/java/org/puneet/cloudsimplus/hiippo/runtime/RunConfig.java),
checked independently by the [output validator](scripts/statistics_validator.py).
[Documentation](https://cloudsim-ho-project.puneetchandna.com/),
[contributing](CONTRIBUTING.md), [code of conduct](CODE_OF_CONDUCT.md).

## Build and verify

Use a full **Java 21 JDK** (`java` and `javac`), Git and **Python 3.11+**.
Linux tests require the executable `python3` for independent stdlib oracles.
Wrapper bootstrap on Linux requires `unzip` and either `sha256sum` or `shasum`.
The included Maven Wrapper pins Maven 3.9.16; no global Maven installation is
needed. Initial setup needs network access to download Maven and dependencies.
Set `JAVA_HOME` to the JDK and put its `bin` directory on `PATH`.

For the integrated Linux terminal app, start `./cloudsim.sh`. First launch offers
to install the pinned terminal UI into `.cloudsim/venv`; Setup can download a
checksum-verified JDK 21 into `.cloudsim/jdk` or select an installed JDK.
`./cloudsim.sh --setup` performs project-local setup directly (automatic JDK
download currently supports Linux x86-64). No global Java alternatives or shell
settings are changed. The launcher uses the selected JDK for checks, Maven and
simulation and keeps its default Maven downloads under `.cloudsim/maven`.

Run, Results, Tools and Setup stay available during a job. Choose Smoke,
Explore, Research or Static Stress; stress sizes and advanced options are
editable while frozen protocol settings remain read-only. Failed attempts keep
their settings and logs. Ctrl+C opens cancellation, Ctrl+Q quits, and F1 shows
keyboard help. F2–F5 switch between Run, Results, Tools and Setup. Small
terminals scroll instead of hiding errors.

Automation uses `--profile smoke|explore|research|stress`, `--check`, `--build`,
`--test` or `--validate DIRECTORY`. Add `--plain` for plain progress. Help,
dry-run and existing-output validation do not need Java or the terminal UI.
Setup is explicit; direct experiment actions never download a JDK automatically.

From the supplied v2 source checkout (not a fresh clone of the public default
branch), run:

```sh
java -version
javac -version
python3 --version
./mvnw -B clean verify
java -jar target/cloudsim-ho-research-v2-2.0.0.jar --help
java -Xmx4g -jar target/cloudsim-ho-research-v2-2.0.0.jar --profile smoke --output-dir results/smoke
```

The application prints the unique run directory. Validate that exact directory:

```sh
python3 scripts/statistics_validator.py results/smoke/<run-directory>
```

Replace `<run-directory>` with the printed child name. `verify` includes unit
tests and real packaged CLI integration tests; `test` alone does not verify the
shaded JAR. The validator checks manifest/file hashes, raw matrices, canonical
inputs, placements, budgets, metrics and independent analysis. Keep the exact
JAR alongside its validated results and compare its SHA-256 to `artifact_sha256`.

Windows support is **packaged smoke only**, not the Linux research/test contract.
In PowerShell, build the package without the Linux oracle suite and check every
native exit code (Python is invoked as `python`):

```powershell
.\mvnw.cmd -B '-Dmaven.test.skip=true' clean package
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -Xmx4g -jar target/cloudsim-ho-research-v2-2.0.0.jar --profile smoke --output-dir results/smoke
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
# Substitute the run directory printed above:
python scripts/statistics_validator.py results/smoke/<run-directory>
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Windows execution and remote CI are separate gates; local Linux validation does
not establish either. CI runs short PR-only Ubuntu validation and manual
Ubuntu/Windows packaged smoke, with ten-minute job timeouts. It never publishes
packages or runs the research matrix.

## Run profiles and configuration

| Profile | Scenarios (VMs/hosts) | Replications | HO N/T | Cases |
| --- | --- | --- | --- | --- |
| smoke | Micro 10/3 | 1 | 10/4 | 4 |
| explore | Micro 10/3, Small 50/10 | 5 | 20/20 | 40 |
| research | Micro 10/3, Small 50/10, Medium 100/20 | 30 | 30/40 | 360 main + 90 OAT |

All four algorithms run in every main replication. Research additionally runs
HO on Small with nine distinct one-at-a-time (OAT) N/T settings and ten paired
sensitivity replications. The shared (30,40) setting is counted once. These
settings and the **100-VM ceiling** are fixed; larger scenarios, GPU, parallel
execution, Pareto optimization, overcommit and migrations are future work.

```sh
java -Xmx4g -jar target/cloudsim-ho-research-v2-2.0.0.jar --profile explore --output-dir results/explore
# Linux only; 12-hour external safety timeout, maximum 4 GiB Java heap:
timeout 12h java -Xmx4g -jar target/cloudsim-ho-research-v2-2.0.0.jar --profile research --output-dir results/research
```

Validate each complete directory with the same Python command. A timeout or
interruption leaves incomplete evidence; never combine fragments. Stable 2.0.0
requires all **450 local Linux cases** from the exact versioned artifact, valid
independent analyses and two reviewer approvals. That acceptance is not implied
by the version number or smoke/explore checks.

No arguments, standalone `--help` or `--version` print information and exit 0
without creating files. A run requires `--profile smoke|explore|research`.
Optional flags are `--config file.properties`, `--output-dir directory` (default
`results`) and `--debug`. CLI values take precedence over the overlay, then
profile defaults. Only these UTF-8 Java properties are accepted:

```properties
master.seed=123456
log.level=INFO
```

`master.seed` is a signed 64-bit decimal; `log.level` is `INFO` or `DEBUG`.
Unknown/duplicate keys (including escaped equivalents), malformed UTF-8,
invalid values or conflicting/missing options exit **2** before output creation.
Missing/unreadable config files and execution/output failures exit **1**. Only
complete valid runs exit **0**. `output.dir`, scenario and algorithm settings
are not configurable keys. `--debug` selects DEBUG without relaxing log bounds.

## What is measured

The optimizer minimizes estimated steady host power in **watts**. The simulation
separately integrates actual event-time power into **joules** and **kWh**;
used hosts remain on through the final workload horizon. SLA violations count
failed/censored cloudlets or slowdown strictly above 1.10 against independently
simulated isolated references, divided by all requested cloudlets.

Strict reservation, constant utilization and one cloudlet per VM can make SLA
identically zero and horizon placement-invariant. The conservative paired
wild-bootstrap/BCa/Holm analysis reports **NO_CLAIM** when required inference is
degenerate; descriptive energy differences do not override that gate. Results
are conditional on this synthetic simulator model, not production evidence.

HO implements all three paper phases with a declared Gaussian Mantegna Levy
adaptation. Its full objective-call budget is **N + 3NT**, including predators:
130/1220/3630 for the three main profiles. GA receives the same budget. The
[search implementation](src/main/java/org/puneet/cloudsimplus/hiippo/placement/Search.java)
and independent [optimizer oracle](scripts/optimizer_oracle.py) specify and check
the equations, draw order and deterministic repair. The frozen configuration
and output validator define the practical margins and inference gates.

## Outputs and licensing

Each unique directory contains `run.json`, sorted `effective.properties`,
`raw/main_results.csv`, `raw/placements.csv`, `raw/optimizer_trace.csv` and
`logs/run.log`. Explore adds descriptive analysis; research adds sensitivity
rows, paired primary comparisons, runtime summaries and an analysis report.
Only a validated `COMPLETE` manifest is eligible evidence. `RUNNING` and `FAILED`
directories remain for diagnosis. Logs rotate at 10 MiB with three retained
archives, including DEBUG. Existing local logs/results are never cleaned by the app.

Project source has the [MIT license](LICENSE). Bundled dependencies retain their
own licenses, including CloudSim Plus GPL-3.0; the JAR includes notices and a
[dependency inventory](src/main/resources/META-INF/third-party/DEPENDENCIES.txt).
Technical notice inclusion does not resolve distribution/legal obligations;
review those separately before publishing a binary.
