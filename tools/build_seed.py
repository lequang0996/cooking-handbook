#!/usr/bin/env python3
"""Regenerates app/src/main/assets/dishes_seed.json from Anduin2017/HowToCook.
The seed only lets the app work offline on first start; the app itself syncs with GitHub afterwards.
Usage: python3 tools/build_seed.py
"""
import json, os, subprocess, tempfile

REPO = "https://github.com/Anduin2017/HowToCook.git"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "dishes_seed.json")

with tempfile.TemporaryDirectory() as tmp:
    subprocess.check_call(["git", "clone", "--filter=blob:none", "--no-checkout", REPO, tmp])
    subprocess.check_call(["git", "-C", tmp, "checkout", "master", "--", "dishes"])
    g = ["git", "-C", tmp, "-c", "core.quotepath=off"]
    tree = subprocess.check_output(g + ["ls-tree", "-r", "HEAD", "dishes/"], text=True)
    log = subprocess.check_output(g + ["log", "--name-only", "--pretty=format:@@%ct", "--", "dishes/"], text=True)
    dates, cur = {}, None
    for line in log.splitlines():
        if line.startswith("@@"): cur = int(line[2:]) * 1000
        elif line.strip() and line not in dates: dates[line] = cur
    seed = []
    for line in tree.splitlines():
        meta, path = line.split("\t", 1)
        if path.endswith(".md") and not path.startswith("dishes/template/"):
            with open(os.path.join(tmp, path), encoding="utf8") as f:
                seed.append({"path": path, "sha": meta.split()[2], "updated": dates.get(path, max(dates.values())), "md": f.read()})
    seed.sort(key=lambda x: x["path"])
    with open(OUT, "w", encoding="utf8") as f:
        json.dump(seed, f, ensure_ascii=False, separators=(",", ":"))
    print(len(seed), "dishes written")
