"""
Draws the README artwork into `docs/readme/`: the app icon and the module tiles as SVG, and the banner and the card
mosaic in English and Chinese as PNG. The PNGs are HTML pages photographed by Playwright, so they need Node with the
`playwright` package and a Chromium it can launch; the fonts (OFL, from google/fonts) are downloaded once into
`~/.cache/dayloom-readme`. Run from the repository root after the screenshots change: `python3 scripts/readme/make_art.py`.
把 README 用的图画到 `docs/readme/`：应用图标和模块小图标是 SVG，横幅和卡片拼图分中英文两份 PNG。PNG 是用 Playwright 给
HTML 页面拍的照，所以需要 Node、`playwright` 包和它能启动的 Chromium；字体（OFL，来自 google/fonts）第一次运行时下载到
`~/.cache/dayloom-readme`。截图更新后在仓库根目录运行：`python3 scripts/readme/make_art.py`。
"""
import base64
import json
import os
import subprocess
import sys
import tempfile
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "scripts", "icons"))
import gen_icons as gi  # noqa: E402

OUT = os.path.join(ROOT, "docs", "readme")
CACHE = os.path.expanduser("~/.cache/dayloom-readme")
FONTS = {
    "Parisienne": "ofl/parisienne/Parisienne-Regular.ttf",
    "LongCang": "ofl/longcang/LongCang-Regular.ttf",
    "Inter": "ofl/inter/Inter%5Bopsz,wght%5D.ttf",
    "NotoSansSC": "ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf",
}
CREAM, GOLD, TEAL_TOP, TEAL_BOTTOM = "#F6F1E6", "#F6B73C", "#245E6C", "#163C4A"

# Module tiles in README order, each with its woven icon. / README 里模块小图标的顺序和各自的编织图标。
TILES = ["weather", "train", "bus", "transit", "traffic", "done", "reminders", "calendar", "football", "news"]


def font_url(name):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name + ".ttf")
    if not os.path.exists(path):
        urllib.request.urlretrieve("https://raw.githubusercontent.com/google/fonts/main/" + FONTS[name], path)
    return "file://" + path


def icon_svg():
    """The launcher icon on its teal gradient, rounded like a launcher would. / 带青色渐变底、像桌面那样圆角的启动器图标。"""
    fills = gi.launcher()
    body = gi.svg({}, fills, {gi.WARP: CREAM, gi.WEFT: CREAM, gi.SUN: GOLD}, "18 18 72 72", 72)
    inner = body[body.index(">") + 1:-len("</svg>")]
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="18 18 72 72">'
            f'<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{TEAL_TOP}"/>'
            f'<stop offset="1" stop-color="{TEAL_BOTTOM}"/></linearGradient></defs>'
            f'<rect x="18" y="18" width="72" height="72" rx="17" fill="url(#g)"/>{inner}</svg>')


def tile_svg(name):
    """A woven module icon in cream and gold on a small teal tile, legible on light and dark pages alike.
    编织模块图标，奶油色加金色，放在青色小方块上，浅色和深色页面上都看得清。"""
    build = gi.TWO_TONE["ic_" + name][0]
    strokes, fills = gi.render(*build())
    body = gi.svg(strokes, fills, {gi.INK: CREAM, gi.ACC: GOLD}, "0 0 24 24", 24)
    inner = body[body.index(">") + 1:-len("</svg>")]
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="56" height="56" viewBox="-6 -6 36 36">'
            f'<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{TEAL_TOP}"/>'
            f'<stop offset="1" stop-color="{TEAL_BOTTOM}"/></linearGradient></defs>'
            f'<rect x="-6" y="-6" width="36" height="36" rx="9" fill="url(#g)"/>{inner}</svg>')


