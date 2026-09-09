#!/usr/bin/env python3
"""Check native source tokens and declared opaque role pairs, not rendered pixels."""
import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def luminance(value):
    rgb = [int(value[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in rgb]
    return sum(v * w for v, w in zip(linear, (.2126, .7152, .0722)))


def contrast(a, b):
    low, high = sorted((luminance(a), luminance(b)))
    return (high + .05) / (low + .05)


def check():
    tokens = json.loads((ROOT / 'jetson_slate_harmony_v7/design/tokens.json').read_text())['color']
    source = (ROOT / 'app/src/main/java/com/example/jetsoncontroller/ui/theme/Color.kt').read_text()
    pairs = []
    normal = ('canvas', 'surface', 'sectionBase', 'sectionSoft', 'sectionRaised', 'sectionDanger', 'hero')
    text = [(fg, bg) for fg in ('ink', 'muted') for bg in normal]
    text += [('onPrimary', 'primary'), ('onAccent', 'accent'), ('heroText', 'hero'),
             ('heroMuted', 'hero'), ('success', 'successBg'), ('warning', 'warningBg'),
             ('danger', 'dangerBg'), ('onDanger', 'danger'), ('info', 'infoBg'),
             ('onDisabled', 'disabled'), ('onNavSelected', 'navSelected')]
    graphics = [(fg, bg) for fg in ('controlBorder', 'focusRing', 'primary') for bg in normal]
    graphics += [('focusRing', 'accent')]
    for mode, colors in tokens.items():
        match = re.search(r'val Cobalt' + mode.title() + r' = CobaltColors\((.*?)\n\)', source, re.S)
        if not match:
            raise AssertionError('Missing native palette: ' + mode)
        actual = dict(re.findall(r'(\w+) = Color\(0xFF([A-F0-9]{6})\)', match[1]))
        assert actual == {role: value[1:] for role, value in colors.items()}, mode + ': source token drift'
        for threshold, combinations in ((4.5, text), (3.0, graphics)):
            for foreground, background in combinations:
                ratio = contrast(colors[foreground], colors[background])
                pairs.append(dict(theme=mode, foreground=foreground, background=background,
                                  ratio=ratio, target=threshold, passed=ratio >= threshold))
    failed = [pair for pair in pairs if not pair['passed']]
    return dict(scope='NATIVE_SOURCE_TOKENS_AND_DECLARED_OPAQUE_PAIRS_ONLY',
                tokens=sum(len(t) for t in tokens.values()), pairs=pairs,
                failures=failed, status='FAIL' if failed else 'PASS',
                renderedNativePixels='NOT_CHECKED', physicalDevice='NOT_CHECKED', realFigma='NOT_CHECKED')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = check()
    rendered = json.dumps(result, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + '\n')
    print(f"{result['status']}: {result['tokens']} native tokens, {len(result['pairs'])} role pairs, "
          f"{len(result['failures'])} failures (not a native screenshot test)")
    raise SystemExit(bool(result['failures']))
