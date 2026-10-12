#!/usr/bin/env python3
"""Compact view of a dual-stack-sweep report: totals, then every FAIL/OPEN with its first data diffs.

Usage: sweep-summary.py report.json [--all]   (--all also lists ACCEPTED differences)
Exit status 1 when any FAIL/OPEN remains.
"""
import json
import sys

report = json.load(open(sys.argv[1]))
show_all = "--all" in sys.argv
print(json.dumps(report["summary"], ensure_ascii=False))
bad = 0
for entry in report["differences"]:
    if entry["disposition"] == "ACCEPTED" and not show_all:
        continue
    bad += entry["disposition"] != "ACCEPTED"
    java, rust = entry["java"], entry["rust"]
    print(f"{entry['disposition']} {entry['category']} {entry['endpoint']} [{entry['who']}/{entry['variant']}] "
          f"J={java.get('status')}/{java.get('code')}/{java.get('message')} R={rust.get('status')}/{rust.get('code')}/{rust.get('message')}")
    for diff in (entry.get("data_diffs") or [])[:4]:
        print("    " + json.dumps(diff, ensure_ascii=False)[:220])
sys.exit(1 if bad else 0)
