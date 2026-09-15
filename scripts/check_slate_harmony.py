#!/usr/bin/env python3
"""Check documented GEO colors against Compose tokens and declared contrast pairs."""

import argparse
import json
from pathlib import Path
import re
from typing import Dict

ROOT = Path(__file__).resolve().parents[1]
PALETTE_SOURCE = ROOT / "app/src/main/java/com/example/jetsoncontroller/ui/theme/GeoPalette.kt"
ROLE_SOURCE = ROOT / "app/src/main/java/com/example/jetsoncontroller/ui/theme/Color.kt"


def luminance(value: str) -> float:
    rgb = [int(value[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in rgb]
    return sum(v * weight for v, weight in zip(linear, (.2126, .7152, .0722)))


def contrast(a: str, b: str) -> float:
    low, high = sorted((luminance(a), luminance(b)))
    return (high + .05) / (low + .05)


def native_roles(mode: str) -> Dict[str, str]:
    palette_source = PALETTE_SOURCE.read_text()
    role_source = ROLE_SOURCE.read_text()
    palette = {
        name: f"#{value.upper()}"
        for name, value in re.findall(
            r"val\s+(\w+)\s*=\s*Color\(0xFF([A-Fa-f0-9]{6})\)", palette_source
        )
    }
    block = re.search(rf"val\s+{mode}\s*=\s*GeoColors\((.*?)\n\)", role_source, re.S)
    if not block:
        raise AssertionError(f"Missing native palette: {mode}")
    roles: Dict[str, str] = {}
    for name, expression in re.findall(r"^\s*(\w+)\s*=\s*([^,\n]+)", block.group(1), re.M):
        expression = expression.strip()
        palette_ref = re.fullmatch(r"GeoPalette\.(\w+)", expression)
        literal = re.fullmatch(r"Color\(0xFF([A-Fa-f0-9]{6})\)", expression)
        if palette_ref:
            try:
                roles[name] = palette[palette_ref.group(1)]
            except KeyError as error:
                raise AssertionError(f"Unresolved GeoPalette reference: {expression}") from error
        elif literal:
            roles[name] = f"#{literal.group(1).upper()}"
        elif name != "overlay":
            raise AssertionError(f"Unsupported {mode}.{name} expression: {expression}")
    return roles


TEXT_PAIRS = (
    ("ink", "canvas"), ("ink", "surface"),
    ("muted", "canvas"), ("muted", "surface"),
    ("onPrimary", "primary"), ("onAccent", "accent"),
    ("heroText", "hero"), ("heroMuted", "hero"),
    ("success", "successBg"), ("warning", "warningBg"),
    ("danger", "dangerBg"), ("info", "infoBg"),
    ("pending", "pendingBg"), ("unknown", "unknownBg"),
    ("onDisabled", "disabled"), ("onNavSelected", "navSelected"),
)

GRAPHICS_PAIRS = (
    # Text fields paint an opaque surface under this boundary; canvas is not its pair.
    ("controlBorder", "surface"),
    ("focusRing", "canvas"),
    ("focusRing", "surface"),
)


def check() -> dict:
    tokens = json.loads((ROOT / "docs/design/colors.json").read_text())["color"]
    pairs = []
    for documented_mode, native_mode in (("light", "GeoLight"), ("dark", "GeoDark")):
        documented = tokens[documented_mode]
        actual = native_roles(native_mode)
        if actual != documented:
            missing = sorted(actual.keys() - documented.keys())
            extra = sorted(documented.keys() - actual.keys())
            changed = sorted(
                role for role in actual.keys() & documented.keys()
                if actual[role].upper() != documented[role].upper()
            )
            raise AssertionError(
                f"{documented_mode}: source token drift; "
                f"missing={missing}, extra={extra}, changed={changed}"
            )
        for threshold, combinations in ((4.5, TEXT_PAIRS), (3.0, GRAPHICS_PAIRS)):
            for foreground, background in combinations:
                ratio = contrast(documented[foreground], documented[background])
                pairs.append({
                    "theme": documented_mode,
                    "foreground": foreground,
                    "background": background,
                    "ratio": ratio,
                    "target": threshold,
                    "passed": ratio >= threshold,
                })
    failed = [pair for pair in pairs if not pair["passed"]]
    return {
        "scope": "NATIVE_GEO_SOURCE_TOKENS_AND_DECLARED_OPAQUE_PAIRS_ONLY",
        "tokens": sum(len(theme) for theme in tokens.values()),
        "pairs": pairs,
        "failures": failed,
        "status": "FAIL" if failed else "PASS",
        "renderedNativePixels": "NOT_CHECKED",
        "physicalDevice": "NOT_CHECKED",
        "realFigma": "NOT_CHECKED",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = check()
    rendered = json.dumps(result, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n")
    print(
        f"{result['status']}: {result['tokens']} native tokens, "
        f"{len(result['pairs'])} role pairs, {len(result['failures'])} failures "
        "(not a native screenshot test)"
    )
    raise SystemExit(bool(result["failures"]))
