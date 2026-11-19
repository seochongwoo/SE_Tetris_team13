#!/usr/bin/env python3
"""Summarize debug P2P input/paint samples; this does not certify LAN performance."""
import argparse
import csv
import math
from collections import defaultdict
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", type=Path, nargs="+")
    args = parser.parse_args()
    groups = defaultdict(list)
    missing = defaultdict(int)
    seen = set()
    for path in args.logs:
        for row in csv.reader(path.read_text(encoding="utf-8").splitlines()):
            if not row or row[0] != "P2P_LATENCY":
                continue
            if len(row) != 8:
                parser.error(f"Malformed latency record in {path}: {row}")
            _, mode, role, match, sample, command, target, nanos = row
            identity = (mode, role, match, sample, target)
            if identity in seen:
                parser.error("Duplicate sample: use fresh data directories for each measurement run")
            seen.add(identity)
            action = "MOVE" if command in ("MOVE_LEFT", "MOVE_RIGHT") else command
            key = (mode, role, action, target.removesuffix("_MISSING").replace("REMOTE_ACK", "REMOTE"))
            if target.endswith("_MISSING"):
                missing[key] += 1
            else:
                groups[key].append(int(nanos) / 1_000_000)
    print("mode,role,action,target,count,missing,max_ms,p95_ms,mean_ms,over_200ms")
    for key in sorted(groups.keys() | missing.keys()):
        values = sorted(groups[key])
        if values:
            stats = (len(values), missing[key], f"{max(values):.3f}",
                     f"{values[math.ceil(len(values) * .95) - 1]:.3f}",
                     f"{sum(values) / len(values):.3f}", sum(value > 200 for value in values))
        else:
            stats = (0, missing[key], "NA", "NA", "NA", 0)
        print(",".join(map(str, (*key, *stats))))
    print("No automatic PASS: verify both directions, 50 samples/action, LAN metadata and camera error.")


if __name__ == "__main__":
    main()
