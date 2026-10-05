# README capture notes

These are actual Lattora 2.1.0 preview screens, captured on Linux x86-64 on
2026-10-05. No screen, measurement, progress counter or scientific verdict is a
fixture. Version 2.1.0 remains unpublished.

## App and method

The standalone archive records source revision
`dfd8b9bca5baae7be69c814de9c8e1d722dddd85`, Python 3.14.8,
Java 21.0.12.1+1 and Textual 8.2.8. Its full manifest-bound file inventory passed
SHA-256 verification. The manifest SHA-256 is
`be679a808668d36fdeadd45fcaf5b27e1926b578db5884faefc4be592dd856d6`.
All 15 bundled `scripts/*.py` files matched the current source checkout at
`7cac683709403924e82274d5fd547d3b2a69c440` byte for byte. This is a file comparison,
not independent verification of the archive's recorded Git history.

Screens came from the unchanged `CloudSimApp` under Textual's
`run_test(size=(120, 34))`, using `export_screenshot()` with a true-color terminal
environment. The export adds the cosmetic terminal frame and the title
"Lattora · 2.1.0 preview". Theme changes use the app's own selector. Capture
settings, checks and scratch files are isolated from the original evidence.

The GIF is an edited navigation tour: retained Smoke summary, saved evidence,
run history, Smoke plan, then Stress controls in Harbor, Ember and Paper.
Seven genuine screen exports are held for reading (4.0, 2.2, 2.2, 2.4, 2.6,
1.8 and 2.8 seconds), for an 18-second loop. It is not a recording of live
experiment progress or elapsed execution time. Stress previews were not run.

The visible "Setup needs attention" banner is real: current usable memory was
below the app's 3 GiB guard. The guard was not bypassed. This prevents a new run;
it does not prevent browsing complete retained evidence.

SVG exports were rendered with the installed ffmpeg/librsvg decoder at
1482 × 880. PNGs retain that resolution. Pillow resized the GIF frames to
1200 × 713 and quantized them to 256 colors; no UI content was composited,
repainted or replaced. The README links each image to its full-size file.

## Scientific evidence

The displayed campaign is `smoke-20261005T022829Z-5zr3i_t3`, run
`3e1f8c2e-9dbb-47f3-917b-1b9a74963b51`. It ran earlier on 2026-10-05 from
02:28:29 to 02:28:33 UTC, completed all four cases and recorded `PASS` validation.
It was not executed again for this capture. Local build/tests remain `NOT_RUN`;
release verification is recorded separately.

The current `statistics_validator.py` freshly revalidated the retained leaf:
hashes, canonical inputs, raw identities, placements, traces, metrics and
independent analysis agreed. `run_validation.validation_targets()` rechecked
campaign completeness and retained JAR binding before and after validation.
All evidence files retained their original hashes through validation and capture.
These checks establish the documented validator scope, not producer
authenticity or independent optimizer replay. Smoke checks the pipeline and
establishes no algorithm winner or research claim.

| Evidence | SHA-256 |
| --- | --- |
| `runner.json` | `00f6fc2abff7d5ef503601d108ae1ffd08b4ded43229a0172bf86250cdbc3a55` |
| `smoke.jar` | `12f5a6000e92fe495ad1dc18e99303fa366cae42d810ba9000b26f095cdde4bf` |
| `run.json` | `69b5183d45f0f066988cd2916558118a88f88bb7c9ec867b471d12646aed5184` |
| `raw/main_results.csv` | `c78916481b3bc9928d1346fdd867a02b93fd3b715861d280569ba9d56398db0b` |
| `raw/placements.csv` | `33e41badf93c1b9a25b864785698c9531468c6ac08308d154434a40bfd1495c8` |
| `raw/optimizer_trace.csv` | `7e82471ccb3966c75b90526126179f856571a083f8fc7fb7bf486e6b415036a8` |
