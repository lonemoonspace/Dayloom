"""
Builds the home-bar slogan fonts: `res/font/slogan.ttf` (English, Cormorant Garamond Italic) and `res/font-zh/slogan.ttf`
(Chinese, Ma Shan Zheng), each cut down to the glyphs of `app_slogan` so the APK grows by a few KB instead of several MB.
Run from the repository root after changing the slogan: `python3 scripts/fonts/subset_slogan.py` (needs `fonttools`).
生成首页标题栏标语用的字体：`res/font/slogan.ttf`（英文，Cormorant Garamond Italic）与 `res/font-zh/slogan.ttf`
（中文，马善政楷书），都只保留 `app_slogan` 用到的字形，APK 只增加几 KB 而不是几 MB。
改了标语后在仓库根目录运行：`python3 scripts/fonts/subset_slogan.py`（需要 `fonttools`）。

Both fonts are under the SIL Open Font License 1.1 with no Reserved Font Name, so subsetting is allowed; the licence
text is in `res/raw/third_party_licenses.txt`.
两款字体都采用 SIL Open Font License 1.1，且没有保留字体名，因此允许裁剪；许可证全文在 `res/raw/third_party_licenses.txt`。
"""
import io
import os
import re
import urllib.request
import xml.etree.ElementTree as ET

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

# Pinned so a rebuild gives the same glyphs. / 固定提交，重新生成时字形不变。
GOOGLE_FONTS = "https://raw.githubusercontent.com/google/fonts/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/"
RES = "app/src/main/res/"

TARGETS = [
    # (strings file, source font, variable-font axes to pin, output)
    # （字符串文件，源字体，要固定的可变字体轴，输出）
    ("values/strings.xml", "cormorantgaramond/CormorantGaramond-Italic%5Bwght%5D.ttf", {"wght": 500}, "font/slogan.ttf"),
    ("values-zh/strings.xml", "mashanzheng/MaShanZheng-Regular.ttf", None, "font-zh/slogan.ttf"),
]


def slogan(strings_path):
    for node in ET.parse(RES + strings_path).getroot().iter("string"):
        if node.get("name") == "app_slogan":
            return re.sub(r"\\(.)", r"\1", node.text or "")
    raise SystemExit(f"app_slogan missing in {strings_path}")


def build(strings_path, source, axes, out):
    text = slogan(strings_path) + "…"  # The ellipsis is drawn when the slogan is cut. / 标语被截断时要画省略号。
    font = TTFont(io.BytesIO(urllib.request.urlopen(GOOGLE_FONTS + source).read()))
    if axes:
        font = instancer.instantiateVariableFont(font, axes)
    options = subset.Options()
    options.layout_features = ["kern", "liga", "calt"]
    options.name_IDs = ["*"]
    subsetter = subset.Subsetter(options)
    subsetter.populate(text=text)
    subsetter.subset(font)
    os.makedirs(os.path.dirname(RES + out), exist_ok=True)
    font.save(RES + out)
    print(f"{out}: {os.path.getsize(RES + out)} bytes for {text!r}")


for target in TARGETS:
    build(*target)
