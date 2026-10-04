# Contributing

Read the [frozen configuration](src/main/resources/protocol.properties),
[`RunConfig.effective()`](src/main/java/org/puneet/cloudsimplus/hiippo/runtime/RunConfig.java),
[search implementation](src/main/java/org/puneet/cloudsimplus/hiippo/placement/Search.java)
and independent [optimizer oracle](scripts/optimizer_oracle.py) and
[output validator](scripts/statistics_validator.py) before changing behavior.
Report defects with source/JAR version, command, JDK/OS, exit code and retained
run manifest/logs. Do not treat pre-v2 results or partial datasets as evidence.

Use a full Java 21 JDK, Git, Python 3.11+ available as `python3`, and the included
Maven 3.9.16 Wrapper on Linux or Apple Silicon macOS. The [quickstart](README.md#build-and-verify) is the
supported setup. Run a focused test during changes, then:

```sh
./mvnw -B clean verify
python3 -B -m unittest discover -s scripts -p 'test_statistics_validator.py'
java -Xmx4g -jar target/cloudsim-ho-research-v2-2.0.0.jar --profile smoke --output-dir results/smoke
python3 scripts/statistics_validator.py results/smoke/<run-directory>
```

Substitute the printed child directory. `verify` tests the actual shaded JAR;
`mvnw test` only runs unit tests. Meaningful changes need a check that fails
without the fix. Preserve the independent Python oracles and frozen contract;
do not add retries, skip unfavorable cases, tune on test seeds or replace
undefined statistics with artificial jitter.

The only executable main is `App`. Current packages are `scenario`, `placement`
and `runtime`. The old runners, algorithm/policy implementations, property files
and legacy tests were retired after caller checks; use Git history to investigate
old results. Preserve ignored user results/logs. `clean verify` cleans Maven's
`target` directory, not user results.

PR CI retains Linux build/test/packaged smoke and adds native Apple Silicon
macOS local setup, build verification, Python/TUI tests, all frozen profiles
and bounded stress. Manual dispatch also runs these macOS checks and packaged
smoke on Linux and Windows, with twenty-minute timeouts per job.
Windows uses `mvnw.cmd -Dmaven.test.skip=true clean package` followed by
the JAR and `python` validator; it does not claim the Linux test suite passed.
There are no scheduled/release-triggered publishing or research jobs.

Full research is available through `./cloudsim.sh --profile research` or
`./run-research.sh` on Linux and Apple Silicon macOS, with supervised deadlines
and a bounded heap. GNU `timeout` is needed only for the documented raw Linux
JAR command. Linux memory checks retain cgroup-v2 ancestor limits; macOS uses
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
