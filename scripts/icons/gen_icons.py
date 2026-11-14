"""
Generates the launcher icon, its background and themed-icon layer and the notification icon into the app's resources, and
the woven set of module, button and status icons into `scripts/icons/generated/`, which the app does not use for now.
Run from the repository root: `python3 scripts/icons/gen_icons.py [preview.html]`. Edit the shapes here, not the XML.
把启动器图标及其背景、主题图标层和通知图标生成到 App 资源里；模块、按钮与状态图标这套编织图标生成到 `scripts/icons/generated/`，
App 暂时不用。在仓库根目录运行：`python3 scripts/icons/gen_icons.py [preview.html]`。要改形状就改这里，不要手改 XML。

The woven set uses two colour keys: black is the ink and pure green the accent; drawing it needs a colour filter that maps
them to theme colours. Status and notification icons are single-colour and tinted as usual.
编织图标用两种颜色键：黑色是墨色，纯绿是强调色，绘制时需要一个把它们换成主题颜色的颜色滤镜。状态图标和通知图标是单色的，照常着色。
"""
import math
import os
import sys

from geometry import Arc, Line, Strand, ang, circle, circle_intersections, cut, dot, fmt, poly, render, ribbon, roundrect

INK, ACC = "ink", "acc"
W = 1.75
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "drawable")
# Kept outside the resources so they neither ship in the APK nor count as unused. / 放在资源目录外：不进 APK，也不算未使用资源。
GENERATED = os.path.join(os.path.dirname(__file__), "generated")


def strand(prims, color, closed=False, name="", width=W):
    return Strand(prims, color, width, closed, name)


# ---------------- module and button icons / 模块与按钮图标 ----------------

def weather():
    ring = strand(circle(11, 7.5, 3.5), ACC, True, "ring")
    c1, c2, c3 = (16.5, 15), (11.5, 13), (7, 15.5)
    p12 = min(circle_intersections(c1, 4, c2, 4.5), key=lambda p: p[1])
    p23 = min(circle_intersections(c2, 4.5, c3, 3.5), key=lambda p: p[1])
    a12 = ang(c1, p12)
    cloud = strand([
        Line(7, 19, 16.5, 19),
        Arc(*c1, 4, 90, a12 if a12 < 90 else a12 - 360),
        Arc(*c2, 4.5, ang(c2, p12), ang(c2, p23)),
        Arc(*c3, 3.5, ang(c3, p23), -270),
    ], INK, True, "cloud")
    cut(cloud, ring, (14.02, 9.27), 2.35)
    cut(ring, cloud, (8.35, 9.79), 2.35)
    return [ring, cloud], []


def transit():
    body = strand(roundrect(6, 3, 18, 18, 3), INK, True, "body")
    legs = [strand(poly((9, 21), (10.5, 18)), INK), strand(poly((15, 21), (13.5, 18)), INK)]
    band = strand(poly((2, 10.5), (22, 10.5)), ACC, name="band")
    cut(band, body, (6, 10.5), 3)
    cut(body, band, (18, 10.5), 3)
    return [body, *legs, band], [(INK, dot(9.5, 15, 1.1)), (INK, dot(14.5, 15, 1.1))]


def train():
    body = strand(roundrect(6.5, 2.5, 17.5, 15, 3), INK, True, "body")
    sign = strand(poly((10, 6.25), (14, 6.25)), INK, name="sign")
    rails = [strand(poly((9.5, 16.5), (5.5, 21.5)), INK, name="rail"), strand(poly((14.5, 16.5), (18.5, 21.5)), INK, name="rail")]
    sleeper = strand(poly((2.5, 19.5), (21.5, 19.5)), ACC, name="sleeper")
    cut(sleeper, rails[0], (7.1, 19.5), 2)
    cut(sleeper, rails[1], (16.9, 19.5), 2)
    return [body, sign, *rails, sleeper], [(INK, dot(9.5, 11.25, 1.1)), (INK, dot(14.5, 11.25, 1.1))]


def bus():
    body = strand(roundrect(5, 3, 19, 17.5, 2.5), INK, True, "body")
    wheels = [strand(poly((x, 17.5), (x, 20.5)), INK, name="wheel") for x in (8.5, 15.5)]
    mirrors = strand(poly((2, 12.5), (2, 9.5), (22, 9.5), (22, 12.5)), ACC, name="mirrors")
    cut(mirrors, body, (5, 9.5), 2.5)
    cut(body, mirrors, (19, 9.5), 2.5)
    return [body, *wheels, mirrors], [(INK, dot(8.5, 14, 1.1)), (INK, dot(15.5, 14, 1.1))]


