#!/usr/bin/env bash
# Builds one image per OCR engine for the spike in #6.
set -euo pipefail
cd "$(dirname "$0")"
docker build -t ocr-spike/tesseract -f Dockerfile.tesseract .
docker build -t ocr-spike/paddle -f Dockerfile.paddle .
docker build -t ocr-spike/doctr --build-arg PACKAGES="python-doctr" -f Dockerfile.torch .
# surya >= 0.18 is a VLM that needs a llama-server binary; pin the classic det+rec models
docker build -t ocr-spike/surya --build-arg PACKAGES="surya-ocr<0.18 requests transformers<5" -f Dockerfile.torch .
docker build -t ocr-spike/easyocr --build-arg PACKAGES="easyocr" -f Dockerfile.torch .
docker build -t ocr-spike/rapidocr -f Dockerfile.rapidocr .
