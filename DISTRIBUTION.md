# Lattora distribution

The public version comes from the direct project version in `pom.xml`. CLI,
engine manifest, archive names and release manifests use it. Protocol and output
schema versions remain independent. Internal Java packages and the existing
GitHub repository are retained.

## Build an archive

From a clean, reviewed checkout on a supported native machine:

```sh
./cloudsim.sh --setup
./cloudsim.sh --test --plain
.cloudsim/venv/bin/python -B scripts/build_lattora.py --target linux-x86_64
.cloudsim/venv/bin/python -B scripts/accept_lattora.py \
  --archive target/distribution/lattora-2.1.0-linux-x86_64.tar.gz \
  --report target/distribution/acceptance-linux-x86_64.json
```

Use `linux-arm64` or `macos-arm64` on the matching native machine. The packager
requires the full matching Java/Python verification receipt, unchanged source
inventory, revision, engine version and JAR hash. `--allow-dirty` permits local
diagnostic bundles, which release assembly rejects. This is useful to review
uncommitted implementation; it is not a release acceptance shortcut.

`packaging/runtimes.json` pins portable CPython 3.14.8 and Temurin JRE
21.0.12.1+1 by download URL and SHA-256 for each target. Python is an ordinary
executable so validators and supervised workers use the same pinned interpreter.
Textual 8.2.8 and its complete transitive closure are pinned with wheel hashes in
`packaging/requirements-ui.lock`. Runtime links are materialized as files so the
bootstrap can reject all archive links and special files before extraction.
Dependency notices ship in the runtime trees, Python distribution metadata,
`licenses/engine` and the JAR. See the project license and dependency inventory.
The manifest records every runtime, script, resource and engine hash.

Options include `--runtime-cache DIRECTORY` for verified runtime downloads,
`--wheelhouse DIRECTORY` for an offline wheel source, and `--output-dir DIRECTORY`.
Experiments after installation do not need these caches or network access.

## Acceptance and release

`lattora-packages.yml` builds on native Ubuntu x86-64, Ubuntu ARM64 and Apple
Silicon macOS runners. Each job verifies the full source suite, builds the actual
archive and accepts it with a fresh home and no system Python, Java, Git or
Maven on the app PATH. Acceptance covers all frozen profiles, bounded Stress,
independent validation, arbitrary and spaced paths, central/explicit outputs,
installation reruns, TUI resizing/navigation with a real worker, cancellation,
RSS monitoring, and management failure fixtures. Update fixtures use diagnostic
versions derived from the actual archive; they never contact or publish a stable
release. Logs identify which checks use fixtures.

Reports bind their platform, version, source revision and status to the archive's
SHA-256. All three reports must pass before `build_lattora.py --release-metadata`
can generate `install.sh`, `release.json` and `SHA256SUMS`. A tag must match the
POM version. The release workflow retains downloadable review artifacts and
optionally creates a **draft** GitHub release. It refuses to change a published
release. There is no automatic publication step.

Review the three reports, archive hashes, licenses and draft assets before launch
approval. Project source uses MIT; bundled dependencies retain their own licenses,
including CloudSim Plus GPL-3.0. Technical notice inclusion does not resolve
distribution/legal obligations; review those separately before publishing a binary.

Publishing the approved draft activates the public `releases/latest`
installer. Native CI results are required; cross-platform fixture tests on Linux
are not evidence of native ARM or macOS execution.

## Installer options

The public bootstrap requires Bash, curl, tar, awk and `sha256sum` or `shasum`.
Once the stable release is published, choose a pinned version or leave shell
configuration untouched by passing options after `bash -s --`:

```sh
curl -fsSL https://github.com/puneet-chandna/Lattora/releases/latest/download/install.sh | bash -s -- --version 2.1.0
curl -fsSL https://github.com/puneet-chandna/Lattora/releases/latest/download/install.sh | bash -s -- --no-modify-path
```

For an unpublished candidate, extract the verified archive and run its launcher
from that directory:

```sh
./bin/lattora _install
```

This stages the bundle under the versioned installation root, performs its
health check and prints activation instructions. Local candidate installation
does not publish a release or activate the public bootstrap URL.

## Installation and recovery

The installer stages and health-checks a version, then writes an immutable
activation record containing the version and rollback history. Replacing the
`current` symlink commits both together, so an abruptly killed operation cannot
activate a version without its rollback history. Management operations share a filesystem lock. Updates retain every
installed version; app sessions hold a shared session lock so uninstall cannot
remove a running release. Rollback verifies the previously active version before
activation. Integrity failures reject experiments before Java execution. Release
verification records never claim local tests ran in an installed experiment.

Data locations and commands are documented in the [README](README.md#results-and-interpretation).
Linux cache uses `${XDG_CACHE_HOME:-~/.cache}/lattora`; macOS cache uses
`~/Library/Caches/lattora/`. Linux settings and results respect their XDG locations;
macOS stores both under `~/Library/Application Support/lattora/`.
The command is exposed through `~/.local/bin/lattora`. The installation root
stays under `~/.local/share/lattora` even if Linux XDG data locations differ;
experiment/configuration/cache locations respect XDG. Uninstall removes only
managed app versions, its command and activation state. It preserves experiments,
settings and the marked PATH block. Reinstalling is safe and idempotent. A damaged
same-version directory is preserved and rejected; restore it from a trusted
archive before activating it, or activate another retained healthy version.

Linux packages require glibc and the existing readable `/proc` and
cgroup-v2 memory view. macOS requires a native ARM64 terminal. Memory preflight,
heap limits and scientific claims are unchanged. macOS memory checks exclude
compressed pages and swap. Large Stress still requires appropriate memory and
time; packaging does not alter its feasibility or interpretation.
