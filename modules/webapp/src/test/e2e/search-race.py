"""E2E check for #14: a filter applied while a search is in flight must not be dropped.

Delays the first item search, selects the first sidebar tag meanwhile, and checks that
all result cards carry that tag once the searches settled. Run in the playwright image:
  docker run --rm --network host -v <env-file>:/env:ro -v $PWD:/t \
    mcr.microsoft.com/playwright/python:v1.49.0-noble \
    sh -c "pip install -q playwright==1.49.0; python /t/search-race.py http://<host>:7880"
The env file provides DOCSPELL_USR and DOCSPELL_PASS. DELAY=0 gives a control run.
"""
import asyncio, os, sys
from playwright.async_api import async_playwright

for line in open("/env"):
    if "=" in line and not line.startswith("#"):
        k, v = line.strip().split("=", 1); os.environ.setdefault(k, v.strip("'\""))
base = sys.argv[1]

async def main():
    events = []
    delayed = {"n": 0}

    async def slow_first_search(route):
        if delayed["n"] == 0:
            delayed["n"] += 1
            events.append("delay start")
            await asyncio.sleep(float(os.environ.get("DELAY", "4")))  # keep the initial, unfiltered search in flight
            events.append("delay end")
        await route.continue_()

    async with async_playwright() as p:
        b = await p.firefox.launch()
        page = await b.new_page(viewport={"width": 1400, "height": 1000})
        await page.goto(base + "/app/login")
        await page.fill("input[type=text]", os.environ["DOCSPELL_USR"])
        await page.fill("input[type=password]", os.environ["DOCSPELL_PASS"])
        await page.keyboard.press("Enter")
        await page.wait_for_url("**/app/**", timeout=15000)
        await asyncio.sleep(2)
        await page.route("**/api/v1/sec/item/search", slow_first_search)
        await page.goto(base + "/app/search")
        await page.get_by_text("Tags", exact=True).first.click()
        tags = page.locator("a.rounded.px-1.py-1:visible").filter(has_not_text="empty").filter(has_not_text="Show")
        await tags.first.wait_for(timeout=10000)
        name = (await tags.first.inner_text()).strip().splitlines()[0].strip()
        events.append(f"click tag '{name}' (search delayed: {delayed['n'] == 1 and 'delay end' not in events})")
        await tags.first.click()
        await asyncio.sleep(10)
        cards = await page.locator("div.ds-item-card").all()
        texts = [await c.inner_text() for c in cards]
        with_tag = sum(name in t for t in texts)
        print(*events, sep="\n")
        print(f"cards={len(texts)} with_tag={with_tag}")
        print("RESULT", "PASS" if texts and with_tag == len(texts) else "FAIL")
        await b.close()

asyncio.run(main())
