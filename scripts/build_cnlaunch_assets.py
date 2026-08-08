#!/usr/bin/env python3
"""Build dtc-descriptions.json and menu-trees.json from an exported cnlaunch folder (Stream E).

Usage (on PC after tablet LAN export + unzip):
  python scripts/build_cnlaunch_assets.py path/to/cnlaunch
  python scripts/build_cnlaunch_assets.py path/to/cnlaunch --out app/src/main/assets/cnlaunch

Copies results into the Android assets tree or prints paths for adb push to
  /data/data/com.caseforge.scanner/files/cnlaunch-assets/
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

DTC_RE = re.compile(r"\b([PCBU][0-9A-F]{4})\b", re.I)
MENU_HINTS = ("menu", "tree", "nav", "path")


def crawl_dtcs(root: Path) -> dict[str, dict[str, str]]:
    out: dict[str, dict[str, str]] = {"OBD-II": {}}
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if path.suffix.lower() not in {".txt", ".xml", ".ini", ".csv", ""}:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for m in DTC_RE.finditer(text):
            code = m.group(1).upper()
            if code in out["OBD-II"]:
                continue
            # grab trailing description fragment on same line if present
            line = text[max(0, text.rfind("\n", 0, m.start()) + 1) : text.find("\n", m.end())]
            desc = line.replace(code, "").strip(" :-|\t")
            if len(desc) >= 8:
                out["OBD-II"][code] = desc[:240]
    return out


def crawl_menus(root: Path) -> dict[str, list[str]]:
    menus: dict[str, list[str]] = {}
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        name = path.name.lower()
        if not any(h in name for h in MENU_HINTS):
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for line in text.splitlines():
            if "read_dtcs" in line.lower() and "read_dtcs" not in menus:
                menus["read_dtcs"] = ["Diagnose", "Read DTCs"]
    return menus


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("cnlaunch_root", type=Path, help="Path to cnlaunch folder from zip export")
    ap.add_argument(
        "--out",
        type=Path,
        default=Path("app/src/main/assets/cnlaunch"),
        help="Output directory for JSON assets",
    )
    args = ap.parse_args()
    root = args.cnlaunch_root
    if not root.is_dir():
        raise SystemExit(f"Not a directory: {root}")

    dtc = crawl_dtcs(root)
    menus = crawl_menus(root)
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "dtc-descriptions.json").write_text(json.dumps(dtc, indent=2), encoding="utf-8")
    (args.out / "menu-trees.json").write_text(json.dumps(menus, indent=2), encoding="utf-8")
    print(f"Wrote {args.out}/dtc-descriptions.json ({sum(len(v) for v in dtc.values())} codes)")
    print(f"Wrote {args.out}/menu-trees.json ({len(menus)} capabilities)")


if __name__ == "__main__":
    main()
