"""Verify the real dashboard resource palette against WCAG AA sRGB contrast."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res'
def read(folder):
    return {e.attrib['name']: e.text for e in ET.parse(ROOT / folder / 'colors.xml').getroot()}
def luminance(value):
    rgb = [int(value[i:i+2], 16) / 255 for i in (1, 3, 5)]
    rgb = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in rgb]
    return sum(a * b for a, b in zip(rgb, [.2126, .7152, .0722]))
def contrast(a, b):
    high, low = sorted([luminance(a), luminance(b)], reverse=True)
    return (high + .05) / (low + .05)
for mode, overrides in [('light', {}), ('dark', read('values-night'))]:
    colors = {name.removeprefix("dashboard_"): value for name, value in (read("values") | overrides).items() if name.startswith("dashboard_")}
    pairs = [(foreground, background) for foreground in
             ['text_primary', 'text_secondary', 'brand_primary', 'brand_secondary', 'brand_accent', 'income_blue', 'expense_red', 'brand_transfer']
             for background in ['dark_background', 'dark_surface', 'dark_surface_variant']]
    pairs += [('brand_on_primary', 'brand_primary'), ('brand_on_primary_container', 'brand_primary_container'), ('brand_on_error', 'expense_red')]
    results = [(contrast(colors[a], colors[b]), a, b) for a, b in pairs]
    for ratio, a, b in results:
        assert ratio >= 4.5, f'{mode}: {a}/{b} is only {ratio:.2f}:1'
    worst = min(results)
    print(f'{mode}: {len(results)} pairs pass AA; minimum {worst[0]:.2f}:1 ({worst[1]}/{worst[2]})')
