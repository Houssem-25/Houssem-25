#!/usr/bin/env python3
"""Converts the MIPH national nomenclature CSV (data/products.csv from
me-yasserzaouag/national-nomenclature-of-pharmaceutical-products, MIT) into assets/nomenclature.csv.

usage: gen_nomenclature.py <path/to/products.csv> [out.csv]
"""
import csv, re, sys

src = sys.argv[1]
out = sys.argv[2] if len(sys.argv) > 2 else "assets/nomenclature.csv"
clean = lambda s: re.sub(r"\s+", " ", (s or "").strip())
seen = {}
rows = []
for r in csv.DictReader(open(src, encoding="utf-8-sig")):
    reg = clean(r["registration_number"])
    brand = clean(r["brand_name"])
    if not reg or not brand:
        continue
    code = "DZ:" + reg
    seen[code] = seen.get(code, 0) + 1
    if seen[code] > 1:
        code += "#%d" % seen[code]
    dosage = clean(r["dosage"])
    pack = clean(r["packaging"])
    name = " ".join(x for x in (brand, dosage, pack) if x)
    lst = clean(r["list"]).upper()
    rx = 1 if lst else 0  # LISTE I / LISTE II / STUPEFIANT are prescription-only
    rows.append([code, name, 0, 0, clean(r["international_common_name"]), dosage,
                 clean(r["form"]), clean(r["laboratories_holding_the_registration_decision"]),
                 "", "", rx, 0, 0])
with open(out, "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f, lineterminator="\n")
    w.writerow(["barcode", "name", "price", "stock", "dci", "dosage", "form", "lab", "expiry",
                "batch", "rx", "tr", "reimb"])
    w.writerows(rows)
print(len(rows), "products ->", out)
