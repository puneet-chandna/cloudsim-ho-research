#!/usr/bin/env bash
set -euo pipefail
launcher_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
launcher_python="$launcher_root/.cloudsim/venv/bin/python"
if [[ ! -x "$launcher_python" ]]; then launcher_python=python3; fi
exec "$launcher_python" -B "$launcher_root/scripts/cloudsim.py" "$@"