def traffic():
    left = strand(poly((4.5, 21), (9, 3)), INK, name="left edge")
    right = strand(poly((19.5, 21), (15, 3)), INK, name="right edge")
    dashes = [strand(poly((12, a), (12, b)), INK, name="dash") for a, b in ((4, 6.5), (9.5, 16.5), (19.5, 21))]
    thread = strand(poly((1.5, 13), (22.5, 13)), ACC, name="thread")
    cut(thread, left, (6.5, 13), 3)
    cut(dashes[1], thread, (12, 13), 2)
    cut(thread, right, (17.5, 13), 3)
    return [left, right, *dashes, thread], []


def reminders():
    top = strand(poly((6, 4), (18, 4), (12, 10.4), closed=True), INK, True)
    bottom = strand(poly((6, 20), (18, 20), (12, 13.6), closed=True), ACC, True)
    return [top, bottom], []


def calendar():
    frame = strand(roundrect(5, 5, 19, 21, 3), INK, True, "frame")
    hooks = [strand(poly((x, 2.5), (x, 7.5)), INK, name="hook") for x in (8.5, 15.5)]
    ribbon = strand(poly((1.5, 11), (22.5, 11)), ACC, name="ribbon")
    for hook, x in zip(hooks, (8.5, 15.5)):
        cut(frame, hook, (x, 5), 2.2)
    cut(ribbon, frame, (5, 11), 2.5)
    cut(frame, ribbon, (19, 11), 2.5)
    today = "M13.8,14.5 h1.4 a0.8,0.8 0 0,1 0.8,0.8 v1.4 a0.8,0.8 0 0,1 -0.8,0.8 h-1.4 a0.8,0.8 0 0,1 -0.8,-0.8 v-1.4 a0.8,0.8 0 0,1 0.8,-0.8 Z"
    return [frame, *hooks, ribbon], [(INK, today)]


def football():
    pitch = strand(roundrect(3, 3.5, 21, 20.5, 2.5), INK, True, "pitch")
    halfway = strand(poly((12, 3.5), (12, 20.5)), ACC, name="halfway line")
    centre = strand(circle(12, 12, 4), INK, True, "centre circle")
    cut(halfway, centre, (12, 8), 2.4)
    cut(centre, halfway, (12, 16), 1.3)
    return [pitch, halfway, centre], []


def news():
    page = strand(roundrect(4, 3, 20, 21, 3), INK, True, "page")
    lines = [strand(poly((7, y), (17, y)), INK, name="text line") for y in (9, 15)]
    bookmark = strand(poly((14, 1), (14, 17)), ACC, name="bookmark")
    cut(page, bookmark, (14, 3), 1.9)
    cut(bookmark, lines[0], (14, 9), 1.5)
    cut(lines[1], bookmark, (14, 15), 1.75)
    return [page, *lines, bookmark], []


def home():
    left = strand(poly((3.5, 12), (15, 2.23)), INK, name="left board")
    right = strand(poly((20.5, 12), (9, 2.23)), ACC, name="right board")
    body = strand([Line(6, 10.3, 6, 19.5), Arc(7.5, 19.5, 1.5, 180, 90), Line(7.5, 21, 16.5, 21),
                   Arc(16.5, 19.5, 1.5, 90, 0), Line(18, 19.5, 18, 10.3)], INK, name="walls")
    door = strand([Line(10.5, 21, 10.5, 17.5), Arc(12, 17.5, 1.5, 180, 360), Line(13.5, 17.5, 13.5, 21)], INK, name="door")
    cut(left, right, (12, 4.775), 2.4)
    return [left, right, body, door], []


def settings():
    rails = [strand(poly((2.5, y), (21.5, y)), INK, name="rail") for y in (8, 16)]
    knobs = [strand(circle(10, 8, 3), ACC, True, "knob"), strand(circle(14, 16, 3), ACC, True, "knob")]
    cut(rails[0], knobs[0], (7, 8), 2.46)
    cut(knobs[0], rails[0], (13, 8), 1.5)
    cut(knobs[1], rails[1], (11, 16), 1.5)
    cut(rails[1], knobs[1], (17, 16), 2.46)
    return [*rails, *knobs], []


