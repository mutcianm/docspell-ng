"""OCR spike runner (#6): run one engine over the pages listed in <data>/pages.json.

Usage: ocr.py <engine> <data-dir>
Reads <data>/pages/<file> and the language from pages.json ([{"file", "lang"}]),
writes <data>/out/<engine>/<file>.txt and <data>/out/<engine>/stats.json.
Also: ocr.py render <data-dir> renders the first page of <data>/src/* to a 300 dpi PNG.
"""
import json
import os
import resource
import subprocess
import sys
import time

TESS_LANG = {"rus": "rus", "nld": "nld", "eng": "eng"}


def render(data):
    src, dst = os.path.join(data, "src"), os.path.join(data, "pages")
    os.makedirs(dst, exist_ok=True)
    for name in sorted(os.listdir(src)):
        base, ext = os.path.splitext(name)
        path = os.path.join(src, name)
        if ext.lower() == ".pdf":
            # first page only
            subprocess.run(["pdftoppm", "-r", "300", "-png", "-f", "1", "-l", "1", "-singlefile", path, os.path.join(dst, base)], check=True)
        else:
            from PIL import Image
            Image.open(path).convert("RGB").save(os.path.join(dst, base + ".png"))


def tesseract(multi, tessdata=None):
    env = dict(os.environ, TESSDATA_PREFIX=tessdata) if tessdata else None

    def run(path, lang):
        langs = "rus+nld+eng" if multi else TESS_LANG[lang]
        out = subprocess.run(["tesseract", path, "-", "-l", langs], check=True, capture_output=True, text=True, env=env)
        return out.stdout
    return run


def paddle():
    from paddleocr import PaddleOCR
    models = {}

    def run(path, lang):
        code = {"rus": "ru", "nld": "nl", "eng": "en"}[lang]
        if code not in models:
            # enable_mkldnn=False: paddle 3.x oneDNN CPU path fails with ConvertPirAttribute2RuntimeAttribute
            models[code] = PaddleOCR(lang=code, enable_mkldnn=False, use_doc_orientation_classify=False,
                                     use_doc_unwarping=False, use_textline_orientation=False)
        res = models[code].predict(path)
        return "\n".join(res[0]["rec_texts"])
    return run


def rapidocr():
    from rapidocr import LangRec, ModelType, OCRVersion, RapidOCR
    engines = {}

    def run(path, lang):
        code = {"rus": LangRec.CYRILLIC, "nld": LangRec.LATIN, "eng": LangRec.EN}[lang]
        if code not in engines:
            # PP-OCRv5 has non-Chinese recognizers only as "mobile"; PP-OCRv6 multi lacks Cyrillic
            engines[code] = RapidOCR(params={"Det.ocr_version": OCRVersion.PPOCRV5, "Det.model_type": ModelType.MOBILE,
                                             "Rec.ocr_version": OCRVersion.PPOCRV5, "Rec.model_type": ModelType.MOBILE,
                                             "Rec.lang_type": code})
        return "\n".join(engines[code](path).txts or ())
    return run


def doctr():
    from doctr.io import DocumentFile
    from doctr.models import ocr_predictor
    model = ocr_predictor(pretrained=True)

    def run(path, lang):
        return model(DocumentFile.from_images(path)).render()
    return run


def surya():
    from PIL import Image
    from surya.detection import DetectionPredictor
    from surya.recognition import RecognitionPredictor
    det = DetectionPredictor()
    try:
        from surya.foundation import FoundationPredictor
        rec = RecognitionPredictor(FoundationPredictor())
    except ImportError:
        rec = RecognitionPredictor()

    def run(path, lang):
        pred = rec([Image.open(path).convert("RGB")], det_predictor=det)[0]
        return "\n".join(line.text for line in pred.text_lines)
    return run


def easyocr():
    import easyocr as e
    readers = {}

    def run(path, lang):
        codes = {"rus": ["ru", "en"], "nld": ["nl", "en"], "eng": ["en"]}[lang]
        key = tuple(codes)
        if key not in readers:
            readers[key] = e.Reader(codes, gpu=False)
        return "\n".join(readers[key].readtext(path, detail=0, paragraph=True))
    return run


ENGINES = {
    "tesseract": lambda: tesseract(False),
    "tesseract-multi": lambda: tesseract(True),
    # tessdata_best models, downloaded to <data>/models/tessdata_best
    "tesseract-best": lambda: tesseract(True, "/data/models/tessdata_best"),
    "paddle": paddle,
    "rapidocr": rapidocr,
    "doctr": doctr,
    "surya": surya,
    "easyocr": easyocr,
}


def peak_rss_mb():
    self_kb = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    child_kb = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss
    return round(max(self_kb, child_kb) / 1024)


def main(engine, data):
    if engine == "render":
        return render(data)
    pages = json.load(open(os.path.join(data, "pages.json")))
    out = os.path.join(data, "out", engine)
    os.makedirs(out, exist_ok=True)
    t0 = time.time()
    run = ENGINES[engine]()
    stats = {"load_secs": round(time.time() - t0, 1), "pages": {}}
    for page in pages:
        t = time.time()
        try:
            text = run(os.path.join(data, "pages", page["file"]), page["lang"])
        except Exception as ex:  # keep going: one failing page must not kill the comparison
            text = ""
            stats["pages"][page["file"]] = {"error": repr(ex)}
        else:
            stats["pages"][page["file"]] = {"secs": round(time.time() - t, 1)}
        with open(os.path.join(out, page["file"] + ".txt"), "w") as f:
            f.write(text)
        print(engine, page["file"], stats["pages"][page["file"]], flush=True)
    stats["peak_rss_mb"] = peak_rss_mb()
    json.dump(stats, open(os.path.join(out, "stats.json"), "w"), indent=1)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
