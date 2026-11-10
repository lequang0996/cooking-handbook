#!/usr/bin/env python3
"""Pre-translates the bundled recipes (assets/dishes_seed.json) with Gemini and writes
app/src/main/assets/translations/<lang>.json - the files the app reads as its built-in translations.

Each entry stores the git blob sha of the Chinese source, so a recipe that later changes upstream is
re-translated by the app itself (Gemini key in Settings) instead of showing a stale translation.

Usage:
  GEMINI_API_KEYS=$'key1\nkey2' python3 tools/translate_seed.py            # all 7 languages, resumable
  python3 tools/translate_seed.py --dry-run                                 # only check the parser, no API calls
  GEMINI_API_KEYS=... python3 tools/translate_seed.py --langs en,vi --limit 5
"""
import argparse, json, os, re, sys, threading, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
MODEL = "gemini-3.5-flash-lite"
URL = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent"
LANGS = {"en": "English", "es": "Spanish", "pt": "Brazilian Portuguese", "ru": "Russian",
         "ja": "Japanese", "ko": "Korean", "vi": "Vietnamese"}

# ---------------------------------------------------------------- port of DishParser.kt
IMG = re.compile(r"!\[[^\]]*]\([^)]*\)")
LINK = re.compile(r"\[([^\]]*)]\([^)]*\)")
HTML = re.compile(r"<[^>]+>")
STEP = re.compile(r"^\s*\d+\s*[.、．]\s*(.*)$")


def clean(s):
    s = IMG.sub("", s)
    s = LINK.sub(r"\1", s)
    s = HTML.sub("", s)
    return s.replace("**", "").replace("__", "").replace("`", "").strip()


def bullet(line):
    t = line.strip()
    return clean(t[2:]) if t[:2] in ("- ", "* ", "+ ") else None


def parse(md):
    lines = md.replace("\r", "").split("\n")
    title_line = next((l for l in lines if l.startswith("# ")), None)
    if title_line is None:
        return None
    title = clean(title_line[2:])
    if title.endswith("的做法"):
        title = title[:-3]
    title = title.strip()
    if not title:
        return None
    intro, ing, qty, notes, steps = [], [], [], [], []
    section = "NONE"
    started = False
    for line in lines:
        if not started:
            started = line.startswith("# ")
            continue
        if line.startswith("## "):
            name = line[3:]
            section = ("ING" if "原料" in name or "工具" in name else
                       "CALC" if "计算" in name else "STEPS" if "操作" in name else "OTHER")
            continue
        if line.startswith("#"):
            continue
        t = line.strip()
        if not t:
            continue
        if section == "NONE":
            if t.startswith("预估烹饪难度") or t.startswith("预估卡路里"):
                continue
            c = clean(t)
            if c:
                intro.append(c)
        elif section == "ING":
            b = bullet(line)
            if b:
                ing.append(b)
        elif section == "CALC":
            b = bullet(line)
            if b is not None:
                if b:
                    qty.append(b)
            else:
                notes.append(clean(t))
        elif section == "STEPS":
            m = STEP.match(line)
            if m:
                steps.append(clean(m.group(1)))
            elif steps:
                b = bullet(line)
                extra = ("• " + b) if b is not None else clean(t)
                if extra and extra != "•":
                    steps[-1] += "\n" + extra
    if not steps and not ing:
        return None
    return {"title": title, "intro": " ".join(intro), "ingredients": ing,
            "servingNote": " ".join(notes), "quantities": qty, "steps": steps}


# ---------------------------------------------------------------- Gemini
SYSTEM = """You are a professional culinary translator and recipe editor. Translate the recipe in the user's JSON from Simplified Chinese into {lang}.

Return ONLY one JSON object with exactly the same keys as the input (title, intro, ingredients, servingNote, quantities, steps). Arrays must keep exactly the same number of items in the same order; strings that are empty stay empty.

Rules:
- Translate every string completely and naturally, the way a good cookbook in the target language would say it. No Chinese characters may remain, except where a dish or ingredient is conventionally kept in Chinese (then add the translation).
- title: the natural, well-known name of the dish in the target language (not a word-by-word translation when an established name exists).
- steps: clear imperative cooking instructions. Keep each step as one string; keep any line breaks and the "• " bullet markers inside a step.
- Keep every number, quantity, time, temperature and unit exactly right. Use the units common in the target language (g, kg, ml, tbsp, tsp, min); do not convert between measurement systems.
- Use the correct culinary terms for ingredients, tools and techniques. Keep brand names as they are.
- Do not add, omit, explain or comment on anything."""


class Keys:
    def __init__(self, keys):
        self.keys, self.i, self.lock, self.parked = keys, 0, threading.Lock(), {}

    def pick(self):
        while True:
            with self.lock:
                now = time.time()
                for _ in self.keys:
                    k = self.keys[self.i % len(self.keys)]
                    self.i += 1
                    if self.parked.get(k, 0) <= now:
                        return k
                wait = min(self.parked.values()) - now
            if wait > 120:
                raise SystemExit("All keys are rate limited for a long time; run again later (progress is saved).")
            time.sleep(max(wait, 0.5))

    def park(self, k, sec):
        with self.lock:
            self.parked[k] = time.time() + sec


