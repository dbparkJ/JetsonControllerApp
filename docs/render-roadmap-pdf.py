#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

import markdown


CSS = r"""
@page {
  size: A4;
  margin: 15mm 14mm 17mm;
}
* { box-sizing: border-box; }
html { color: #1d2422; background: #ffffff; }
body {
  margin: 0;
  font-family: "Noto Sans CJK KR", "Noto Sans KR", sans-serif;
  font-size: 9.2pt;
  line-height: 1.55;
  letter-spacing: 0;
  word-break: keep-all;
  overflow-wrap: anywhere;
}
h1, h2, h3, h4 {
  color: #123d38;
  line-height: 1.28;
  letter-spacing: 0;
  break-after: avoid-page;
}
h1 { margin: 0 0 8mm; font-size: 23pt; }
h2 {
  margin: 9mm 0 4mm;
  padding-bottom: 2mm;
  border-bottom: 1px solid #9eb8b3;
  font-size: 16pt;
}
h3 { margin: 7mm 0 3mm; font-size: 12.5pt; color: #245f57; }
h4 { margin: 5mm 0 2mm; font-size: 10.5pt; }
p { margin: 0 0 3.2mm; }
blockquote {
  margin: 0 0 5mm;
  padding: 3mm 4mm;
  border-left: 3px solid #006b5f;
  background: #eef6f4;
  color: #344a46;
}
ul, ol { margin: 1.5mm 0 4mm; padding-left: 6mm; }
li { margin: 0 0 1.2mm; }
table {
  width: 100%;
  margin: 3mm 0 6mm;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 7.4pt;
  line-height: 1.42;
}
thead { display: table-header-group; }
tr { break-inside: avoid; }
th, td {
  padding: 2.2mm 2mm;
  border: 1px solid #b8c8c5;
  vertical-align: top;
  overflow-wrap: anywhere;
}
th { background: #e2eeeb; color: #153e38; font-weight: 700; }
tbody tr:nth-child(even) { background: #f7faf9; }
code {
  font-family: "Noto Sans Mono CJK KR", "DejaVu Sans Mono", monospace;
  font-size: 0.9em;
  color: #173f39;
  background: #edf3f2;
  padding: 0.15em 0.3em;
  border-radius: 2px;
}
pre {
  margin: 3mm 0 5mm;
  padding: 3mm;
  border: 1px solid #c4d2cf;
  background: #f4f7f6;
  white-space: pre-wrap;
  break-inside: avoid-page;
}
pre code { padding: 0; background: transparent; color: #263633; }
a { color: #005f86; text-decoration: none; }
img { max-width: 100%; break-inside: avoid; }
strong { color: #173f39; }
"""


def clean_source(value: str) -> str:
    return re.sub(r"\s*(?:filecite|cite)[^]+", "", value)


def render(source: Path, destination: Path) -> None:
    chromium = shutil.which("chromium") or shutil.which("chromium-browser")
    if chromium is None:
        raise SystemExit("Chromium is required to render the roadmap PDF")

    source_text = clean_source(source.read_text(encoding="utf-8"))
    body = markdown.markdown(
        source_text,
        extensions=("extra", "fenced_code", "tables", "sane_lists"),
        output_format="html5",
    )
    html = (
        "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\">"
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        "<title>JetsonControllerApp UI/UX 재설계 및 제품 확장 실행 명세</title>"
        f"<style>{CSS}</style></head><body>{body}</body></html>"
    )

    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="roadmap-render-", dir=source.parent) as temp:
        html_path = Path(temp) / "roadmap.html"
        html_path.write_text(html, encoding="utf-8")
        subprocess.run(
            [
                chromium,
                "--headless",
                "--no-sandbox",
                "--disable-gpu",
                "--disable-dev-shm-usage",
                "--no-pdf-header-footer",
                "--print-to-pdf-no-header",
                "--print-to-pdf=" + str(destination.resolve()),
                html_path.resolve().as_uri(),
            ],
            check=True,
        )
    if not destination.is_file() or destination.stat().st_size == 0:
        raise SystemExit("Chromium did not create the PDF")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    arguments = parser.parse_args()
    render(arguments.source, arguments.destination)


if __name__ == "__main__":
    main()
