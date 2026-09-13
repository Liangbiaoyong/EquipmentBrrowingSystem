# -*- coding: utf-8 -*-
"""站点图标生成器（建筑学院仪器共享平台）

品牌几何取自 Login.vue 的抽象轨道标记与 Layout.vue 的蓝渐变圆角方块，
本脚本是 favicon.svg / favicon.ico / apple-touch-icon.png 的唯一几何来源。
改完数值后重跑：python frontend/scripts/generate-favicon.py
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

OUT_DIR = Path(__file__).resolve().parent.parent / "public"

VIEW = 32.0                      # SVG viewBox 边长，其余尺寸均以它为基准换算
RADIUS_RATIO = 0.30              # 圆角占边长比例，对齐 .brand-mark 的 12/40
BLUE_TOP = (0x25, 0x63, 0xEB)    # #2563eb
BLUE_BOTTOM = (0x3B, 0x82, 0xF6)  # #3b82f6

# Login 的标记是 ±30° 两条轨道交叉，16px 下必然糊成一团，
# 所以只留一条轨道；但 rx/ry 比例(0.444)与倾角(30°)都照搬原标记。
ORBIT_RX = 12.0
ORBIT_RY = 5.333
ORBIT_TILT = 30.0
STROKE = 2.6                     # 比 Login 的 1.8/24 相对加粗，抵住小尺寸的抗锯齿
NODE_R = 3.4                     # 中心节点：Login 是空心环，这里实心化
SUPERSAMPLE = 8                  # 超采样倍数，先放大再 LANCZOS 缩回

ICO_SIZES = (16, 32, 48)
APPLE_TOUCH_SIZE = 180
MIN_STROKE_PX = 2                # 缩到 1px 以下的描边会被抗锯齿洗成灰


def _tile(px, radius_ratio):
    """蓝渐变 + 圆角蒙版的底砖。radius_ratio=0 得到满幅方形（iOS 自行切圆角）。"""
    yy, xx = np.mgrid[0:px, 0:px].astype(np.float32)
    t = ((xx + yy) / (2.0 * (px - 1)))[..., None]
    rgb = (1.0 - t) * np.array(BLUE_TOP, dtype=np.float32) + t * np.array(BLUE_BOTTOM, dtype=np.float32)
    tile = Image.fromarray(rgb.astype(np.uint8), "RGB").convert("RGBA")

    mask = Image.new("L", (px, px), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, px - 1, px - 1], radius=radius_ratio * px, fill=255
    )
    tile.putalpha(mask)
    return tile


def _glyph(px):
    """白色轨道：一条 ±30° 椭圆轨道 + 实心中心节点。"""
    layer = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    scale = px / VIEW
    stroke = max(MIN_STROKE_PX, round(STROKE * scale))

    rad = math.radians(ORBIT_TILT)
    cos_r, sin_r = math.cos(rad), math.sin(rad)
    points = []
    for i in range(721):
        a = 2.0 * math.pi * i / 720
        x, y = ORBIT_RX * math.cos(a), ORBIT_RY * math.sin(a)
        points.append((
            px / 2.0 + (x * cos_r - y * sin_r) * scale,
            px / 2.0 + (x * sin_r + y * cos_r) * scale,
        ))
    draw.line(points, fill=(255, 255, 255, 255), width=stroke, joint="curve")

    r = NODE_R * scale
    draw.ellipse([px / 2.0 - r, px / 2.0 - r, px / 2.0 + r, px / 2.0 + r], fill=(255, 255, 255, 255))
    return layer


def render(size, radius_ratio=RADIUS_RATIO):
    px = size * SUPERSAMPLE
    icon = Image.alpha_composite(_tile(px, radius_ratio), _glyph(px))
    return icon.resize((size, size), Image.LANCZOS)


def svg_source():
    c = VIEW / 2.0
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {VIEW:g} {VIEW:g}" role="img" aria-label="仪器共享平台">
  <!-- 由 frontend/scripts/generate-favicon.py 生成，请勿手改 -->
  <defs>
    <linearGradient id="tile" x1="0" y1="0" x2="{VIEW:g}" y2="{VIEW:g}" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#2563eb"/>
      <stop offset="1" stop-color="#3b82f6"/>
    </linearGradient>
  </defs>
  <rect width="{VIEW:g}" height="{VIEW:g}" rx="{RADIUS_RATIO * VIEW:g}" fill="url(#tile)"/>
  <ellipse cx="{c:g}" cy="{c:g}" rx="{ORBIT_RX:g}" ry="{ORBIT_RY:g}" transform="rotate({ORBIT_TILT:g} {c:g} {c:g})"
           fill="none" stroke="#fff" stroke-width="{STROKE:g}"/>
  <circle cx="{c:g}" cy="{c:g}" r="{NODE_R:g}" fill="#fff"/>
</svg>
"""


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    (OUT_DIR / "favicon.svg").write_text(svg_source(), encoding="utf-8")

    # iOS 会把图标切圆角，所以给它满幅方形，自己不再叠一层圆角
    render(APPLE_TOUCH_SIZE, radius_ratio=0).save(OUT_DIR / "apple-touch-icon.png")

    frames = [render(s) for s in ICO_SIZES]
    frames[-1].save(
        OUT_DIR / "favicon.ico",
        format="ICO",
        sizes=[(s, s) for s in ICO_SIZES],
        append_images=frames[:-1],
    )

    for name in ("favicon.svg", "favicon.ico", "apple-touch-icon.png"):
        print(f"  {name:24s} {(OUT_DIR / name).stat().st_size:>7d} B")


if __name__ == "__main__":
    main()
