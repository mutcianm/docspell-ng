#!/usr/bin/env bash
# Runs the given engines (default: all) over <data>/pages with the same CPU budget.
# Usage: run.sh <data-dir> [engine...]
set -euo pipefail
cd "$(dirname "$0")"
data="$(realpath "$1")"; shift
engines=("${@:-tesseract tesseract-multi paddle doctr easyocr surya}")
mkdir -p "$data/models"
for engine in ${engines[@]}; do
  image="ocr-spike/${engine%-*}"
  [[ "$engine" != tesseract* ]] || image=ocr-spike/tesseract
  docker run --rm --cpus 4 -e OMP_NUM_THREADS=4 -e HOME=/data/models \
    --user "$(id -u):$(id -g)" -v "$data:/data" -v "$PWD/ocr.py:/opt/ocr.py:ro" \
    "$image" "$engine" /data
done
