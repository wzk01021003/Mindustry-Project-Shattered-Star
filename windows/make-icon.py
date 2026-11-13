#!/usr/bin/env python3
"""程序化生成像素风图标：终代核心 + 碎星棱片 + 星辉尾迹。零外部素材。"""
from pathlib import Path
from PIL import Image, ImageDraw

S = 64
BG = (11, 18, 30, 255)
GRID = (16, 26, 41, 255)
FRAME = (43, 71, 96, 255)
GOLD = (242, 193, 78, 255)
GOLD_DARK = (150, 104, 32, 255)
CYAN = (86, 214, 214, 255)
CYAN_DARK = (35, 112, 130, 255)
WHITE = (232, 244, 255, 255)


def diamond(d, cx, cy, r, fill, outline=None):
    pts = [(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)]
    d.polygon(pts, fill=fill)
    if outline:
        d.line(pts + [pts[0]], fill=outline, width=1)


def build():
    img = Image.new("RGBA", (S, S), BG)
    d = ImageDraw.Draw(img)

    for y in range(0, S, 8):
        for x in range(0, S, 8):
            if (x // 8 + y // 8) % 2 == 0:
                d.rectangle([x, y, x + 7, y + 7], fill=GRID)
    d.rectangle([1, 1, S - 2, S - 2], outline=FRAME, width=1)

    # 四角碎星棱片（赛普洛斯碎裂的星环）
    for cx, cy, r in [(11, 12, 5), (52, 11, 4), (10, 51, 4), (53, 52, 5)]:
        diamond(d, cx, cy, r, CYAN_DARK)
        diamond(d, cx, cy, r - 2, CYAN)

    # 星辉跃迁尾迹
    for i, (x, y) in enumerate([(17, 21), (21, 24), (25, 27), (29, 30)]):
        d.rectangle([x, y, x + 2, y + 2], fill=(CYAN_DARK if i < 2 else CYAN))

    # 终代核心：外层八角框 + 内芯
    cx, cy = 38, 37
    outer = [(cx - 13, cy - 7), (cx - 7, cy - 13), (cx + 7, cy - 13),
             (cx + 13, cy - 7), (cx + 13, cy + 7), (cx + 7, cy + 13),
             (cx - 7, cy + 13), (cx - 13, cy + 7)]
    d.polygon(outer, fill=GOLD_DARK)
    inner = [(cx - 10, cy - 5), (cx - 5, cy - 10), (cx + 5, cy - 10),
             (cx + 10, cy - 5), (cx + 10, cy + 5), (cx + 5, cy + 10),
             (cx - 5, cy + 10), (cx - 10, cy + 5)]
    d.polygon(inner, fill=(28, 40, 56, 255))
    d.line(outer + [outer[0]], fill=GOLD, width=1)
    d.rectangle([cx - 5, cy - 5, cx + 5, cy + 5], fill=GOLD)
    d.rectangle([cx - 2, cy - 2, cx + 2, cy + 2], fill=WHITE)

    return img.resize((256, 256), Image.NEAREST)


def main():
    out = Path(__file__).with_name("app.ico")
    build().save(out, format="ICO",
                 sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print(f"generated {out} ({out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
