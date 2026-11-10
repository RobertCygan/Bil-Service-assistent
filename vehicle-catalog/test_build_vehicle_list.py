import unittest

from build_vehicle_list import build, clean_model


class CleanModelTest(unittest.TestCase):
    def test_cleaning(self):
        cases = {
            "CITROEN C1": "C1",
            "ID.3 PRO 150 KW": "ID.3 PRO",
            "ID.4 PRO 210KW": "ID.4 PRO",
            "171010 GOLF": "GOLF",
            "MX-5 U9": "MX-5",
            "  golf   plus ": "GOLF PLUS",
            "208": "208",
            "MODEL 3": "MODEL 3",
        }
        for raw, expected in cases.items():
            self.assertEqual(expected, clean_model(raw, ["CITROEN"]), raw)

    def test_rejected(self):
        for raw in ["113021", "", None, "Niet geregistreerd", "CITROEN", "X" * 31]:
            self.assertIsNone(clean_model(raw, ["CITROEN"]), raw)


class BuildTest(unittest.TestCase):
    def row(self, merk, model, n, first="20200101", last="20240101"):
        return {"merk": merk, "handelsbenaming": model, "n": str(n), "first": first, "last": last}

    def test_aliases_spelling_merge_and_thresholds(self):
        result = build([
            self.row("VOLKSWAGEN", "T-ROC", 300, first="20170505"),
            self.row("VW", "T ROC", 20, last="20260101"),
            self.row("VOLKSWAGEN", "RARE TRIM", 10),
            self.row("VOLKSWAGEN", "113021", 5000),
            self.row("TINYBRAND", "ONE", 99),
            self.row("JAGUAR LAND ROVER", "X", 5000),
        ])
        self.assertEqual(["Volkswagen"], [m["name"] for m in result["makes"]])
        make = result["makes"][0]
        self.assertEqual(5330, make["vehicles"])
        self.assertEqual(
            [{"name": "T-ROC", "firstYear": 2017, "lastYear": 2026, "vehicles": 320}], make["models"])
        self.assertEqual("CC0 1.0", result["meta"]["licence"])

    def test_small_make_keeps_its_models(self):
        result = build([self.row("GENESIS", "GV60", 120), self.row("GENESIS", "G70", 30), self.row("GENESIS", "ODD", 5)])
        self.assertEqual(["GV60", "G70"], [m["name"] for m in result["makes"][0]["models"]])


if __name__ == "__main__":
    unittest.main()
