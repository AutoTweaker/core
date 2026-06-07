#!/usr/bin/env python3
import json
import sys
from pathlib import Path

import yaml


def main():
    if len(sys.argv) < 3:
        print(f"Usage: {sys.argv[0]} <index.yml> <index.json> <version>", file=sys.stderr)
        sys.exit(1)

    yml_path, json_path, version = sys.argv[1], sys.argv[2], sys.argv[3]

    with open(yml_path) as f:
        data = yaml.safe_load(f)

    assets = json.load(sys.stdin).get("assets", [])

    urls = {}
    for a in assets:
        ct = a["contentType"]
        if ct == "application/x-debian-package":
            urls["deb_url"] = a["url"]
        elif ct == "application/x-tar":
            urls["tar_url"] = a["url"]
        elif ct == "application/zip":
            urls["zip_url"] = a["url"]

    data["core"]["latest"] = {"version": version, **urls}

    with open(json_path, "w") as f:
        json.dump(data, f, indent=2)


if __name__ == "__main__":
    main()
