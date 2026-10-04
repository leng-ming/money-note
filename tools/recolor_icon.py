"""
把用户找的蓝色鲸鱼图标改成品牌绿，并生成 Android 各分辨率的 mipmap。

为什么用色相旋转而不是图生图（img2img）：
img2img 会连造型一起改，账本、算盘、鲸鱼的比例都可能变；
色相旋转只动颜色通道，主体形状 100% 保留，正是我们想要的。

用法: python recolor_icon.py <源图> [目标色相偏移]
"""
import os
import sys

import cv2
import numpy as np

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(PROJECT, "app", "src", "main", "res")
TOOLS_DIR = os.path.join(PROJECT, "tools")

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# 品牌绿 #2E7D32 在 OpenCV 的 HSV 里 H≈61，蓝鲸的 H 大约 105，所以左移 44
DEFAULT_SHIFT = -44


def blue_to_green(bgr, shift=DEFAULT_SHIFT):
    """只把明显的蓝色像素整体挪到绿色，其他颜色（金币黄、腮红粉）原样不动"""
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV)
    h = hsv[:, :, 0].astype(np.int16)
    s = hsv[:, :, 1].astype(np.int16)
    v = hsv[:, :, 2].astype(np.int16)

    # 蓝色区间。带饱和度和亮度门槛，免得把灰白背景和高光也一起转了
    is_blue = (h >= 88) & (h <= 142) & (s > 32) & (v > 45)

    h_new = h.copy()
    h_new[is_blue] = (h[is_blue] + shift) % 180

    out = cv2.merge([
        h_new.astype(np.uint8),
        s.astype(np.uint8),
        v.astype(np.uint8),
    ])
    return cv2.cvtColor(out, cv2.COLOR_HSV2BGR)


def load_square(path, zoom=1.0):
    # cv2.imread 在 Windows 上读不了含中文的路径，用 fromfile + imdecode 绕过
    data = np.fromfile(path, dtype=np.uint8)
    img = cv2.imdecode(data, cv2.IMREAD_COLOR)
    if img is None:
        raise RuntimeError(f"读不了: {path}")
    h, w = img.shape[:2]
    side = int(min(h, w) / zoom)
    y0, x0 = (h - side) // 2, (w - side) // 2
    return img[y0:y0 + side, x0:x0 + side]


def rounded(img, radius_ratio=0.22, bg=(255, 255, 255)):
    h, w = img.shape[:2]
    radius = int(min(h, w) * radius_ratio)
    mask = np.zeros((h, w), np.uint8)
    cv2.rectangle(mask, (radius, 0), (w - radius, h), 255, -1)
    cv2.rectangle(mask, (0, radius), (w, h - radius), 255, -1)
    for cx, cy in ((radius, radius), (w - radius, radius),
                   (radius, h - radius), (w - radius, h - radius)):
        cv2.circle(mask, (cx, cy), radius, 255, -1)
    canvas = np.zeros((h, w, 3), np.uint8)
    canvas[:] = bg
    a = (mask.astype(np.float32) / 255.0)[:, :, None]
    return (img.astype(np.float32) * a + canvas.astype(np.float32) * (1 - a)).astype(np.uint8)


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\LM\Downloads\微信图片_20261003194019_47_27.png"
    shift = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_SHIFT

    square = load_square(src, zoom=1.0)
    green = blue_to_green(square, shift)

    # 1) 对比预览：原图 vs 改色后，各缩到 150px 加圆角
    before = rounded(cv2.resize(square, (150, 150), interpolation=cv2.INTER_AREA))
    after = rounded(cv2.resize(green, (150, 150), interpolation=cv2.INTER_AREA))
    tiny = rounded(cv2.resize(green, (48, 48), interpolation=cv2.INTER_AREA))
    tiny_big = cv2.resize(tiny, (144, 144), interpolation=cv2.INTER_NEAREST)

    pad = 16
    canvas = np.full((150 + 2 * pad + 30, 150 * 3 + pad * 4, 3), 244, np.uint8)
    canvas[pad:pad + 150, pad:pad + 150] = before
    canvas[pad:pad + 150, pad * 2 + 150:pad * 2 + 300] = after
    canvas[pad:pad + 144, pad * 3 + 300:pad * 3 + 444] = tiny_big
    for i, tag in enumerate(("原图(蓝)", "改色后(绿)", "48px 实际大小")):
        cv2.putText(canvas, tag, (pad + i * (150 + pad) + 6, 150 + pad + 22),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, (70, 70, 70), 1, cv2.LINE_AA)
    out_cmp = os.path.join(TOOLS_DIR, "icon_recolor_compare.png")
    cv2.imwrite(out_cmp, canvas)
    print(f"对比图: {out_cmp}")

    # 2) 各分辨率 mipmap
    for density, size in DENSITIES.items():
        folder = os.path.join(RES_DIR, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        icon = cv2.resize(green, (size, size), interpolation=cv2.INTER_AREA)
        cv2.imwrite(os.path.join(folder, "ic_launcher.png"), icon)
        cv2.imwrite(os.path.join(folder, "ic_launcher_round.png"), icon)
        print(f"  mipmap-{density}: {size}x{size}")

    docs = os.path.join(PROJECT, "docs")
    os.makedirs(docs, exist_ok=True)
    cv2.imwrite(os.path.join(docs, "app-icon.png"),
                cv2.resize(green, (512, 512), interpolation=cv2.INTER_AREA))
    print("  预览图: docs/app-icon.png")

    # 3) 原图也留一份到 docs，方便以后回看
    cv2.imwrite(os.path.join(docs, "app-icon-source.png"), square)
    print("  原图存档: docs/app-icon-source.png")


if __name__ == "__main__":
    main()