def refresh():
    return [
        strand([Arc(12, 12, 7.5, 192.5, 347.5)], INK),
        strand(poly((16.78, 8.43), (19.32, 10.38), (20.8, 7.54)), INK),
        strand([Arc(12, 12, 7.5, 12.5, 167.5)], ACC),
        strand(poly((7.22, 15.57), (4.68, 13.62), (3.2, 16.46)), ACC),
    ], []


def edit():
    pencil = strand(poly((14.5, 5.5), (18.5, 9.5), (8.5, 19.5), (4, 20), (4.5, 15.5), closed=True), INK, True, "pencil")
    band = strand(poly((9.5, 6.5), (18, 15)), ACC, name="band")
    cut(pencil, band, (11.5, 8.5), 2.3)
    cut(band, pencil, (15.5, 12.5), 2.3)
    return [pencil, band], []


def wave(y0, x0, halves, half, amp):
    """
    A wave of circle arcs from a crest at x0 over `halves` half-periods, ending on a crest or trough.
    由圆弧组成的浪：从 x0 处的浪尖开始，经过 `halves` 个半周期，结束在浪尖或浪谷。
    """
    r = ((half / 2) ** 2 + amp ** 2) / (2 * amp)
    da = math.degrees(math.atan2(r - amp, half / 2))
    prims = [Arc(x0, y0 - amp + r, r, -90, -da)]
    x = x0 + half
    for i in range(halves - 1):
        prims.append(Arc(x, y0 + amp - r, r, 180 - da, da) if i % 2 == 0 else Arc(x, y0 - amp + r, r, -180 + da, -da))
        x += half
    last_is_crest = (halves - 1) % 2 == 0
    prims.append(Arc(x, y0 - amp + r, r, -180 + da, -90) if last_is_crest else Arc(x, y0 + amp - r, r, 180 - da, 90))
    return prims


def menu():
    rows = [strand(wave(y, 4, 4, 4, 0.8), INK, name="wave") for y in (6, 12, 18)]
    thread = strand(poly((16, 2.5), (16, 21.5)), ACC, name="thread")
    cut(rows[0], thread, (16, 6.8), 2.5)
    cut(thread, rows[1], (16, 12.8), 6)
    cut(rows[2], thread, (16, 18.8), 2.5)
    return [*rows, thread], []


def done():
    ring = strand(circle(11, 12, 7.5), ACC, True, "ring")
    check = strand(poly((7, 12), (10.5, 15.5), (20.5, 5.5)), INK, name="check")
    cut(ring, check, (17.587, 8.413), 2)
    return [ring, check], []


def back():
    upper = strand(poly((13, 4.5), (4.5, 13)), INK, name="upper arm")
    lower = strand(poly((13, 19.5), (4.5, 11)), ACC, name="lower arm")
    shaft = strand(poly((19.5, 12), (9.5, 12)), INK, name="shaft")
    cut(upper, lower, (6.5, 12), 2.5)
    return [upper, lower, shaft], []


TWO_TONE = {
    "ic_weather": (weather, "Weather module: a sun ring linked with a cloud.", "天气模块：太阳环与云套在一起。"),
    "ic_transit": (transit, "Public transport module: a train front threaded by one line.", "公共交通模块：一根线穿过车头。"),
    "ic_train": (train, "Train commute card: a train over a track whose sleeper runs under both rails.", "火车通勤卡片：轨道上的列车，枕木从两根铁轨下面穿过。"),
    "ic_bus": (bus, "Bus commute card and replacement buses: a bus front threaded by its mirror bar.", "公交通勤卡片与替代巴士：后视镜横杆穿过车头的公交车。"),
    "ic_traffic": (traffic, "Traffic module: a road with a thread woven under, over and under it.", "路况模块：一根线在道路上一挑一压一挑。"),
    "ic_reminders": (reminders, "Expiry reminders module: the two triangles of an hourglass, tip to tip.", "到期提醒模块：沙漏的两个三角尖对尖。"),
    "ic_calendar": (calendar, "Calendar module: hooks over the page edge and a bookmark ribbon through it.", "日历模块：挂钩压住上沿，书签带穿过页面。"),
    "ic_football": (football, "Football module: a pitch whose halfway line weaves with the centre circle.", "足球模块：中线和中圈一压一挑的球场。"),
    "ic_news": (news, "News module: a page with a bookmark woven through its lines.", "新闻模块：书签线和文字行一压一挑的页面。"),
    "ic_home": (home, "Home tab: crossed gable boards over a house.", "首页标签：屋顶两块山墙板交叉。"),
    "ic_settings": (settings, "Settings tab: two knobs threaded on rails.", "设置标签：两个旋钮串在滑轨上。"),
    "ic_refresh": (refresh, "Refresh: two arrows chasing each other.", "刷新：两段箭头首尾相追。"),
    "ic_edit": (edit, "Edit: a pencil with a band wrapped round it.", "编辑：一道带子缠在笔杆上。"),
    "ic_menu": (menu, "Menu and drag handle: three waves woven with one thread.", "菜单与拖动柄：三道浪和一根线织在一起。"),
    "ic_done": (done, "Done: a check mark breaking out of a ring.", "完成：对勾从圆环里冲出来。"),
    "ic_back": (back, "Back: an arrow whose two head strokes cross at the tip, like the home roof.", "返回：箭头的两笔在尖端交叉，像首页图标的屋顶。"),
}

