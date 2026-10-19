#!/usr/bin/env python3
"""Assert R3 source/JSON agreement, 63 pairs in six palettes and 13 fixed pairs."""

import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
THEME = ROOT / "app/src/main/java/com/example/jetsoncontroller/ui/theme"
PALETTES = {
    "STANDARD": "GeoStandard", "NAVY_LIGHT": "GeoNavyLight", "HIGH_CONTRAST": "GeoHighContrast",
    "GRAPHITE": "GeoGraphite", "NAVY_DARK": "GeoNavyDark", "MIST": "GeoMist",
}
ROLE_ALIASES = {"info": "primary", "infoSoft": "primaryTint", "focus": "primary", "switchTrackOn": "primary"}

# R3-final Appendix A.2. Keep thresholds explicit; decorative borders are not control boundaries.
PAIRS = (
    ("text", "surface", 4.5),
    ("text", "canvas", 4.5),
    ("text", "field", 4.5),
    ("text", "fieldPressed", 4.5),
    ("text", "primaryTint", 4.5),
    ("textSecondary", "surface", 4.5),
    ("textSecondary", "sunken", 4.5),
    ("textSecondary", "field", 4.5),
    ("textSecondary", "fieldPressed", 4.5),
    ("textSubtle", "surface", 4.5),
    ("textSubtle", "sunken", 4.5),
    ("textSubtle", "canvas", 4.5),
    ("primary", "surface", 4.5),
    ("primary", "fieldPressed", 4.5),
    ("onPrimary", "primary", 4.5),
    ("onPrimary", "primaryPressed", 4.5),
    ("primaryTintText", "primaryTint", 4.5),
    ("success", "surface", 4.5),
    ("success", "successSoft", 4.5),
    ("warning", "surface", 4.5),
    ("warning", "warningSoft", 4.5),
    ("danger", "surface", 4.5),
    ("danger", "dangerSoft", 4.5),
    ("danger", "fieldPressed", 4.5),
    ("info", "infoSoft", 4.5),
    ("pending", "surface", 4.5),
    ("pending", "pendingSoft", 4.5),
    ("neutral", "surface", 4.5),
    ("neutral", "neutralSoft", 4.5),
    ("text", "successSoft", 4.5),
    ("text", "warningSoft", 4.5),
    ("text", "dangerSoft", 4.5),
    ("onDanger", "danger", 4.5),
    ("onDanger", "dangerPressed", 4.5),
    ("disabledText", "disabledFill", 4.5),
    ("onTopBar", "topBar", 4.5),
    ("onTopBarIcon", "topBar", 4.5),
    ("navSelected", "nav", 4.5),
    ("navUnselected", "nav", 4.5),
    ("onAttention", "attention", 4.5),
    ("onInverse", "inverse", 4.5),
    ("inverseAction", "inverse", 4.5),
    ("textSecondary", "mapPlate", 4.5),
    ("borderStrong", "surface", 3.0),
    ("borderStrong", "field", 3.0),
    ("borderStrong", "fieldPressed", 3.0),
    ("focus", "surface", 3.0),
    ("focus", "canvas", 3.0),
    ("navFocus", "nav", 3.0),
    ("navFocus", "topBar", 3.0),
    ("attention", "nav", 3.0),
    ("attention", "topBar", 3.0),
    ("chromeSuccess", "topBar", 3.0),
    ("chromeWarning", "topBar", 3.0),
    ("chromeDanger", "topBar", 3.0),
    ("chromeInfo", "topBar", 3.0),
    ("chromePending", "topBar", 3.0),
    ("chromeNeutral", "topBar", 3.0),
    ("switchTrackOff", "surface", 3.0),
    ("switchTrackOff", "fieldPressed", 3.0),
    ("switchThumbOff", "switchTrackOff", 3.0),
    ("switchTrackOn", "surface", 3.0),
    ("switchThumbOn", "switchTrackOn", 3.0),
)
FIXED_PAIRS = (
    ("mapZone", "mapCasing", 3.0),
    ("mapTrailPrecise", "mapCasing", 3.0),
    ("mapTrailApproximate", "mapCasing", 3.0),
    ("mapTrailUnknown", "mapCasing", 3.0),
    ("mapGap", "mapCasing", 3.0),
    ("mapDevice", "mapCasing", 3.0),
    ("mediaText", "mediaPlate", 4.5),
    ("mediaLive", "mediaBadge", 4.5),
    ("mediaDelayed", "mediaBadge", 4.5),
    ("mediaPending", "mediaBadge", 4.5),
    ("mediaFailed", "mediaBadge", 4.5),
    ("mediaText", "mediaBadge", 4.5),
    ("mapFocus", "mapCasing", 3.0),
)


