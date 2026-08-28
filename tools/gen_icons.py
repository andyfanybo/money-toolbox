# -*- coding: utf-8 -*-
"""
生成「省钱工具箱」App 图标:
- 自适应图标前景 (ic_launcher_foreground.png, 108dp 基准, 5 个密度)
- 旧版启动图标 ic_launcher.png / ic_launcher_round.png (5 个密度)
- README 用的 docs/icon.png 与 docs/banner.png

设计: 青翠渐变底 + 白色工具箱 + 金色 ¥ 金币 (省钱 = 工具箱里攒下钱)
运行: python tools/gen_icons.py
"""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DOCS = os.path.join(ROOT, "docs")

EMERALD_A = (16, 185, 129)   # #10B981
EMERALD_B = (4, 120, 87)     # #047857
WHITE = (255, 255, 255, 255)
GOLD = (251, 191, 36, 255)   # #FBBF24
GOLD_DARK = (217, 119, 6, 255)  # #D97706
TRANS = (0, 0, 0, 0)

SS = 4  # 超采样倍数,保证边缘平滑


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(w, h=None):
    """对角渐变底图 (RGB)"""
    if h is None:
        h = w
    small = Image.new("RGB", (256, 256))
    px = small.load()
    for y in range(256):
        for x in range(256):
            px[x, y] = lerp(EMERALD_A, EMERALD_B, (x + y) / 510)
    return small.resize((w, h), Image.BILINEAR)


def line_round(d, p1, p2, fill, width):
    """带圆头的线段"""
    d.line([p1, p2], fill=fill, width=int(width))
    r = width / 2
    for (x, y) in (p1, p2):
        d.ellipse([x - r, y - r, x + r, y + r], fill=fill)


def render_foreground(size_px):
    """图标前景艺术 (透明底)。108 视口坐标,内容落在自适应图标安全区内。"""
    W = size_px * SS
    img = Image.new("RGBA", (W, W), TRANS)
    d = ImageDraw.Draw(img)
    s = W / 108.0

    def P(*pts):
        return [(x * s, y * s) for (x, y) in pts]

    # 1) 工具箱提手 (上拱弧 + 两条腿)
    d.arc([43 * s, 30 * s, 65 * s, 48 * s], start=180, end=360, fill=WHITE, width=int(4.5 * s))
    line_round(d, (43 * s, 39 * s), (43 * s, 47 * s), WHITE, 4.5 * s)
    line_round(d, (65 * s, 39 * s), (65 * s, 47 * s), WHITE, 4.5 * s)

    # 2) 箱盖 + 箱身
    d.rounded_rectangle([28 * s, 47 * s, 80 * s, 57 * s], radius=5 * s, fill=WHITE)
    d.rounded_rectangle([32 * s, 57 * s, 76 * s, 82 * s], radius=6 * s, fill=WHITE)

    # 3) 盖与身之间留一道透缝,增强立体感
    d.line(P((33, 57), (75, 57)), fill=TRANS, width=int(1.6 * s))

    # 4) 金币 (压在箱身右下角)
    d.ellipse([55 * s, 59 * s, 83 * s, 87 * s], fill=GOLD, outline=GOLD_DARK, width=int(2.4 * s))

    # 5) 金币上的 ¥
    wy = 3.0 * s
    line_round(d, (63.6 * s, 65.6 * s), (69 * s, 71.4 * s), WHITE, wy)
    line_round(d, (74.4 * s, 65.6 * s), (69 * s, 71.4 * s), WHITE, wy)
    line_round(d, (69 * s, 71.4 * s), (69 * s, 80.6 * s), WHITE, wy)
    line_round(d, (63.9 * s, 74.2 * s), (74.1 * s, 74.2 * s), WHITE, wy)
    line_round(d, (63.9 * s, 78.2 * s), (74.1 * s, 78.2 * s), WHITE, wy)

    return img.resize((size_px, size_px), Image.LANCZOS)


def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return m


def circle_mask(size):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size - 1, size - 1], fill=255)
    return m


def render_launcher(size_px, shape="squircle"):
    """旧版启动图标: 渐变底 + 居中艺术"""
    W = size_px * SS
    bg = gradient(W).convert("RGBA")
    if shape == "squircle":
        mask = rounded_mask(W, int(0.225 * W))
    else:
        mask = circle_mask(W)
    img = Image.new("RGBA", (W, W), TRANS)
    img.paste(bg, (0, 0), mask)
    art = render_foreground(int(W * 0.80))
    img.alpha_composite(art, ((W - art.width) // 2, (W - art.height) // 2))
    return img.resize((size_px, size_px), Image.LANCZOS)


DENSITIES = [("mdpi", 1.0), ("hdpi", 1.5), ("xhdpi", 2.0), ("xxhdpi", 3.0), ("xxxhdpi", 4.0)]


def main():
    # 自适应图标前景
    for name, mult in DENSITIES:
        d = os.path.join(RES, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        fg = int(108 * mult)
        render_foreground(fg).save(os.path.join(d, "ic_launcher_foreground.png"))
        render_launcher(int(48 * mult), "squircle").save(os.path.join(d, "ic_launcher.png"))
        render_launcher(int(48 * mult), "circle").save(os.path.join(d, "ic_launcher_round.png"))
        print("mipmap-%s done" % name)

    os.makedirs(DOCS, exist_ok=True)

    # README 图标
    render_launcher(512, "squircle").save(os.path.join(DOCS, "icon.png"))
    render_foreground(512).save(os.path.join(DOCS, "icon_foreground.png"))

    # README 横幅 (按目标比例绘制,避免拉伸变形)
    bw, bh = 1200, 630
    W, H = bw * SS, bh * SS
    banner = gradient(W, H).convert("RGBA")
    art = render_foreground(int(H * 0.70))
    ax = int(W * 0.075)
    banner.alpha_composite(art, (ax, (H - art.height) // 2))

    def font(size_pt, bold=True):
        candidates = [
            r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc",
            r"C:\Windows\Fonts\simhei.ttf",
            r"C:\Windows\Fonts\arialbd.ttf",
        ]
        for c in candidates:
            try:
                return ImageFont.truetype(c, size_pt)
            except Exception:
                continue
        return ImageFont.load_default()

    d = ImageDraw.Draw(banner)
    title_f = font(int(H * 0.168))
    sub_f = font(int(H * 0.060), bold=False)
    tx = ax + art.width + int(W * 0.05)
    d.text((tx, int(H * 0.265)), "省钱工具箱", font=title_f, fill=WHITE)
    d.text((tx, int(H * 0.515)), "免费时长到期早知道", font=sub_f, fill=(255, 255, 255, 225))
    d.text((tx, int(H * 0.615)), "缴费省钱时机刚刚好", font=sub_f, fill=(255, 255, 255, 225))

    banner = banner.resize((bw, bh), Image.LANCZOS)
    banner.save(os.path.join(DOCS, "banner.png"))
    print("docs done")


if __name__ == "__main__":
    main()
