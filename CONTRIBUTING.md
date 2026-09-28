# Contributing

Read the [frozen configuration](src/main/resources/protocol.properties),
[`RunConfig.effective()`](src/main/java/org/puneet/cloudsimplus/hiippo/runtime/RunConfig.java),
[search implementation](src/main/java/org/puneet/cloudsimplus/hiippo/placement/Search.java)
and independent [optimizer oracle](scripts/optimizer_oracle.py) and
[output validator](scripts/statistics_validator.py) before changing behavior.
Report defects with source/JAR version, command, JDK/OS, exit code and retained
run manifest/logs. Do not treat pre-v2 results or partial datasets as evidence.

Use a full Java 21 JDK, Git, Python 3.10+ available as `python3`, and the included
Maven 3.9.16 Wrapper on Linux. The [quickstart](README.md#build-and-verify) is the
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

PR CI is short Ubuntu build/test/packaged smoke with a ten-minute timeout.
Manual release CI runs packaged smoke on Ubuntu and Windows, also ten minutes
per job. Windows uses `mvnw.cmd -Dmaven.test.skip=true clean package` followed by
the JAR and `python` validator; it does not claim the Linux test suite passed.
There are no scheduled/release-triggered publishing or research jobs.

Full research is local Linux only, `-Xmx4g` with an external `timeout 12h`.
Stable release requires 450 complete cases, independent output/analysis checks,
artifact/environment provenance and two independent reviewer approvals.
Smoke/explore are beta validation, not full research acceptance. Publication
and bundled-dependency licensing remain separate decisions.

Keep README and companion documentation consistent with tested commands,
configuration keys, outputs and limitations. Explain behavioral/protocol changes
and evidence in the PR; obtain two reviews before integration. Follow the
[code of conduct](CODE_OF_CONDUCT.md).