def call(keys, system, user):
    last = None
    for _ in range(8):
        k = keys.pick()
        body = json.dumps({
            "systemInstruction": {"parts": [{"text": system}]},
            "contents": [{"role": "user", "parts": [{"text": user}]}],
            "generationConfig": {"temperature": 0.2, "responseMimeType": "application/json"},
        }).encode()
        req = urllib.request.Request(URL, body, {"Content-Type": "application/json", "x-goog-api-key": k})
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                data = json.load(r)
            return "".join(p.get("text", "") for p in data["candidates"][0]["content"]["parts"])
        except urllib.error.HTTPError as e:
            last = e
            if e.code == 429:
                keys.park(k, 60)
            elif e.code >= 500:
                keys.park(k, 5)
            elif e.code in (401, 403) or e.code == 400:
                keys.park(k, 600)
            else:
                raise
        except Exception as e:  # network
            last = e
            time.sleep(2)
    raise RuntimeError(f"Gemini failed: {last}")


def check(out, src):
    for name in ("ingredients", "quantities", "steps"):
        assert isinstance(out[name], list) and len(out[name]) == len(src[name]), f"{name} length"
    assert out["title"].strip(), "empty title"
    assert all(s.strip() for s in out["steps"]), "empty step"
    assert not src["intro"] or out.get("intro", "").strip(), "empty intro"
    return {"title": out["title"].strip(), "intro": out.get("intro", "").strip(),
            "ingredients": [s.strip() for s in out["ingredients"]],
            "servingNote": out.get("servingNote", "").strip(),
            "quantities": [s.strip() for s in out["quantities"]],
            "steps": [s.strip() for s in out["steps"]]}


def translate(keys, src, lang):
    for attempt in range(2):
        try:
            txt = call(keys, SYSTEM.format(lang=LANGS[lang]), json.dumps(src, ensure_ascii=False))
            return check(json.loads(txt), src)
        except (AssertionError, KeyError, ValueError) as e:
            err = e
    raise RuntimeError(f"bad answer: {err}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--langs", default=",".join(LANGS))
    ap.add_argument("--limit", type=int, default=0, help="only the first N dishes (testing)")
    ap.add_argument("--workers", type=int, default=0)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    seed = json.load(open(os.path.join(ASSETS, "dishes_seed.json"), encoding="utf8"))
    titles = json.load(open(os.path.join(ASSETS, "titles.json"), encoding="utf8"))
    dishes = []
    for d in seed:
        p = parse(d["md"])
        if p:
            dishes.append((d["path"], d["sha"], p))
    if a.limit:
        dishes = dishes[:a.limit]
    print(f"{len(dishes)} dishes parsed from {len(seed)} files")
    if a.dry_run:
        n = sum(len(p["steps"]) for _, _, p in dishes)
        chars = sum(len(json.dumps(p, ensure_ascii=False)) for _, _, p in dishes)
        print(f"{n} steps, {chars} characters of source JSON per language, {len(titles)} curated titles")
        missing = [p["title"] for _, _, p in dishes if p["title"] not in titles]
        print("titles missing from titles.json:", missing or "none")
        return

    raw = [k.strip() for k in re.split(r"[\n,; ]+", os.environ.get("GEMINI_API_KEYS", "")) if len(k.strip()) >= 8]
    if not raw:
        sys.exit("Set GEMINI_API_KEYS (one key per line).")
    keys = Keys(raw)
    out_dir = os.path.join(ASSETS, "translations")
    os.makedirs(out_dir, exist_ok=True)
    workers = a.workers or min(len(raw) * 3, 12)

    for lang in [l for l in a.langs.split(",") if l in LANGS]:
        path = os.path.join(out_dir, f"{lang}.json")
        done = json.load(open(path, encoding="utf8")) if os.path.exists(path) else {}
        todo = [(p, s, src) for p, s, src in dishes if done.get(p, {}).get("sha") != s]
        print(f"[{lang}] {len(done)} done, {len(todo)} to translate")
        lock, failed = threading.Lock(), []

        def save():
            tmp = path + ".tmp"
            with open(tmp, "w", encoding="utf8") as f:
                json.dump(done, f, ensure_ascii=False, separators=(",", ":"))
            os.replace(tmp, path)

        def work(item):
            p, s, src = item
            t = translate(keys, src, lang)
            cur = titles.get(src["title"], {}).get(lang)
            if cur:
                t["title"] = cur  # keep the hand-written dish names
            t["sha"] = s
            return p, t

        with ThreadPoolExecutor(workers) as ex:
            futs = {ex.submit(work, it): it for it in todo}
            for n, f in enumerate(as_completed(futs), 1):
                try:
                    p, t = f.result()
                    with lock:
                        done[p] = t
                        if n % 10 == 0:
                            save()
                except SystemExit:
                    save()
                    raise
                except Exception as e:
                    failed.append(futs[f][0])
                    print(f"  failed {futs[f][0]}: {e}")
                if n % 25 == 0:
                    print(f"  [{lang}] {n}/{len(todo)}")
        save()
        print(f"[{lang}] finished, {len(failed)} failed (run again to retry them)")


if __name__ == "__main__":
    main()
