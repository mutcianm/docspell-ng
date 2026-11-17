#!/usr/bin/env bash
# Scores <data>/out/* against <data>/ref in a throwaway container with rapidfuzz.
set -euo pipefail
cd "$(dirname "$0")"
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$(realpath "$1"):/data:ro" -v "$PWD/score.py:/opt/score.py:ro" \
  python:3.12-slim sh -c "pip install -q --no-cache-dir --target /tmp/py rapidfuzz 2>/dev/null; PYTHONPATH=/tmp/py python /opt/score.py /data"
