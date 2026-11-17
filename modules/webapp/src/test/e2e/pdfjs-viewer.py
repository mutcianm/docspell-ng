"""E2E check for #17: the bundled pdf.js viewer loads and renders a PDF.

Opens the viewer from the restserver's webjar route with a PDF served by the test
itself, so no login or stored attachment is needed. Fails on console errors, failed
asset requests, or a page that does not render. Run in the playwright image:
  docker run --rm --network host -v $PWD:/t -v <dir-with-pdfs>:/pdf:ro \
    mcr.microsoft.com/playwright/python:v1.49.0-noble \
    sh -c "pip install -q playwright==1.49.0; python /t/pdfjs-viewer.py http://<host>:7880 /pdf/a.pdf ..."
"""
import asyncio, os, sys
from urllib.parse import urlparse
from playwright.async_api import async_playwright

base, pdfs = sys.argv[1], sys.argv[2:]
viewer = os.environ.get("VIEWER", "/app/assets/pdf-js/5.4.530/web/viewer.html")


async def check(browser, pdf):
    page = await browser.new_page()
    problems = []
    page.on("console", lambda m: m.type == "error" and problems.append(f"console: {m.text}"))
    page.on("pageerror", lambda e: problems.append(f"pageerror: {e}"))
    page.on("response", lambda r: r.status >= 400 and problems.append(f"HTTP {r.status} {r.url}"))
    body = open(pdf, "rb").read()
    # match the path only: the viewer url itself carries the name in its query
    await page.route(lambda u: urlparse(u).path == "/e2e-test.pdf", lambda r: r.fulfill(body=body, content_type="application/pdf"))

    await page.goto(f"{base}{viewer}?file=/e2e-test.pdf")
    await page.wait_for_function("window.PDFViewerApplication?.pdfDocument?.numPages > 0", timeout=30000)
    await page.wait_for_selector(".page[data-loaded=true] canvas", timeout=30000)
    pages = await page.evaluate("PDFViewerApplication.pdfDocument.numPages")
    spans = await page.locator(".textLayer span").count()
    await page.screenshot(path=f"/tmp/{os.path.basename(pdf)}.png")
    await page.close()
    print(f"{'FAIL' if problems else 'OK'} {pdf}: pages={pages} text-spans={spans}")
    for p in problems:
        print("  ", p)
    return not problems


async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        results = [await check(browser, pdf) for pdf in pdfs]
        await browser.close()
    sys.exit(0 if all(results) else 1)


asyncio.run(main())
