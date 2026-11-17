"""Scores OCR outputs against hand-made reference excerpts (#6).

Usage: score.py <data-dir>   (needs rapidfuzz)
<data>/ref/<file>.txt holds reference lines for a part of each page. Per engine:
- word recall: share of reference words found in the output (what full-text search needs)
- line similarity: per reference line, best partial match in the output (0-100), weighted by length
"""
import json
import os
import re
import sys

from rapidfuzz import fuzz

WORD = re.compile(r"[\w@.\-/]+", re.UNICODE)


def words(text):
    return [w.strip(".-/").lower() for w in WORD.findall(text) if len(w.strip(".-/")) >= 2]


def score(ref, out):
    ref_words = words(ref)
    out_words = set(words(out))
    recall = sum(w in out_words for w in ref_words) / max(len(ref_words), 1)
    out_flat = " ".join(out.split())
    lines = [" ".join(l.split()) for l in ref.splitlines() if l.strip()]
    sim = sum(fuzz.partial_ratio(l, out_flat) * len(l) for l in lines) / max(sum(map(len, lines)), 1)
    return recall * 100, sim


def main(data):
    pages = json.load(open(os.path.join(data, "pages.json")))
    engines = sorted(os.listdir(os.path.join(data, "out")))
    rows = {}
    for engine in engines:
        stats_path = os.path.join(data, "out", engine, "stats.json")
        stats = json.load(open(stats_path)) if os.path.exists(stats_path) else {}
        per_page = {}
        for p in pages:
            out_path = os.path.join(data, "out", engine, p["file"] + ".txt")
            if not os.path.exists(out_path):
                continue
            ref = open(os.path.join(data, "ref", p["file"] + ".txt")).read()
            per_page[p["file"]] = score(ref, open(out_path).read())
        secs = [v["secs"] for v in stats.get("pages", {}).values() if "secs" in v]
        rows[engine] = {
            "pages": per_page,
            "secs_per_page": round(sum(secs) / len(secs), 1) if secs else None,
            "errors": sum("error" in v for v in stats.get("pages", {}).values()),
            "load_secs": stats.get("load_secs"),
            "peak_rss_mb": stats.get("peak_rss_mb"),
        }

    groups = ["rus-mixed", "rus", "nld", "eng", "hard"]
    head = f"{'engine':16} {'recall':>6} {'sim':>5} | " + " ".join(f"{g:>9}" for g in groups) + \
        f" | {'s/page':>6} {'load':>5} {'RSS MB':>6} err"
    print(head)
    print("-" * len(head))
    for engine, r in rows.items():
        vals = list(r["pages"].values())
        if not vals:
            continue
        rec = sum(v[0] for v in vals) / len(vals)
        sim = sum(v[1] for v in vals) / len(vals)
        by_group = []
        for g in groups:
            gv = [r["pages"][p["file"]][0] for p in pages if p["group"] == g and p["file"] in r["pages"]]
            by_group.append(f"{sum(gv) / len(gv):9.0f}" if gv else f"{'-':>9}")
        print(f"{engine:16} {rec:6.1f} {sim:5.1f} | " + " ".join(by_group) +
              f" | {r['secs_per_page'] or '-':>6} {r['load_secs'] or '-':>5} {r['peak_rss_mb'] or '-':>6} {r['errors']}")
    print("\nper page word recall:")
    print(f"{'page':16} " + " ".join(f"{e[:9]:>9}" for e in rows))
    for p in pages:
        print(f"{p['file'][:-4]:16} " + " ".join(
            f"{rows[e]['pages'][p['file']][0]:9.0f}" if p["file"] in rows[e]["pages"] else f"{'-':>9}" for e in rows))


if __name__ == "__main__":
    main(sys.argv[1])