# ---------------- status and marker icons (single colour) / 状态与标记图标（单色） ----------------


def star(cx, cy, outer, inner):
    pts = [(cx + (outer if i % 2 == 0 else inner) * math.cos(math.radians(-90 + 36 * i)),
            cy + (outer if i % 2 == 0 else inner) * math.sin(math.radians(-90 + 36 * i))) for i in range(10)]
    return "M" + " L".join(f"{fmt(x)},{fmt(y)}" for x, y in pts) + " Z"


STAR = star(12, 12.8, 8.2, 3.6)
TRIANGLE = "M10.27,5.2 a2,2 0 0,1 3.46,0 l6.93,12 a2,2 0 0,1 -1.73,3 H4.07 a2,2 0 0,1 -1.73,-3 Z"
CANOPY = "M3.5,12 A8.5,8.5 0 0,1 20.5,12 Z"
STATUS = {
    # Where the tinted shape is the symbol itself it is outlined too: on a tinted pill, such as the weather card's, a soft
    # shape alone fades away.
    # 淡色形状本身就是符号时再加描边：放在有底色的胶囊上（比如天气卡片），只有淡色形状会看不清。
    "ic_status_warn": ("Warning: exclamation mark in an outlined, tinted triangle.", "警示：描边淡色三角里的感叹号。",
                       [("soft", TRIANGLE), ("ink", dot(12, 16.6, 1.2))], [(TRIANGLE, "round"), ("M12,10 V13.2", "round")]),
    "ic_status_umbrella": ("Umbrella: outlined, tinted canopy and a hooked handle.", "带伞：描边的淡色伞面与弯钩伞柄。",
                           [("soft", CANOPY)], [(CANOPY, "round"), ("M12,12 V18 A2,2 0 0,1 8,18", "round")]),
    # The fill and an outline of the same path round off the points. / 同一路径既填充又描边，星角就是圆的。
    "ic_star": ("Starred: a solid star with rounded points.", "已收藏：圆角的实心星。", [("ink", STAR)], [(STAR, "round")]),
}
STATUS_PALETTE = {"ink": "#FF000000", "soft": "#33000000"}

# ---------------- launcher / 启动器 ----------------

WARP, WEFT, SUN = "warp", "weft", "sun"


def woven_day(cx, cy, w, dx, dy, over, gap):
    """
    The character 日 as cloth: two warps and three wefts in plain weave, every ribbon going over one crossing and under the
    next, reaching `over` past the outer ones and leaving `gap` clear where it passes under. Returns fills keyed WARP, WEFT
    and SUN, the middle weft.
    把「日」字织出来：两根经线、三根纬线的平纹，每根带子一上一下，两端比最外的交叉点多伸出 `over`，从下面穿过时两侧各留
    `gap` 的缝。返回按 WARP、WEFT、SUN（中间那根纬线）归类的填充路径。
    """
    xs, ys = (cx - dx, cx + dx), (cy - dy, cy, cy + dy)
    reach, half = w / 2 + over, w / 2 + gap
    fills = {WARP: [], WEFT: [], SUN: []}
    for i, x in enumerate(xs):
        cuts = [(y - half, y + half) for j, y in enumerate(ys) if (i + j) % 2 == 1]
        fills[WARP] += ribbon(ys[0] - reach, ys[-1] + reach, cuts, w, False, x)
    for j, y in enumerate(ys):
        cuts = [(x - half, x + half) for i, x in enumerate(xs) if (i + j) % 2 == 0]
        fills[SUN if j == 1 else WEFT] += ribbon(xs[0] - reach, xs[-1] + reach, cuts, w, True, y)
    return fills