def data_uri(path):
    with open(path, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


def weave_css(color):
    """The app's plain weave as a CSS background: one short round-capped float per cell, alternating direction.
    App 背景那种平纹，做成 CSS 背景：每格一段两端圆头的短线，横竖交替。"""
    cell, stroke, inset = 22, 0.26 * 22, 0.18 * 22
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{2 * cell}" height="{2 * cell}">'
           f'<g stroke="{color}" stroke-width="{stroke}" stroke-linecap="round">'
           f'<line x1="{inset}" y1="{cell / 2}" x2="{cell - inset}" y2="{cell / 2}"/>'
           f'<line x1="{cell * 1.5}" y1="{inset}" x2="{cell * 1.5}" y2="{cell - inset}"/>'
           f'<line x1="{cell / 2}" y1="{cell + inset}" x2="{cell / 2}" y2="{2 * cell - inset}"/>'
           f'<line x1="{cell + inset}" y1="{cell * 1.5}" x2="{2 * cell - inset}" y2="{cell * 1.5}"/></g></svg>')
    return "url('data:image/svg+xml;base64," + base64.b64encode(svg.encode()).decode() + "')"


def font_faces():
    return "".join(f"@font-face{{font-family:{n};src:url('{font_url(n)}')}}" for n in FONTS)


TEXT = {
    "en": {
        "lang": "en",
        "tagline": "A calm, bilingual daily dashboard for Android",
        "lines": "Weather · Norwegian transit · Traffic · To-dos · Reminders · Calendar · Football · News",
        "chips": ["Android 13+", "English · 中文", "No ads, no tracking"],
        "phones": ("home-en.png", "home-zh-dark.png"),
        "body": "Inter, NotoSansSC",
    },
    "zh": {
        "lang": "zh",
        "tagline": "安静、双语的 Android 日常看板",
        "lines": "天气 · 挪威公共交通 · 路况 · 待办 · 到期提醒 · 日历 · 足球 · 新闻",
        "chips": ["Android 13+", "中文 · English", "无广告，不追踪"],
        "phones": ("home-zh.png", "home-zh-dark.png"),
        "body": "Inter, NotoSansSC",
    },
}


def hero_html(lang):
    t = TEXT[lang]
    front, back = (data_uri(os.path.join(ROOT, "docs", "screenshots", p)) for p in t["phones"])
    chips = "".join(f"<span>{c}</span>" for c in t["chips"])
    return f"""<!doctype html><meta charset="utf-8"><style>{font_faces()}
*{{box-sizing:border-box;margin:0}}
body{{width:1280px;height:600px;overflow:hidden;font-family:{t['body']};-webkit-font-smoothing:antialiased}}
.hero{{position:relative;width:1280px;height:600px;border-radius:28px;overflow:hidden;
  background:{weave_css('rgba(255,255,255,0.045)')},radial-gradient(900px 500px at 15% 0%,#2E7282 0%,transparent 60%),
  linear-gradient(160deg,{TEAL_TOP},{TEAL_BOTTOM});}}
.left{{position:absolute;left:76px;top:78px;width:640px;color:{CREAM}}}
.brand{{display:flex;align-items:center;gap:26px}}
.icon{{width:116px;height:116px;filter:drop-shadow(0 14px 22px rgba(0,0,0,.35))}}
.name{{font:800 76px/1 Inter;letter-spacing:-2px}}
.han{{font:64px/1 LongCang;color:{GOLD};margin-left:14px;position:relative;top:4px}}
.slogan{{margin-top:38px;font:44px/1.15 Parisienne;color:#F9D98E}}
.slogan-zh{{font:38px/1.3 LongCang;color:#F9D98E;margin-top:6px}}
.tag{{margin-top:30px;font:600 25px/1.35 {t['body']};}}
.lines{{margin-top:12px;font:400 15.5px/1.5 {t['body']};color:rgba(246,241,230,.72)}}
.chips{{margin-top:28px;display:flex;gap:10px}}
.chips span{{font:600 15px/1 {t['body']};padding:10px 16px;border-radius:999px;background:rgba(246,241,230,.1);
  border:1px solid rgba(246,241,230,.22)}}
.phone{{position:absolute;width:292px;height:632px;border-radius:44px;background:#0C1418;padding:11px;
  box-shadow:0 40px 70px rgba(0,0,0,.45),0 0 0 1.5px rgba(255,255,255,.12) inset}}
.phone div{{width:100%;height:100%;border-radius:34px;background-size:100% auto;background-position:top;background-repeat:no-repeat}}
.p1{{left:900px;top:58px;transform:rotate(7deg)}}
.p2{{left:742px;top:92px;transform:rotate(-5deg)}}
</style>
<div class="hero">
  <div class="phone p1"><div style="background-image:url('{back}')"></div></div>
  <div class="phone p2"><div style="background-image:url('{front}')"></div></div>
  <div class="left">
    <div class="brand">{icon_svg().replace('<svg ', '<svg class="icon" ', 1)}<div><span class="name">Dayloom</span><span class="han">织日</span></div></div>
    {'<div class="slogan">All threads, for your day</div>' if lang == 'en' else '<div class="slogan-zh">千丝万缕，织成一日</div>'}
    <div class="tag">{t['tagline']}</div>
    <div class="lines">{t['lines']}</div>
    <div class="chips">{chips}</div>
  </div>
</div>"""


