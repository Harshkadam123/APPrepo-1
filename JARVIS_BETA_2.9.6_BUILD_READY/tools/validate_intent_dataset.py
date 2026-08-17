"""Validate JARVIS intent CSV without training a model."""
import csv
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "training" / "intent_training.csv"

with path.open(encoding="utf-8", newline="") as f:
    rows = list(csv.DictReader(f))

required = {"text", "intent"}
if not required.issubset(rows[0].keys() if rows else set()):
    raise SystemExit("Dataset must contain text,intent columns.")

bad = [i + 2 for i, r in enumerate(rows) if not r["text"].strip() or not r["intent"].strip()]
dupes = Counter(r["text"].strip().lower() for r in rows)

print(f"Examples: {len(rows)}")
print("Intent distribution:")
for label, count in Counter(r["intent"].strip() for r in rows).most_common():
    print(f"  {label}: {count}")
print(f"Duplicate texts: {sum(n - 1 for n in dupes.values() if n > 1)}")
if bad:
    print(f"Invalid rows: {bad}")
    raise SystemExit(1)
print("Dataset validation passed.")