def launcher():
    """
    The woven 日 on the 108dp adaptive-icon canvas, inside the 66dp safe zone.
    108dp 自适应图标画布上织出来的「日」字，在 66dp 安全区内。
    """
    return woven_day(54, 54, w=8, dx=11.6, dy=15.4, over=4.8, gap=1.6)


def notification():
    """
    The same character on the 24dp status-bar canvas, with wider gaps so they survive at that size.
    24dp 状态栏画布上的同一个字；缝留得更宽，这么小也不会糊掉。
    """
    return woven_day(12, 12, w=3.2, dx=5, dy=6.8, over=2.1, gap=1.2)


# Top and bottom of the launcher background. / 启动器背景的上端与下端颜色。
BACKGROUND = ("#FF245E6C", "#FF1B4656")


def background_xml():
    return "\n".join([
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Launcher background: deep teal, a little lighter at the top. -->",
        "<!-- 启动器背景：深青色，上端略亮。 -->",
        "<!-- Generated by scripts/icons/gen_icons.py; edit the script, not this file. -->",
        "<!-- 由 scripts/icons/gen_icons.py 生成；请改脚本，不要改这个文件。 -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    xmlns:aapt="http://schemas.android.com/aapt"',
        '    android:width="108dp"', '    android:height="108dp"',
        '    android:viewportWidth="108"', '    android:viewportHeight="108">',
        '    <path android:pathData="M0,0 H108 V108 H0 Z">',
        '        <aapt:attr name="android:fillColor">',
        '            <gradient', '                android:type="linear"',
        '                android:startX="54"', '                android:startY="0"',
        '                android:endX="54"', '                android:endY="108"',
        f'                android:startColor="{BACKGROUND[0]}"', f'                android:endColor="{BACKGROUND[1]}" />',
        "        </aapt:attr>", "    </path>", "</vector>",
    ]) + "\n"


# ---------------- output / 输出 ----------------


def chunks(ds, limit=760):
    """Splits path data so no single path passes lint's long-path warning. / 拆分路径数据，避免单条路径触发 lint 的长路径警告。"""
    out, cur = [], []
    for d in ds:
        if cur and len(" ".join(cur + [d])) > limit:
            out.append(cur)
            cur = []
        cur.append(d)
    return out + ([cur] if cur else [])


def vector_xml(strokes, fills, size, viewport, palette, en, zh):
    head = ['<?xml version="1.0" encoding="utf-8"?>', f"<!-- {en} -->", f"<!-- {zh} -->",
            "<!-- Generated by scripts/icons/gen_icons.py; edit the script, not this file. -->",
            "<!-- 由 scripts/icons/gen_icons.py 生成；请改脚本，不要改这个文件。 -->",
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
            f'    android:width="{size}dp"', f'    android:height="{size}dp"',
            f'    android:viewportWidth="{viewport}"', f'    android:viewportHeight="{viewport}">']
    body = []
    for (color, width, cap), ds in strokes.items():
        for part in chunks(ds):
            body += ["    <path", f'        android:pathData="{" ".join(part)}"', f'        android:strokeColor="{palette[color]}"',
                     f'        android:strokeWidth="{fmt(width)}"', f'        android:strokeLineCap="{cap}"',
                     '        android:strokeLineJoin="round" />']
    for color, ds in fills.items():
        for part in chunks(ds):
            body += ["    <path", f'        android:pathData="{" ".join(part)}"', f'        android:fillColor="{palette[color]}" />']
    return "\n".join(head + body + ["</vector>"]) + "\n"


def svg(strokes, fills, palette, viewbox, px):
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{px}" height="{px}" viewBox="{viewbox}">']
    for (color, width, cap), ds in strokes.items():
        parts.append(f'<path d="{" ".join(ds)}" fill="none" stroke="{palette[color]}" stroke-width="{fmt(width)}" '
                     f'stroke-linecap="{cap}" stroke-linejoin="round"/>')
    for color, ds in fills.items():
        parts.append(f'<path d="{" ".join(ds)}" fill="{palette[color]}"/>')
    return "".join(parts) + "</svg>"


