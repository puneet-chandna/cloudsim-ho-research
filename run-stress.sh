#!/usr/bin/env bash
set -euo pipefail
runner_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec python3 -B "$runner_root/scripts/stress_runner.py" "$@"
