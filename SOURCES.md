# Lattora release sources and licenses

The release page provides `lattora-2.1.0-sources.tar.gz` beside the three
standalone archives, with its checksum in `SHA256SUMS`. It contains the exact
Lattora source snapshot, source archives and build materials for the bundled
engine and runtimes, Maven dependency sources/POMs, and a source manifest.
`packaging/sources.json` records upstream URLs and SHA-256 pins.

Lattora's original source is MIT licensed. The combined Java engine includes
CloudSim Plus and is distributed under GPL-3.0, with the original component
notices retained. Temurin uses GPL-2.0 with the Classpath Exception. Python and
its native components retain their individual licenses. Berkeley DB 6.0.19
uses the Sleepycat license. The standalone archive carries these texts in
`licenses/engine`, `licenses/python`, Python package metadata and Java's
`legal` directory. These terms also apply when redistributing the binaries.

## Source and build materials

- Lattora: [v2.1.0](https://github.com/puneet-chandna/Lattora/tree/v2.1.0).
  Each binary's `distribution.json` and the source manifest record the exact
  revision. See [DEVELOPMENT.md](DEVELOPMENT.md) and [DISTRIBUTION.md](DISTRIBUTION.md).
- CloudSim Plus 8.5.7: source revision
  `f23d4b165402e4976de854ceed5e52bc7b78c520`, with its Maven build files.
  All shaded engine dependencies have matching source JARs and POMs in the
  source archive; coordinates are listed in the engine dependency inventory.
- Temurin 21.0.12.1+1: the upstream
  [complete source archive](https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk-sources_21.0.12.1_1.tar.gz)
  is included, together with Temurin build scripts at
  `e6ba7dec3d07654074559310376a3ae89da5f4ac`. The bundled Java `release` file
  identifies the OpenJDK source and build revisions. Build instructions are in
  those source trees; [Adoptium's build guide](https://github.com/adoptium/temurin-build/blob/e6ba7dec3d07654074559310376a3ae89da5f4ac/README.md)
  describes the required platform tools.
- Portable CPython 3.14.8: CPython sources and python-build-standalone build
  scripts at `5e46737f6480fc315ebfea83866910cbfcc772f0` (release `20261003`),
  plus the matching Berkeley DB and Zstandard 1.5.7 sources.
  The Zstandard notice is copied from its exact source revision
  `eef946ae8cf1591c0e5cc5f43486210768647c2e`; the upstream full Python
  archive omits that notice, so Lattora supplies it explicitly. The builder's `pythonbuild/downloads.json`
  records native component versions, source locations and checksums; its build
  scripts describe how to reproduce the platform builds. `licenses/python/PYTHON.json`
  preserves the upstream runtime component inventory.

To build Lattora from the release tag:

```sh
git clone --branch v2.1.0 https://github.com/puneet-chandna/Lattora.git
cd Lattora
./lattora.sh --setup
./lattora.sh --test --plain
```

To use the source snapshot without a GitHub clone, extract `lattora-source.tar`
from the source archive into an empty directory, initialize a local Git
repository and commit the snapshot before running the same source commands.
This produces a new local provenance revision. Modified sources remain
buildable; a published binary's manifest is never rewritten to accept them.
