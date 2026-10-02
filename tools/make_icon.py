"""
把 ComfyUI 生成的鲸鱼图处理成干净的 App 图标。

思路：
1. 把绿色/青色的背景抠成透明（用洪水填充只吃「与画面边缘连通」的部分，
   这样鲸鱼身上偶然出现的浅绿不会被误删）
2. 裁到鲸鱼主体的外接方形，留出安全边距
3. 缩放到 Android 自适应图标前景所需的 432x432，输出 PNG

用法: python make_icon.py
"""
import glob
import os

import cv2
import numpy as np

SRC_DIR = r"I:\ComfyUI-aki-v3.2\ComfyUI-aki-v3.2\ComfyUI\output"
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "icon_out")

# Android 自适应图标前景：108dp，xxxhdpi 下是 432px
FOREGROUND_SIZE = 432
# 前景内容要落在中间 66dp 的安全区内，所以主体最多占约 72/108
CONTENT_RATIO = 0.72


def strip_background(bgr):
    """把背景变透明，返回 BGRA

    用「从四角洪水填充」而不是按 HSV 色相一刀切：
    色相阈值会把鲸鱼身上偏绿的浅灰一起吃掉（试过，鲸鱼只剩描边）；
    按「与角落颜色相近」来扩散则只看相似度，鲸鱼是什么颜色都安全。
    """
    h, w = bgr.shape[:2]
    smooth = cv2.GaussianBlur(bgr, (5, 5), 0)

    mask = np.zeros((h + 2, w + 2), np.uint8)
    flags = 4 | cv2.FLOODFILL_MASK_ONLY | (255 << 8)
    diff = (30, 30, 30)
    for seed in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
        cv2.floodFill(smooth, mask, seed, 0, diff, diff, flags=flags)

    outside = mask[1:-1, 1:-1]
    alpha = cv2.bitwise_not(outside)
    # 去掉零星残留的小白点
    alpha = cv2.morphologyEx(alpha, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    # 边缘轻微羽化，缩放后不会有锯齿
    alpha = cv2.GaussianBlur(alpha, (5, 5), 0)

    b, g, r = cv2.split(bgr)
    return cv2.merge([b, g, r, alpha])


def crop_to_content(bgra):
    """裁到主体的外接方形，四周留一点呼吸空间"""
    alpha = bgra[:, :, 3]
    ys, xs = np.where(alpha > 24)
    if len(xs) == 0:
        return bgra

    x0, x1 = int(xs.min()), int(xs.max())
    y0, y1 = int(ys.min()), int(ys.max())
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    side = int(max(x1 - x0, y1 - y0) * 1.12)

    half = side // 2
    h, w = alpha.shape
    nx0, ny0 = max(0, cx - half), max(0, cy - half)
    nx1, ny1 = min(w, cx + half), min(h, cy + half)

    cropped = bgra[ny0:ny1, nx0:nx1]
    # 补成正方形（用透明像素）
    ch, cw = cropped.shape[:2]
    size = max(ch, cw)
    canvas = np.zeros((size, size, 4), np.uint8)
    oy, ox = (size - ch) // 2, (size - cw) // 2
    canvas[oy:oy + ch, ox:ox + cw] = cropped
    return canvas


def compose_foreground(bgra):
    """把主体放进 432x432 的画布中央，并限制在安全区内"""
    h, w = bgra.shape[:2]
    target = int(FOREGROUND_SIZE * CONTENT_RATIO)
    scale = target / max(h, w)
    new_size = (max(1, int(w * scale)), max(1, int(h * scale)))
    resized = cv2.resize(bgra, new_size, interpolation=cv2.INTER_AREA)

    canvas = np.zeros((FOREGROUND_SIZE, FOREGROUND_SIZE, 4), np.uint8)
    nh, nw = resized.shape[:2]
    oy, ox = (FOREGROUND_SIZE - nh) // 2, (FOREGROUND_SIZE - nw) // 2
    canvas[oy:oy + nh, ox:ox + nw] = resized
    return canvas


def preview(bgra, bg_color=(0x32, 0x7D, 0x2E)):
    """合成到品牌绿背景上，方便肉眼看效果"""
    h, w = bgra.shape[:2]
    canvas = np.zeros((h, w, 3), np.uint8)
    canvas[:] = bg_color
    alpha = bgra[:, :, 3:4].astype(np.float32) / 255.0
    canvas = (bgra[:, :, :3].astype(np.float32) * alpha
              + canvas.astype(np.float32) * (1 - alpha)).astype(np.uint8)
    return canvas


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    files = sorted(glob.glob(os.path.join(SRC_DIR, "whale_icon_*.png")))
    if not files:
        print("没找到生成的图")
        return

    for path in files:
        name = os.path.splitext(os.path.basename(path))[0]
        bgr = cv2.imread(path, cv2.IMREAD_COLOR)
        if bgr is None:
            print(f"读不了: {path}")
            continue

        bgra = strip_background(bgr)
        bgra = crop_to_content(bgra)
        fg = compose_foreground(bgra)

        out_fg = os.path.join(OUT_DIR, f"{name}_fg.png")
        out_preview = os.path.join(OUT_DIR, f"{name}_preview.png")
        cv2.imwrite(out_fg, fg)
        cv2.imwrite(out_preview, preview(fg))

        kept = float((fg[:, :, 3] > 24).sum()) / (FOREGROUND_SIZE ** 2)
        print(f"{name}: 前景保留 {kept*100:.1f}%  ->  {os.path.basename(out_fg)}")

    print(f"\n输出目录: {OUT_DIR}")


if __name__ == "__main__":
    main()