def write(name, text, folder=OUT):
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, name + ".xml"), "w", encoding="utf-8") as f:
        f.write(text)


def main(preview=None):
    two_tone = {INK: "#FF000000", ACC: "#FF00FF00"}
    note_en = "Black is the ink and green the accent. Crossings are real gaps, so a single-colour tint also works."
    note_zh = "黑色是墨色、绿色是强调色；交叉处是真实的缝，单色着色也能正常显示。"
    cells = []
    for name, (build, en, zh) in TWO_TONE.items():
        strokes, fills = render(*build())
        write(name, vector_xml(strokes, fills, 24, 24, two_tone, f"{en} {note_en}", f"{zh}{note_zh}"), GENERATED)
        cells.append((name, svg(strokes, fills, {INK: "#17212B", ACC: "#0F8A7E"}, "0 0 24 24", 96),
                      svg(strokes, fills, {INK: "#E3E8EE", ACC: "#7FD1C7"}, "0 0 24 24", 24)))

    for name, (en, zh, fill_list, stroke_list) in STATUS.items():
        strokes = {}
        for d, cap in stroke_list:
            strokes.setdefault(("ink", 2.2, cap), []).append(d)
        fills = {}
        for color, d in fill_list:
            fills.setdefault(color, []).append(d)
        write(name, vector_xml(strokes, fills, 24, 24, STATUS_PALETTE, f"{en} Coloured by the Icon tint; tinted shapes keep their own alpha.",
                               f"{zh}颜色由 Icon 的 tint 决定，淡色部分保持各自的透明度。"), GENERATED)

    write("ic_notification", vector_xml({}, notification(), 24, 24, {WARP: "#FFFFFFFF", WEFT: "#FFFFFFFF", SUN: "#FFFFFFFF"},
                                        "Status-bar icon: the woven character 日, white on transparent; the system uses only the alpha channel.",
                                        "状态栏小图标：织出来的「日」字，透明底上的白色；系统只取 alpha 通道着色。"))

    fills = launcher()
    write("ic_launcher_foreground", vector_xml(
        {}, fills, 108, 108, {WARP: "#FFF6F1E6", WEFT: "#FFF6F1E6", SUN: "#FFF6B73C"},
        "The character 日 woven from two warps and three wefts; the middle weft is the golden sun. Inside the 66dp safe zone of the 108dp canvas.",
        "两根经线、三根纬线织成的「日」字，中间那根纬线是金色的太阳。绘制在 108dp 画布中 66dp 的安全区内。"))
    write("ic_launcher_monochrome", vector_xml(
        {}, fills, 108, 108, {WARP: "#FFFFFFFF", WEFT: "#FFFFFFFF", SUN: "#FFFFFFFF"},
        "Themed-icon layer: the foreground shapes in one colour; the gaps are real cuts, so the system tint keeps them.",
        "主题图标层：与前景相同的形状，单色；缝是真实切开的，系统着色后依然保留。"))
    write("ic_launcher_background", background_xml())

    if preview:
        app = svg({}, fills, {WARP: "#F6F1E6", WEFT: "#F6F1E6", SUN: "#F6B73C"}, "18 18 72 72", 192)
        grid = "".join(f'<div class="c">{big}<span class="d">{small}</span><b>{n}</b></div>' for n, big, small in cells)
        with open(preview, "w", encoding="utf-8") as f:
            f.write("<!doctype html><meta charset='utf-8'><style>body{margin:0;padding:24px;background:#F3F4F0;font:13px sans-serif}"
                    f".a{{width:192px;height:192px;border-radius:44px;overflow:hidden;background:linear-gradient(#{BACKGROUND[0][3:]},#{BACKGROUND[1][3:]});margin-bottom:24px}}"
                    ".g{display:grid;grid-template-columns:repeat(7,140px);gap:12px}.c{background:#fff;border-radius:14px;padding:12px;"
                    "display:flex;flex-direction:column;align-items:center;gap:8px}.d{background:#141A21;padding:4px;border-radius:6px;line-height:0}</style>"
                    f"<div class='a'>{app}</div><div class='g'>{grid}</div>")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