MOSAIC = {
    # Three columns of cards, picked so the columns end close together. The page colour is the cards' own, so their margins
    # disappear into it. / 三列卡片，挑得让三列底部差不多齐。底色就是卡片图片自己的页面颜色，图片的留边就融进去了。
    "en": [["weather-en", "home-summary-en", "reminders-en"], ["train-en", "boards-en", "traffic-en"], ["todo-en", "news-en"]],
    "zh": [["weather-zh", "home-summary-zh", "reminders-zh"], ["train-zh", "boards-zh", "traffic-zh"], ["todo-zh", "news-zh"]],
}


def mosaic_html(lang):
    cols = ""
    for col in MOSAIC[lang]:
        cards = "".join(f'<img src="{data_uri(os.path.join(ROOT, "app", "src", "test", "screenshots", n + ".png"))}">' for n in col)
        cols += f'<div class="col">{cards}</div>'
    return f"""<!doctype html><meta charset="utf-8"><style>
*{{box-sizing:border-box;margin:0}}
body{{width:1280px;background:transparent}}
.m{{border-radius:28px;padding:28px 22px;display:flex;gap:6px;align-items:flex-start;
  background:#E8EEF5}}
.col{{flex:1;display:flex;flex-direction:column;gap:0}}
.col img{{width:100%;display:block}}
</style><div class="m">{cols}</div>"""


SHOT_JS = """
const { chromium } = require('playwright');
(async () => {
  const jobs = JSON.parse(process.argv[2]);
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 600 }, deviceScaleFactor: 2 });
  for (const [html, png, selector] of jobs) {
    await page.goto('file://' + html);
    await page.evaluate(() => document.fonts.ready);
    await (await page.$(selector)).screenshot({ path: png, omitBackground: true });
  }
  await browser.close();
})();
"""


def main():
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "icon.svg"), "w", encoding="utf-8") as f:
        f.write(icon_svg())
    for name in TILES:
        with open(os.path.join(OUT, f"tile-{name}.svg"), "w", encoding="utf-8") as f:
            f.write(tile_svg(name))
    jobs = []
    with tempfile.TemporaryDirectory() as tmp:
        for lang in ("en", "zh"):
            for kind, html, selector in (("hero", hero_html(lang), ".hero"), ("cards", mosaic_html(lang), ".m")):
                page = os.path.join(tmp, f"{kind}-{lang}.html")
                with open(page, "w", encoding="utf-8") as f:
                    f.write(html)
                jobs.append([page, os.path.join(OUT, f"{kind}-{lang}.png"), selector])
        script = os.path.join(tmp, "shot.js")
        with open(script, "w", encoding="utf-8") as f:
            f.write(SHOT_JS)
        subprocess.run(["node", script, json.dumps(jobs)], check=True)


if __name__ == "__main__":
    main()
