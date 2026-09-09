#!/usr/bin/env python3
"""Rebuild local artifacts only. Does not access Figma, GitHub or any device."""
from pathlib import Path
import subprocess, sys, json, shutil
P=Path(__file__).resolve().parents[1]
def run(script: str) -> None:
    subprocess.run([sys.executable,str(P/"tools"/script)],check=True)
def main() -> None:
    for name in ["design","screens","evidence"]:
        (P/name).mkdir(exist_ok=True)
    run("build_design.py")
    spec=json.loads((P/"design/scene-spec.json").read_text(encoding="utf-8"))
    template=(P/"tools/prototype_template.html").read_text(encoding="utf-8")
    (P/"prototype.html").write_text(template.replace("__SPEC__",json.dumps(spec,ensure_ascii=False,indent=2)),encoding="utf-8")
    run("render_screens.py")
    run("export_snapshots.py")
    run("make_boards.py")
    data=json.loads((P/"design/figma-import-data.json").read_text(encoding="utf-8"))
    code=(P/"figma-plugin/code.template.js").read_text(encoding="utf-8")
    (P/"figma-plugin/code.js").write_text(code.replace("__DATA__",json.dumps(data,ensure_ascii=False,separators=(",",":"))),encoding="utf-8")
    run("validate_prototype.py")
    node=shutil.which("node")
    if node:
        subprocess.run([node,"--check",str(P/"figma-plugin/code.js")],check=True)
        subprocess.run([node,str(P/"tools/test_plugin_mock.js")],check=True)
    else:
        print("Node.js not found: plugin checks NOT RUN.")
    run("audit_set.py")
    print("Local artifacts rebuilt. Real Figma / Android / Jetson verification NOT RUN.")
if __name__ == "__main__":
    main()
