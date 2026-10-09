#!/usr/bin/env python3
"""Compare lunar-java with the Hong Kong Observatory conversion tables, day by day (design §11.5).

Developer machine only. The tables are downloaded to a temporary directory and deleted afterwards; their terms allow
non-commercial use only, so they must never be committed or shipped (design §19). The comparison itself is the JVM test
LunarReferenceTest, which is skipped unless this script passes it the reference file.

Usage: python3 scripts/verify_lunar.py [first_year] [last_year]     (default 1901 2100)

在开发机上把 lunar-java 与香港天文台的公历农历对照表逐日比对（设计文档 §11.5）。
只在开发机运行。对照表下载到临时目录，用完即删；其条款只允许非商业使用，所以永不入库、不进 APK（设计文档 §19）。
比对本身是 JVM 测试 LunarReferenceTest，只有本脚本把参考文件传给它时才会运行，其他场合一律跳过。

用法：python3 scripts/verify_lunar.py [起始年] [结束年]     （默认 1901 2100）
"""

import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

URL = "https://www.hko.gov.hk/en/gts/time/calendar/text/files/T{year}e.txt"

# Observatory names in calendar-year order, matching SolarTerm in LunarProvider.kt.
# 天文台的节气英文名，按公历年内顺序，与 LunarProvider.kt 的 SolarTerm 一致。
TERMS = [
    "Moderate Cold", "Severe Cold", "Spring Commences", "Spring Showers", "Insects Waken", "Vernal Equinox",
    "Bright & Clear", "Corn Rain", "Summer Commences", "Corn Forms", "Corn on Ear", "Summer Solstice",
    "Moderate Heat", "Great Heat", "Autumn Commences", "End of Heat", "White Dew", "Autumnal Equinox",
    "Cold Dew", "Frost", "Winter Commences", "Light Snow", "Heavy Snow", "Winter Solstice",
]

ROW = re.compile(r"^(\d{4})/(\d{1,2})/(\d{1,2})\s+(.+?)\s{2,}\S+day\s*(.*)$")
MONTH_START = re.compile(r"^(\d{1,2})(?:st|nd|rd|th) Lunar Month$", re.IGNORECASE)


def download(year: int) -> str:
    for attempt in range(3):
        try:
            with urllib.request.urlopen(URL.format(year=year), timeout=30) as response:
                return response.read().decode("utf-8", errors="replace")
        except OSError:
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)
    raise AssertionError("unreachable")


def convert(first: int, last: int, out: Path) -> int:
    """Writes `date,month,day,leap,term` rows; returns the number of days. / 写出 `日期,月,日,闰月,节气` 行，返回天数。"""
    month, leap, previous_marker, rows = None, False, None, 0
    with out.open("w", encoding="utf-8") as sink:
        for year in range(first, last + 1):
            for line in download(year).splitlines():
                match = ROW.match(line.strip())
                if not match:
                    continue
                y, m, d, lunar, term = match.groups()
                start = MONTH_START.match(lunar.strip())
                if start:
                    number = int(start.group(1))
                    # A month number repeating the previous one is the leap month. / 月份号与上一个相同即为闰月。
                    leap = number == previous_marker
                    month, previous_marker, day = number, number, 1
                else:
                    day = int(lunar)
                # Days before the first month marker of the first year have no known month. / 首年第一个月份标记之前的日子不知道月份。
                if month is None:
                    continue
                term = term.strip()
                term_index = TERMS.index(term) if term else -1
                sink.write(f"{int(y):04d}-{int(m):02d}-{int(d):02d},{month},{day},{int(leap)},{term_index}\n")
                rows += 1
            print(f"\r{year}", end="", flush=True)
    print()
    return rows


def main() -> int:
    first = int(sys.argv[1]) if len(sys.argv) > 1 else 1901
    last = int(sys.argv[2]) if len(sys.argv) > 2 else 2100
    root = Path(__file__).resolve().parent.parent
    with tempfile.TemporaryDirectory(prefix="dayloom-lunar-") as tmp:
        reference = Path(tmp) / "reference.csv"
        print(f"Downloading Hong Kong Observatory tables {first}-{last} into {tmp}")
        print(f"{convert(first, last, reference)} days converted; running LunarReferenceTest")
        gradlew = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
        command = [
            str(gradlew), ":app:testDebugUnitTest",
            "--tests", "io.github.lonemoonspace.dayloom.feature.calendar.LunarReferenceTest",
            # The reference file is not a task input, so force the test to run. / 参考文件不是任务输入，所以强制运行。
            "--rerun", "-i",
        ]
        result = subprocess.run(command, cwd=root, env={**os.environ, "DAYLOOM_LUNAR_REFERENCE": str(reference)},
                                capture_output=True, text=True)
        summary = [l for l in result.stdout.splitlines() if "checked" in l and "mismatches" in l or "observatory" in l]
        print("\n".join(summary) or result.stdout[-3000:])
        if result.returncode != 0:
            print(result.stderr[-3000:], file=sys.stderr)
        return result.returncode


if __name__ == "__main__":
    sys.exit(main())
