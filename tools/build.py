#!/usr/bin/env python3
"""Собирает самодостаточные HTML-файлы: шрифты и текстура зашиваются внутрь,
поэтому презентация и прототип работают без интернета (план Б на защите).

Запуск:  python3 tools/build.py
"""
import base64
import json
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / "tools" / "fonts"

FACES = {
    # семейство в CSS: (папка/префикс файлов, веса, подмножества)
    "Unbounded": ("unbounded", [500, 700, 900], ["cyrillic", "latin", "latin-ext"]),
    "Onest": ("onest", [400, 500, 600, 700], ["cyrillic", "latin", "latin-ext", "math", "symbols"]),
    "JetBrains Mono": ("jetbrains-mono", [400, 600], ["cyrillic", "latin", "latin-ext"]),
}


def font_css() -> str:
    ranges = json.loads((FONTS / "ranges.json").read_text())
    ranges = {k.replace(":mono-", ":"): v for k, v in ranges.items()}
    rules = []
    for family, (prefix, weights, subsets) in FACES.items():
        for w in weights:
            for sub in subsets:
                f = FONTS / f"{prefix}-{sub}-{w}-normal.woff2"
                if not f.exists():
                    continue
                b64 = base64.b64encode(f.read_bytes()).decode()
                rules.append(
                    "@font-face{font-family:'%s';font-style:normal;font-weight:%d;font-display:block;"
                    "src:url(data:font/woff2;base64,%s) format('woff2');unicode-range:%s}"
                    % (family, w, b64, ranges[f"{prefix}:{sub}"])
                )
    return "\n".join(rules)


def noise_png(size: int = 160, seed: int = 7) -> str:
    """Плёночное зерно: серый шум с низкой непрозрачностью, PNG без внешних библиотек."""
    rnd = random.Random(seed)
    raw = bytearray()
    for _ in range(size):
        raw.append(0)
        for _ in range(size):
            v = rnd.randint(0, 255)
            raw += bytes((v, v, v, rnd.randint(10, 34)))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    return "data:image/png;base64," + base64.b64encode(png).decode()


def qr_svg(text: str) -> str:
    """QR-код как inline SVG (нужен пакет qrcode: pip install qrcode)."""
    import qrcode

    q = qrcode.QRCode(border=2, error_correction=qrcode.constants.ERROR_CORRECT_M)
    q.add_data(text)
    q.make(fit=True)
    m = q.get_matrix()
    n = len(m)
    path = "".join(f"M{x} {y}h1v1h-1z" for y, row in enumerate(m) for x, v in enumerate(row) if v)
    return (f'<svg viewBox="0 0 {n} {n}" shape-rendering="crispEdges" aria-label="QR-бирка">'
            f'<rect width="{n}" height="{n}" fill="#efe9dc"/><path d="{path}" fill="#0b0d0a"/></svg>')


QR_TAGS = {
    "@@QR1@@": "ЯБЛОЧНЫЙ ДОЗОР | мешок 1 из 3 | кластер Изборск | демо",
    "@@QR2@@": "ЯБЛОЧНЫЙ ДОЗОР | мешок 2 из 3 | кластер Изборск | демо",
    "@@QR3@@": "ЯБЛОЧНЫЙ ДОЗОР | мешок 3 из 3 | кластер Изборск | демо",
}


def build(src: Path, dst: Path, fonts: str, noise: str) -> None:
    html = src.read_text(encoding="utf-8")
    html = html.replace("/*@FONTS@*/", fonts).replace("@@NOISE@@", noise)
    for token, text in QR_TAGS.items():
        if token in html:
            html = html.replace(token, qr_svg(text))
    dst.write_text(html, encoding="utf-8")
    print(f"{dst.relative_to(ROOT)}: {dst.stat().st_size / 1024:.0f} КБ")


if __name__ == "__main__":
    css, grain = font_css(), noise_png()
    build(ROOT / "prezentaciya" / "deck.src.html", ROOT / "prezentaciya" / "index.html", css, grain)
    proto = ROOT / "prototip" / "prototype.src.html"
    if proto.exists():
        build(proto, ROOT / "prototip" / "index.html", css, grain)
