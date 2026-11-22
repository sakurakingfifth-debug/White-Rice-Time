#!/usr/bin/env python3
"""把 Iconsax Duotone Filled 的路径数据转成本项目的矢量资源。

数据源：pui-demo 仓库的 `composeApp/src/androidMain/assets/icons/icons_iconsax.txt`
（893 个图标，每行 `显示名|透明度[e]@pathData|…`，`e` 表示 evenodd 填充）。

用法：
    git clone --depth 1 https://github.com/sakurakingfifth-debug/pui-demo /tmp/pui-demo
    python tools/gen_icons_from_iconsax.py /tmp/pui-demo

生成物：app/src/main/res/drawable/ic_pui_<slug>.xml（24×24，逐层 fillAlpha）。
加新图标只需在 MAP 里加一行，然后重跑脚本。
"""

import os
import re
import sys

MAP = [
    # slug（PuiIcon 枚举里对应的名字小写） | iconsax 图标名 | 是否丢掉最后一层
    ("coin",     "Coin",              False),
    ("settings", "Setting 2",         False),
    ("refresh",  "Refresh",           False),
    ("key",      "Key",               False),
    ("eye",      "Eye Slash",         True),   # 去掉斜杠当「显示密码」
    ("eye_off",  "Eye Slash",         False),
    ("bolt",     "Flash",             False),
    ("bell",     "Notification Bing", False),
    ("wallet",   "Card Coin",         False),
    ("brush",    "Brush 4",           False),
    ("glow",     "Magic Star",        False),
    ("code",     "Code",              False),
]

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")


def parse_layer(spec: str):
    m = re.match(r"([0-9.]+)(e?)@(.*)$", spec, re.S)
    if not m:
        raise ValueError("坏的分层描述: " + spec[:40])
    return float(m.group(1)), m.group(2) == "e", m.group(3)


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    src = os.path.join(
        sys.argv[1],
        "composeApp", "src", "androidMain", "assets", "icons", "icons_iconsax.txt",
    )
    if not os.path.exists(src):
        print("找不到图标数据:", src)
        return 1

    rows = {}
    with open(src, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line.strip():
                parts = line.split("|")
                rows[parts[0]] = parts[1:]

    for slug, name, drop_last in MAP:
        if name not in rows:
            print("图标不存在:", name)
            return 1
        layers = [parse_layer(s) for s in rows[name]]
        if drop_last:
            layers = layers[:-1]

        body = []
        for alpha, evenodd, d in layers:
            fa = f'\n        android:fillAlpha="{alpha:g}"' if alpha < 1.0 else ""
            body.append(
                "    <path\n"
                f'        android:fillColor="#FF000000"{fa}\n'
                f'        android:pathData="{d}" />'
            )

        xml = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            f"<!-- Iconsax Duotone Filled · {name}"
            f"{'（去掉斜杠）' if drop_last else ''}"
            " · 数据来自 pui-demo/assets/icons/icons_iconsax.txt -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n'
            '    android:height="24dp"\n'
            '    android:viewportWidth="24"\n'
            '    android:viewportHeight="24">\n'
            + "\n".join(body) + "\n</vector>\n"
        )
        path = os.path.join(OUT, f"ic_pui_{slug}.xml")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(xml)
        print(f"{slug:10s} <- {name:20s} layers={len(layers)}")

    print("完成，共", len(MAP), "个图标")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
