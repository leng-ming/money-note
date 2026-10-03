"""
从 ComfyUI 生成的原图做出 (1) 小尺寸对比图 (2) Android 各分辨率的 mipmap 图标。

不抠背景 —— 生成图本身就是「居中主体 + 纯色背景」的方形构图，
直接居中放大裁切即可，比抠图稳得多（抠图试过两版都会误伤主体）。

用法:
    python make_mipmap.py compare          生成对比图
    python make_mipmap.py install <关键字>  把某张图装成项目图标
"""
import glob
import os
import re
import shutil
import sys

import cv2
import numpy as np

SRC_DIR = r"I:\ComfyUI-aki-v3.2\ComfyUI-aki-v3.2\ComfyUI\output"
TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(TOOLS_DIR)
RES_DIR = os.path.join(PROJECT, "app", "src", "main", "res")

# 传统 mipmap 图标尺寸
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# 居中裁掉多少边缘。1.0 = 原图全用，1.3 = 只取中间 77%
ZOOM = 1.28


def load_square(path, zoom=ZOOM):
    img = cv2.imread(path, cv2.IMREAD_COLOR)
    if img is None:
        raise RuntimeError(f"读不了: {path}")
    h, w = img.shape[:2]
    side = int(min(h, w) / zoom)
    y0, x0 = (h - side) // 2, (w - side) // 2
    return img[y0:y0 + side, x0:x0 + side]


def to_size(square, size):
    return cv2.resize(square, (size, size), interpolation=cv2.INTER_AREA)


def rounded(img, radius_ratio=0.22, bg=(255, 255, 255)):
    """给对比图加上圆角遮罩，模拟启动器的圆角图标"""
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


def source_files(pattern="whale_*.png"):
    return sorted(glob.glob(os.path.join(SRC_DIR, pattern)))


def cmd_compare():
    pattern = sys.argv[2] if len(sys.argv) > 2 else "whale_v2_*.png"
    files = source_files(pattern)
    if not files:
        print(f"没找到匹配 {pattern} 的图")
        return
    tiles = []
    for path in files:
        square = load_square(path)
        small = to_size(square, 120)
        tiles.append((os.path.basename(path), rounded(small)))

    pad = 18
    cell = 120 + pad
    canvas = np.full((cell + 34, cell * len(tiles) + pad, 3), 245, np.uint8)
    for i, (name, tile) in enumerate(tiles):
        x = pad + i * cell
        canvas[pad:pad + 120, x:x + 120] = tile
        tag = re.search(r"(\d+)_\d+_?\.png$", name)
        tag = tag.group(1) if tag else name
        cv2.putText(canvas, tag, (x + 30, cell + 22),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (60, 60, 60), 2, cv2.LINE_AA)

    out = os.path.join(TOOLS_DIR, "icon_compare.png")
    cv2.imwrite(out, canvas)

    # 再单独输出一张「缩到 48px」的极限测试，图标在桌面上就差不多这么大
    tiny = [rounded(to_size(load_square(p), 48), 0.22) for p in files]
    tiny_canvas = np.full((48 * 3, 48 * len(tiny) * 3, 3), 245, np.uint8)
    for i, t in enumerate(tiny):
        big = cv2.resize(t, (144, 144), interpolation=cv2.INTER_NEAREST)
        tiny_canvas[0:144, i * 144:(i + 1) * 144] = big
    out2 = os.path.join(TOOLS_DIR, "icon_compare_tiny.png")
    cv2.imwrite(out2, tiny_canvas)

    print(f"对比图: {out}")
    print(f"小尺寸对比: {out2}")


def cmd_install(keyword):
    files = [f for f in source_files() if keyword in f]
    if not files:
        print(f"没找到包含「{keyword}」的图")
        return
    src = files[0]
    square = load_square(src)
    print(f"用这张: {os.path.basename(src)}")

    # 1) 传统 mipmap PNG
    for density, size in DENSITIES.items():
        folder = os.path.join(RES_DIR, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        icon = to_size(square, size)
        # 圆形版本：系统在需要圆形图标时用（部分启动器仍会读 ic_launcher_round）
        cv2.imwrite(os.path.join(folder, "ic_launcher.png"), icon)
        cv2.imwrite(os.path.join(folder, "ic_launcher_round.png"), icon)
        print(f"  mipmap-{density}: {size}x{size}")

    # 2) 删掉自适应图标配置，否则 Android 8+ 会优先用它、忽略上面的 PNG
    anydpi = os.path.join(RES_DIR, "mipmap-anydpi-v26")
    if os.path.isdir(anydpi):
        shutil.rmtree(anydpi)
        print("  已删除 mipmap-anydpi-v26（改用 PNG 图标）")

    # 3) 顺手留一张 512 的预览图给应用商店/文档用
    preview_dir = os.path.join(PROJECT, "docs")
    os.makedirs(preview_dir, exist_ok=True)
    cv2.imwrite(os.path.join(preview_dir, "app-icon.png"), to_size(square, 512))
    print(f"  预览图: docs/app-icon.png")

    print("\n完成。重新构建即可生效。")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    if sys.argv[1] == "compare":
        cmd_compare()
    elif sys.argv[1] == "install":
        cmd_install(sys.argv[2])
    else:
        print(__doc__)


if __name__ == "__main__":
    main()
