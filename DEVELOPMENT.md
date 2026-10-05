# Developing Lattora

This guide is for a source checkout on `main` or a feature branch based on it. For the standalone app on Linux or macOS, use the
[installer and `lattora` quickstart](README.md#install). Use `./lattora.sh` for setup, verification, builds and the Lattora command interface.
The older `./cloudsim.sh` entry point remains a compatibility alias.
The source cache remains `.cloudsim/` so existing development settings and
verified builds stay usable; installed user storage uses `lattora`.

## Source setup

```sh
./lattora.sh --setup
./lattora.sh --check --plain
./lattora.sh
```

Use a full **Java 21 JDK** (`java` and `javac`), Git and **Python 3.11+**.
Linux and macOS tests require the executable `python3` for independent stdlib oracles.
Wrapper bootstrap requires `unzip` and either `sha256sum` or `shasum`.
The included Maven Wrapper pins Maven 3.9.16; no global Maven installation is
needed. Initial setup needs network access to download Maven and dependencies.
For direct Maven/JAR commands, set `JAVA_HOME` to the selected JDK and put its
`bin` directory on `PATH`. The source launcher selects the project JDK itself.

For the integrated Linux/macOS terminal app, start `./lattora.sh`. First launch offers
to install the pinned terminal UI into `.cloudsim/venv`; Setup can download a
checksum-verified JDK 21 into `.cloudsim/jdk` or select an installed JDK.
`./lattora.sh --setup` performs project-local setup directly (automatic JDK
download supports Linux x86-64, Linux ARM64 and native Apple Silicon macOS). No global Java alternatives or shell
settings are changed. The launcher uses the selected JDK for checks, Maven and
simulation and keeps its default Maven downloads under `.cloudsim/maven`.

Run, Results, Tools and Setup stay available during a job. Choose Smoke,
Explore, Research or Static Stress; stress size, population, iterations,
replications and seed are directly editable while frozen protocol settings remain read-only. Failed attempts keep
their settings and logs. Ctrl+C opens cancellation, Ctrl+Q quits, and F1 shows
keyboard help. F2–F5 switch between Run, Results, Tools and Setup. Small
terminals scroll instead of hiding errors.
The appearance selector offers Harbor, Ember and Paper palettes. A brief opening
reveal leaves input available immediately; `TEXTUAL_ANIMATIONS=none` disables
motion, and `NO_COLOR` is respected.

Automation uses `--profile smoke|explore|research|stress`, `--check`, `--build`,
`--test` or `--validate DIRECTORY`. Add `--plain` for plain progress. Help,
dry-run and existing-output validation do not need Java or the terminal UI.
Setup is explicit; direct experiment actions never download a JDK automatically.

## macOS source prerequisites (Apple Silicon)

Use a native ARM64 terminal and an up-to-date Python 3.11+ installation with
`venv`/`pip`. The Python bundled with macOS may be too old. If you use
[Homebrew](https://brew.sh/), `brew install python git` supplies the prerequisites;
otherwise install Python from [python.org](https://www.python.org/downloads/macos/)
and Git separately. Check `uname -m` reports `arm64` and `python3 --version`
reports 3.11 or newer before starting. Intel Macs and Rosetta execution are
outside the tested support scope.

From the source checkout:

```sh
./lattora.sh --setup
./lattora.sh --check --plain
./lattora.sh                    # terminal workbench: Run, Results, Tools, Setup
```

Setup installs the pinned UI and a checksum-verified ARM64 Temurin JDK 21 only
under `.cloudsim`. You can instead select a full installed JDK in Setup using
its `Contents/Home` directory. Selection honors `JAVA_HOME`, saved project
settings, the managed JDK, then `/usr/libexec/java_home -v 21`; it does not treat
Apple's `/usr/bin/java` stub as a JDK. An explicit `JAVA_HOME` must point to JDK
21. No Homebrew Java package, global Maven, GNU coreutils or shell changes are
required by the launcher.

All profiles, both standalone runners, build/test tools and independent result
validation use the same commands on Linux and Apple Silicon:

```sh
./lattora.sh --test --plain
./lattora.sh --profile smoke --plain
./lattora.sh --profile explore --plain
./lattora.sh --profile research --plain
# Small bounded stress example; larger campaigns need more time and RAM:
./lattora.sh --profile stress --vms 100 --hosts 20 --population 10 --iterations 4 --replications 1 --time-limit 5m --plain
./lattora.sh --validate /absolute/path/to/retained/run --plain
```

The source runners use the same [deadlines, cancellation and native memory checks](EXPERIMENTS.md#workers-and-memory) as the installed app.

Apple Silicon CI exercises local setup, Maven verification, all Python/TUI
tests (including resize/cancellation and orphan cleanup), Smoke, Explore,
all 450 Research cases and bounded stress with independent validation.
That native job must pass before claiming macOS execution is verified;
Linux tests and simulated macOS probes alone do not establish it.

## Source verification and runtime behavior

Experiments reuse a tested build when source, Git revision, JDK, Python/UI
dependencies and the retained JAR hash still match its verification receipt.
The first run or a changed input runs Maven `clean verify` and the Python suite.
`--force-build` and Tools → Run tests explicitly repeat full verification;
`--skip-build` is a separate diagnostic option. Every experiment still runs its
independent result validator. See the experiment guide for
[result interpretation](EXPERIMENTS.md#retained-outputs),
[worker limits](EXPERIMENTS.md#workers-and-memory) and
[Stress calibration](EXPERIMENTS.md#static-stress).

From the supplied v2 source checkout (not a fresh clone of the public default
branch), run:

```sh
java -version
javac -version
python3 --version
./mvnw -B clean verify
java -jar target/lattora-*.jar --help
java -Xmx4g -jar target/lattora-*.jar --profile smoke --output-dir results/smoke
```

The application prints the unique run directory. Validate that exact directory:

```sh
python3 scripts/statistics_validator.py "results/smoke/<run-directory>"
```

Replace `<run-directory>` with the printed child name. `verify` includes unit
tests and real packaged CLI integration tests; `test` alone does not verify the
shaded JAR. The validator checks manifest/file hashes, raw matrices, canonical
inputs, placements, budgets, metrics and independent analysis. Keep the exact
JAR alongside its validated results and compare its SHA-256 to `artifact_sha256`.

## Direct engine Smoke on Windows

Windows has a raw-JAR Smoke workflow only; standalone Lattora packages are deferred.
This does not establish the Linux/macOS research/test contract. In PowerShell, build the package without the Linux oracle suite and check every
native exit code (Python is invoked as `python`):

```powershell
.\mvnw.cmd -B '-Dmaven.test.skip=true' clean package
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -Xmx4g -jar target/lattora-*.jar --profile smoke --output-dir results/smoke
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
# Substitute the run directory printed above:
python scripts/statistics_validator.py "results/smoke/<run-directory>"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

Native macOS/Windows execution and remote CI are separate checks; local Linux
validation does not establish them. The source validation workflow runs Linux
PR checks, Apple Silicon checks and manual Linux/Windows raw-JAR Smoke, with
twenty-minute job timeouts. The separate [archive workflow](DISTRIBUTION.md#acceptance-and-release)
uses native Linux x86-64, Linux ARM64 and Apple Silicon jobs with forty-five-minute
timeouts to build and accept the standalone app, including all 450 Research
cases on each target. Long Stress campaigns remain local. Release preparation
can create a draft; publishing a stable release requires launch approval.

## Direct engine CLI

The raw JAR has its own CLI contract. Unlike `lattora`, invoking the JAR without
arguments prints information and exits. Its default output parent is `results`
relative to the working directory; the installed app uses the central library.
Direct JAR invocation does not provide the workbench or supervised campaign
management. Prefer `lattora run` for installed experiments and `./lattora.sh`
for supervised source runs.

```sh
java -Xmx4g -jar target/lattora-*.jar --profile explore --output-dir results/explore
# This external deadline command is Linux only:
timeout 12h java -Xmx4g -jar target/lattora-*.jar --profile research --output-dir results/research
```

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

[Contributing and required checks](CONTRIBUTING.md) · [Packaging and release](DISTRIBUTION.md)