def luminance(value: str) -> float:
    if not re.fullmatch(r"#[0-9A-F]{6}", value):
        raise AssertionError(f"Contrast requires opaque sRGB: {value}")
    rgb = [int(value[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in rgb]
    return sum(v * weight for v, weight in zip(linear, (.2126, .7152, .0722)))


def contrast(a: str, b: str) -> float:
    low, high = sorted((luminance(a), luminance(b)))
    return (high + .05) / (low + .05)


def native_roles(name: str, source: str) -> dict:
    block = re.search(rf"val\s+{name}\s*=\s*GeoColors\((.*?)\n\)", source, re.S)
    if not block:
        raise AssertionError(f"Missing native palette: {name}")
    expressions = re.findall(r"^\s*(\w+)\s*=\s*([^,\n]+)", block[1], re.M)
    roles = {}
    for role, expression in expressions:
        literal = re.fullmatch(r"Color\(0x([A-F0-9]{8})\)", expression.strip())
        if not literal:
            raise AssertionError(f"Unsupported {name}.{role}: {expression}")
        argb = literal[1]
        roles[role] = "#" + (argb[2:] if argb.startswith("FF") else argb)
    return roles


def assert_equal(actual: dict, documented: dict, label: str):
    if actual != documented:
        changed = sorted(key for key in actual.keys() | documented.keys() if actual.get(key) != documented.get(key))
        raise AssertionError(f"{label}: source token drift: {changed}")


def check() -> dict:
    doc = json.loads((ROOT / "docs/design/colors.json").read_text(encoding="utf-8"))
    source = (THEME / "Color.kt").read_text(encoding="utf-8")
    identity = (THEME / "GeoPalette.kt").read_text(encoding="utf-8")
    declared_roles = set(re.findall(r"val (\w+): Color", source.split("data class GeoColors(", 1)[1].split(") {", 1)[0]))
    if set(doc["color"]) != set(PALETTES) or len(PAIRS) != 63 or len(FIXED_PAIRS) != 13:
        raise AssertionError("Expected exactly six palettes, 63 themed pairs and 13 fixed pairs")
    assert_equal(doc["roleAliases"], ROLE_ALIASES, "role aliases")
    for alias, target in ROLE_ALIASES.items():
        if not re.search(rf"val {alias}: Color get\(\) = {target}\b", source):
            raise AssertionError(f"Missing native role alias {alias} -> {target}")
    pairs = []
    token_count = 0
    for palette, name in PALETTES.items():
        actual = native_roles(name, source)
        if actual.keys() != declared_roles:
            raise AssertionError(f"{name}: every constructor role must be explicit")
        if not re.search(rf"{palette} -> {name}\b", identity):
            raise AssertionError(f"{palette} does not select {name}")
        assert_equal(actual, doc["color"][palette], palette)
        token_count += len(actual)
        actual.update({alias: actual[target] for alias, target in ROLE_ALIASES.items()})
        for foreground, background, threshold in PAIRS:
            ratio = contrast(actual[foreground], actual[background])
            pairs.append(dict(theme=palette, foreground=foreground, background=background,
                              ratio=ratio, target=threshold, passed=ratio >= threshold))
    block = source.split("object GeoFixedColors {", 1)[1].split("}", 1)[0]
    fixed = {role: "#" + (argb[2:] if argb.startswith("FF") else argb)
             for role, argb in re.findall(r"val (\w+) = Color\(0x([A-F0-9]{8})\)", block)}
    assert_equal(fixed, doc["fixed"], "fixed colours")
    # Worst case: the translucent black media plate lies over a white video pixel.
    plate = fixed["mediaPlate"]
    if not re.fullmatch(r"#[0-9A-F]{8}", plate):
        raise AssertionError("Media plate must include its black alpha")
    alpha = int(plate[1:3], 16) / 255
    fixed["mediaPlate"] = "#" + "".join(f"{round(int(plate[i:i+2], 16)*alpha + 255*(1-alpha)):02X}" for i in (3, 5, 7))
    for foreground, background, threshold in FIXED_PAIRS:
        ratio = contrast(fixed[foreground], fixed[background])
        pairs.append(dict(theme="FIXED", foreground=foreground, background=background,
                          ratio=ratio, target=threshold, passed=ratio >= threshold))
    failed = [pair for pair in pairs if not pair["passed"]]
    return dict(tokens=token_count, themedChecks=378, fixedChecks=13, pairs=pairs, failures=failed,
                status="FAIL" if failed else "PASS", renderedNativePixels="NOT_CHECKED", physicalDevice="NOT_CHECKED")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = check()
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    for pair in result["failures"]:
        print(f"FAIL {pair['theme']}: {pair['foreground']} / {pair['background']} = {pair['ratio']:.3f}, target {pair['target']}")
    print(f"{result['status']}: 63 pairs x 6 palettes = 378 checks; 13 fixed checks; {len(result['failures'])} failures")
    raise SystemExit(bool(result["failures"]))
