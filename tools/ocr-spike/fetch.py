"""Downloads attachment originals for the OCR spike (#6) into <data>/src and writes <data>/pages.json.

Usage: fetch.py <env-file> <data-dir> <sample.txt>
sample.txt has lines "group|attachid|lang|..." (as produced by the selection query).
The env file provides DOCSPELL_URL, DOCSPELL_USR and DOCSPELL_PASS; values are never printed.
"""
import json
import os
import sys
import urllib.request as u


def load_env(path):
    for line in open(path):
        if "=" in line and not line.startswith("#"):
            k, v = line.strip().split("=", 1)
            os.environ.setdefault(k, v.strip("'\""))


def main(env_file, data, sample):
    load_env(env_file)
    base = os.environ["DOCSPELL_URL"] + "/api/v1"
    login = u.Request(base + "/open/auth/login", method="POST", headers={"Content-Type": "application/json"},
                      data=json.dumps({"account": os.environ["DOCSPELL_USR"],
                                       "password": os.environ["DOCSPELL_PASS"]}).encode())
    token = json.load(u.urlopen(login))["token"]
    os.makedirs(os.path.join(data, "src"), mode=0o700, exist_ok=True)
    pages = []
    for n, line in enumerate(l for l in open(sample) if l.strip()):
        group, attach_id, lang = line.strip().split("|")[:3]
        req = u.Request(f"{base}/sec/attachment/{attach_id}/original", headers={"X-Docspell-Auth": token})
        with u.urlopen(req) as resp:
            ctype = resp.headers.get("Content-Type", "")
            ext = {"application/pdf": ".pdf", "image/png": ".png", "image/jpeg": ".jpg", "image/tiff": ".tif"}.get(ctype.split(";")[0])
            if ext is None:
                print(f"skip {attach_id}: {ctype}")
                continue
            name = f"s{n + 1:02d}-{group}"
            with open(os.path.join(data, "src", name + ext), "wb") as f:
                f.write(resp.read())
        pages.append({"file": name + ".png", "lang": lang, "group": group, "attachid": attach_id})
    json.dump(pages, open(os.path.join(data, "pages.json"), "w"), indent=1)
    print(f"fetched {len(pages)} originals")


if __name__ == "__main__":
    main(*sys.argv[1:])
