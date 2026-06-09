#!/usr/bin/env python3
"""
Usage:
journalctl -u autotweaker --user -f -o json -n 200 -p info | grep '"LOGGER_NAME":"io.github.autotweaker' | python3 scripts/journal-pretty
"""
import json
import sys
from datetime import datetime

PRIORITY_MAP = {
    "0": "ERROR",
    "1": "ERROR",
    "2": "ERROR",
    "3": "ERROR",
    "4": "WARN ",
    "5": "INFO ",
    "6": "INFO ",
    "7": "DEBUG",
}

LEVEL_COLORS = {
    "ERROR": "\033[31m",  # red
    "WARN ": "\033[33m",  # yellow
    "INFO ": "\033[36m",  # cyan
    "DEBUG": "\033[32m",  # green
}

RESET = "\033[0m"
DIM = "\033[2m"
MAGENTA = "\033[35m"
BLUE = "\033[34m"
WHITE = "\033[97m"


def format_entry(entry: dict) -> str | None:
    msg = entry.get("MESSAGE", "")
    if not msg:
        return None

    ts_us = entry.get("__REALTIME_TIMESTAMP")
    if ts_us:
        dt = datetime.fromtimestamp(int(ts_us) / 1_000_000)
        ts = dt.strftime("%H:%M:%S.") + f"{dt.microsecond // 1000:03d}"
    else:
        ts = "??:??:??.???"

    thread = entry.get("THREAD_NAME") or ""
    logger = entry.get("LOGGER_NAME") or ""
    priority = entry.get("PRIORITY", "6")
    level = PRIORITY_MAP.get(priority, "INFO ")


    if len(logger) > 36:
        parts = logger.split(".")
        if len(parts) >= 2:
            abbr = list(parts)
            for i in range(len(abbr) - 1):
                if len(".".join(abbr)) <= 36:
                    break
                abbr[i] = abbr[i][0]
            logger = ".".join(abbr)

    lc = LEVEL_COLORS.get(level, "")

    parts = [
        f"{WHITE}{ts}{RESET}",
        f"{MAGENTA}[{thread}]{RESET}" if thread else "[]",
        f"{lc}{level}{RESET}",
        f"{BLUE}{logger}{RESET}" if logger else "",
        f"- {lc}{msg}{RESET}",
    ]

    return " ".join(p for p in parts if p)


def main():
    try:
        for line in sys.stdin:
            line = line.strip()
            if not line:
                continue
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                print(line)
                continue
            result = format_entry(entry)
            if result:
                print(result)
    except (BrokenPipeError, KeyboardInterrupt):
        pass


if __name__ == "__main__":
    main()
