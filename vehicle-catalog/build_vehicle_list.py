#!/usr/bin/env python3
"""Buduje listę marek i modeli aut osobowych z otwartego rejestru RDW (Holandia, CC0).

Użycie:
    python3 build_vehicle_list.py                 # pobiera dane z RDW i zapisuje vehicle-list.json
    python3 build_vehicle_list.py --raw plik.json # używa wcześniej pobranej agregacji (offline)

Lista służy WYŁĄCZNIE do wyboru auta. Nie zawiera żadnych danych serwisowych i nie
identyfikuje konfiguracji (silnik, skrzynia, rynek) potrzebnej do przypisania harmonogramu.
Tylko biblioteka standardowa.
"""
import argparse
import collections
import datetime
import json
import pathlib
import re
import sys
import time
import urllib.parse
import urllib.request

HERE = pathlib.Path(__file__).parent
DATASET = "https://opendata.rdw.nl/resource/m9d7-ebf2.json"
WHERE = "voertuigsoort='Personenauto' AND inrichting!='kampeerwagen'"
MIN_BRAND_VEHICLES = 100
MAX_MODEL_THRESHOLD = 250   # duże marki: model musi mieć co najmniej tyle aut w rejestrze
MIN_MODEL_THRESHOLD = 25    # małe marki: próg to 2% aut marki, ale nie mniej niż tyle
PAGE = 50000


def fetch():
    rows, offset = [], 0
    while True:
        query = {
            "$select": "merk,handelsbenaming,count(*) as n,"
                       "min(datum_eerste_toelating) as first,max(datum_eerste_toelating) as last",
            "$where": WHERE,
            "$group": "merk,handelsbenaming",
            "$order": "merk,handelsbenaming",
            "$limit": PAGE,
            "$offset": offset,
        }
        url = DATASET + "?" + urllib.parse.urlencode(query)
        for attempt in range(4):
            try:
                with urllib.request.urlopen(url, timeout=300) as response:
                    page = json.load(response)
                break
            except OSError as error:
                print(f"retry {attempt}: {error}", file=sys.stderr)
                time.sleep(2 ** (attempt + 1))
        else:
            sys.exit("RDW download failed")
        rows += page
        if len(page) < PAGE:
            return rows
        offset += PAGE


def squash(text):
    return re.sub(r"\s+", " ", (text or "").upper()).strip()


POWER_SUFFIX = re.compile(r"\s+\d+\s?KW\b.*$")
LEADING_TYPE_CODE = re.compile(r"^\d{5,6}\s+")
EMISSION_CODE = re.compile(r"\s+(U9|E2|K6)$")


def clean_model(raw, brand_names):
    name = squash(raw)
    for brand in brand_names:  # "CITROEN C1" -> "C1"
        if name.startswith(brand + " "):
            name = name[len(brand) + 1:]
            break
    if name in brand_names:  # sama nazwa marki nie jest modelem
        return None
    name = LEADING_TYPE_CODE.sub("", name)  # "171010 GOLF" -> "GOLF"
    name = POWER_SUFFIX.sub("", name)
    name = EMISSION_CODE.sub("", name).strip(" -./,")
    if not name or name == "NIET GEREGISTREERD" or len(name) > 30:
        return None
    if re.fullmatch(r"[\d\s./-]{5,}", name):  # fabryczne kody typu, np. "113021"
        return None
    return name


def display_brand(brand, display):
    if brand in display:
        return display[brand]
    return re.sub(r"[A-Za-z]+", lambda m: m.group(0).capitalize(), brand)


def year(value, pick):
    years = [int(v[:4]) for v in value if v and v[:4].isdigit()]
    return pick(years) if years else None


def build(rows):
    rules = json.loads((HERE / "brand_rules.json").read_text(encoding="utf-8"))
    aliases, dropped, display = rules["aliases"], set(rules["dropped"]), rules["display"]

    # klucz modelu = same litery i cyfry, żeby "T-ROC", "T ROC" i "TROC" były jednym modelem
    brands = collections.defaultdict(lambda: collections.defaultdict(
        lambda: {"n": 0, "spellings": collections.Counter(), "first": [], "last": []}))
    total = 0
    for row in rows:
        n = int(row["n"])
        total += n
        raw_brand = squash(row.get("merk"))
        brand = aliases.get(raw_brand, raw_brand)
        if not brand or brand in dropped:
            continue
        model = clean_model(row.get("handelsbenaming"), sorted({raw_brand, brand}, key=len, reverse=True))
        if model is None:
            brands[brand]  # marka liczy się nawet bez czytelnej nazwy modelu
            brands[brand][""]["n"] += n
            continue
        key = re.sub(r"[^A-Z0-9]", "", model) or model
        entry = brands[brand][key]
        entry["n"] += n
        entry["spellings"][model] += n
        entry["first"].append(row.get("first"))
        entry["last"].append(row.get("last"))

    makes, listed = [], 0
    for brand, models in brands.items():
        brand_total = sum(m["n"] for m in models.values())
        if brand_total < MIN_BRAND_VEHICLES:
            continue
        threshold = min(MAX_MODEL_THRESHOLD, max(MIN_MODEL_THRESHOLD, brand_total // 50))
        kept = []
        for key, m in models.items():
            if key == "" or m["n"] < threshold:
                continue
            listed += m["n"]
            kept.append({
                "name": m["spellings"].most_common(1)[0][0],
                "firstYear": year(m["first"], min),
                "lastYear": year(m["last"], max),
                "vehicles": m["n"],
            })
        kept.sort(key=lambda m: (-m["vehicles"], m["name"]))
        makes.append({"name": display_brand(brand, display), "vehicles": brand_total, "models": kept})
    makes.sort(key=lambda m: m["name"].casefold())

    return {
        "meta": {
            "source": "RDW Open Data, Gekentekende voertuigen (m9d7-ebf2)",
            "sourceUrl": "https://opendata.rdw.nl/Voertuigen/Open-Data-RDW-Gekentekende_voertuigen/m9d7-ebf2",
            "licence": "CC0 1.0",
            "retrieved": datetime.date.today().isoformat(),
            "filter": WHERE,
            "minBrandVehicles": MIN_BRAND_VEHICLES,
            "modelThreshold": f"min({MAX_MODEL_THRESHOLD}, max({MIN_MODEL_THRESHOLD}, 2% of make))",
            "sourceVehicles": total,
            "listedVehicles": listed,
            "makes": len(makes),
            "models": sum(len(m["models"]) for m in makes),
            "note": "Selection list only. Years are first-registration years seen in the Dutch register. "
                    "No service data; does not identify engine, gearbox or market.",
        },
        "makes": makes,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", help="wcześniej pobrana agregacja RDW (JSON)")
    parser.add_argument("--out", default=str(HERE / "vehicle-list.json"))
    args = parser.parse_args()
    rows = json.loads(pathlib.Path(args.raw).read_text()) if args.raw else fetch()
    result = build(rows)
    pathlib.Path(args.out).write_text(
        json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(json.dumps(result["meta"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
