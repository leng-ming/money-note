"""
调用本地 ComfyUI 的 API 批量出图，用来挑一个 Android 图标。

用法:
    python gen_icon.py <checkpoint> <width> <height> <steps> <cfg> <前缀> <seed1,seed2,...>

产物落在 ComfyUI 的 output 目录里。
"""
import json
import sys
import time
import urllib.request
import uuid

COMFY = "http://127.0.0.1:8188"

POS = (
    "cute cartoon whale mascot for a mobile app icon, "
    "flat vector illustration, chubby white whale, big friendly eyes with highlight, "
    "happy smile, small water spout on top, tiny pectoral fin, "
    "solid dark green background, centered composition, generous margin around subject, "
    "minimal, clean, kawaii, sticker art, sharp edges, high quality"
)

NEG = (
    "realistic, photo, photographic, 3d render, complex background, scenery, "
    "text, letters, watermark, signature, logo, blurry, low quality, jpeg artifacts, "
    "multiple whales, human, hands, frame, border, gradient background, dark shadows"
)


def build(ckpt, w, h, steps, cfg, prefix, seed):
    return {
        "3": {
            "class_type": "KSampler",
            "inputs": {
                "seed": seed,
                "steps": steps,
                "cfg": cfg,
                "sampler_name": "dpmpp_2m",
                "scheduler": "karras",
                "denoise": 1.0,
                "model": ["4", 0],
                "positive": ["6", 0],
                "negative": ["7", 0],
                "latent_image": ["5", 0],
            },
        },
        "4": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": ckpt}},
        "5": {
            "class_type": "EmptyLatentImage",
            "inputs": {"width": w, "height": h, "batch_size": 1},
        },
        "6": {"class_type": "CLIPTextEncode", "inputs": {"text": POS, "clip": ["4", 1]}},
        "7": {"class_type": "CLIPTextEncode", "inputs": {"text": NEG, "clip": ["4", 1]}},
        "8": {"class_type": "VAEDecode", "inputs": {"samples": ["3", 0], "vae": ["4", 2]}},
        "9": {
            "class_type": "SaveImage",
            "inputs": {"filename_prefix": prefix, "images": ["8", 0]},
        },
    }


def queue(workflow):
    body = json.dumps({"prompt": workflow, "client_id": str(uuid.uuid4())}).encode("utf-8")
    req = urllib.request.Request(
        COMFY + "/prompt", data=body, headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())["prompt_id"]


def wait_for(prompt_id, timeout=600):
    start = time.time()
    while time.time() - start < timeout:
        with urllib.request.urlopen(f"{COMFY}/history/{prompt_id}", timeout=30) as resp:
            history = json.loads(resp.read())
        if prompt_id in history:
            return history[prompt_id]
        time.sleep(2)
    return None


def main():
    ckpt, w, h, steps, cfg, prefix, seeds = (
        sys.argv[1],
        int(sys.argv[2]),
        int(sys.argv[3]),
        int(sys.argv[4]),
        float(sys.argv[5]),
        sys.argv[6],
        sys.argv[7],
    )
    seed_list = [int(s) for s in seeds.split(",")]

    print(f"模型={ckpt}  尺寸={w}x{h}  步数={steps}  cfg={cfg}  seed={seed_list}")
    for seed in seed_list:
        tag = f"{prefix}_{seed}"
        try:
            pid = queue(build(ckpt, w, h, steps, cfg, tag, seed))
            print(f"  已入队 seed={seed}  prompt_id={pid}")
        except Exception as exc:  # noqa: BLE001
            print(f"  seed={seed} 入队失败: {exc}")

    print("等待生成完成...")
    # 简单起见，等所有任务都从队列里消失
    for _ in range(300):
        with urllib.request.urlopen(f"{COMFY}/queue", timeout=30) as resp:
            q = json.loads(resp.read())
        running = len(q.get("queue_running", []))
        pending = len(q.get("queue_pending", []))
        if running == 0 and pending == 0:
            break
        time.sleep(3)

    print("完成。")


if __name__ == "__main__":
    main()
