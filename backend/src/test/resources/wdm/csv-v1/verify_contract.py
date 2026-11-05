"""Offline synthetic fixture checks; does not exercise application behavior.

Run from any working directory with Python 3.10+ (standard library only).
"""

import csv
import io
import json
import unittest
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path


ROOT = Path(__file__).resolve().parent
HEADER = (
    "order_id,order_item_id,customer_code,ordered_at,status,product_id,"
    "sku_snapshot,product_name_snapshot,quantity,list_unit_price,"
    "allocated_discount_amount,line_total,currency_code"
).split(",")
CASES = json.loads((ROOT / "cases.json").read_text(encoding="utf-8"))


def read_rows(filename):
    with (ROOT / filename).open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source, strict=True)
        if reader.fieldnames != HEADER:
            raise AssertionError("Fixture header differs from CSV v1")
        rows = list(reader)
        if any(set(row) != set(HEADER) or None in row.values() for row in rows):
            raise AssertionError("Fixture record width differs from CSV v1")
        return rows


def instant(value):
    result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if result.utcoffset() is None:
        raise AssertionError("Fixture timestamp must have an explicit offset")
    return result.astimezone(timezone.utc)


class CsvContractFixtures(unittest.TestCase):
    def test_canonical_bytes_and_round_trip(self):
        for filename in CASES["csvFiles"]:
            with self.subTest(filename=filename):
                raw = (ROOT / filename).read_bytes()
                self.assertFalse(raw.startswith(b"\xef\xbb\xbf"))
                self.assertTrue(raw.endswith(b"\r\n"))
                self.assertNotRegex(raw.replace(b"\r\n", b""), b"[\r\n]")
                rows = read_rows(filename)
                output = io.StringIO(newline="")
                writer = csv.DictWriter(output, fieldnames=HEADER, lineterminator="\r\n")
                writer.writeheader()
                writer.writerows(rows)
                self.assertEqual(raw, output.getvalue().encode("utf-8"))

    def test_valid_values_and_exact_numbers(self):
        rows = read_rows("valid.csv")
        self.assertEqual(CASES["valid"]["rowCount"], len(rows))
        lengths = {"order_id": 40, "customer_code": 80, "product_id": 40,
                   "sku_snapshot": 80, "product_name_snapshot": 200}
        for row in rows:
            with self.subTest(line=row["order_item_id"]):
                self.assertTrue(all(value and value.strip() for value in row.values()))
                self.assertEqual("COMPLETED", row["status"])
                for field, length in lengths.items():
                    self.assertLessEqual(len(row[field]), length)
                self.assertRegex(row["order_item_id"], r"^[1-9][0-9]*$")
                self.assertLessEqual(int(row["order_item_id"]), 18446744073709551615)
                self.assertRegex(row["quantity"], r"^[1-9][0-9]*$")
                self.assertLessEqual(int(row["quantity"]), 4294967295)
                for field in ("list_unit_price", "allocated_discount_amount", "line_total"):
                    self.assertRegex(row[field], r"^(0|[1-9][0-9]{0,16})\.[0-9]{2}$")
                price, discount, total = map(Decimal, (
                    row["list_unit_price"], row["allocated_discount_amount"], row["line_total"]))
                self.assertEqual(price * int(row["quantity"]) - discount, total)
                self.assertLessEqual(discount, price * int(row["quantity"]))
                self.assertRegex(row["currency_code"], r"^[A-Z]{3}$")
                self.assertRegex(row["ordered_at"],
                                 r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z$")
                instant(row["ordered_at"])
                self.assertIn(row["product_id"], CASES["catalogProductIds"])
        self.assertGreater(max(int(row["order_item_id"]) for row in rows), 2**53 - 1)

    def test_customer_orders_and_deterministic_ties(self):
        rows = read_rows("valid.csv")
        ordered = sorted(rows, key=lambda row: (
            row["customer_code"], instant(row["ordered_at"]),
            row["order_id"], int(row["order_item_id"])))
        self.assertEqual(rows, ordered)
        self.assertEqual(CASES["valid"]["orderedLineIds"],
                         [row["order_item_id"] for row in rows])
        self.assertEqual(CASES["valid"]["orderCount"], len({r["order_id"] for r in rows}))
        self.assertEqual(CASES["valid"]["customerCount"], len({r["customer_code"] for r in rows}))
        per_order = {}
        for row in rows:
            common = tuple(row[field] for field in (
                "customer_code", "ordered_at", "status", "currency_code"))
            self.assertEqual(per_order.setdefault(row["order_id"], common), common)
        tie = CASES["valid"]["sameTimestampOrderIds"]
        self.assertEqual(len(tie), len(set(tie)))
        self.assertEqual(1, len({r["ordered_at"] for r in rows if r["order_id"] in tie}))
        same_product = [r for r in rows if r["order_id"] == "order-a"]
        self.assertEqual(1, len({r["product_id"] for r in same_product}))
        self.assertEqual(2, len({r["order_item_id"] for r in same_product}))

    def test_text_is_lossless_and_empty_export_has_only_header(self):
        rows = read_rows("valid.csv")
        special = next(r for r in rows if r["order_item_id"] == CASES["valid"]["specialLineId"])
        self.assertEqual(CASES["valid"]["specialProductName"], special["product_name_snapshot"])
        self.assertIn("Vi\u1ec7t", special["product_name_snapshot"])
        self.assertEqual(CASES["valid"]["formulaLikeSku"], special["sku_snapshot"])
        self.assertEqual([], read_rows("empty.csv"))
        self.assertEqual((",".join(HEADER) + "\r\n").encode(), (ROOT / "empty.csv").read_bytes())
        raw = (ROOT / "valid.csv").read_bytes()
        # Accepted transport variants decode to the same cells; original bytes stay different.
        for variant in (b"\xef\xbb\xbf" + raw, raw.replace(b"\r\n", b"\n")):
            parsed = list(csv.DictReader(io.StringIO(variant.decode("utf-8-sig"), newline="")))
            expected = rows if variant.startswith(b"\xef\xbb\xbf") else [
                {key: value.replace("\r\n", "\n") for key, value in row.items()} for row in rows]
            self.assertEqual(expected, parsed)
            self.assertNotEqual(raw, variant)

    def test_period_examples_document_order_time_not_completion_time(self):
        period = CASES["period"]
        start, end = instant(period["fromTime"]), instant(period["toTime"])
        self.assertLess(start, end)
        included = sorted(row["orderId"] for row in period["sourceOrders"]
                          if row["status"] == "COMPLETED"
                          and start <= instant(row["orderedAt"]) < end)
        self.assertEqual(period["expectedOrderIds"], included)
        for example in period["offsetExamples"]:
            self.assertEqual(instant(example["input"]), instant(example["utc"]))
        completion_based = sorted(row["orderId"] for row in period["sourceOrders"]
                                  if row["status"] == "COMPLETED"
                                  and row["completedAt"] is not None
                                  and start <= instant(row["completedAt"]) < end)
        self.assertNotEqual(included, completion_based)

    def test_future_business_failure_examples_are_observable(self):
        rows = read_rows("business-invalid.csv")
        for example in CASES["businessFailures"]:
            with self.subTest(example=example["case"]):
                matched = [r for r in rows if r["order_id"] == example["orderId"]]
                self.assertEqual(example["rowCount"], len(matched))
                if example["case"] == "unknown_product":
                    self.assertNotIn(matched[0]["product_id"], CASES["catalogProductIds"])
                elif example["case"] == "conflicting_order":
                    self.assertEqual(2, len({r["customer_code"] for r in matched}))
                elif example["case"] == "duplicate_line_identity":
                    self.assertEqual(matched[0], matched[1])
                else:
                    self.fail("Undeclared future business example")
        self.assertEqual(sum(c["rowCount"] for c in CASES["businessFailures"]), len(rows))


if __name__ == "__main__":
    unittest.main(verbosity=2)
