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


def row(merk, model, n, first="20200101", last="20240101"):
    return {"merk": merk, "handelsbenaming": model, "n": str(n), "first": first, "last": last}


def eea(make, model, country, year):
    return {"Mk": make, "Cn": model, "MS": country, "Year": year}


class BuildTest(unittest.TestCase):
    def test_aliases_spelling_merge_and_thresholds(self):
        result = build([
            row("VOLKSWAGEN", "T-ROC", 300, first="20170505"),
            row("VW", "T ROC", 20, last="20260101"),
            row("VOLKSWAGEN", "RARE TRIM", 10),
            row("VOLKSWAGEN", "113021", 5000),
            row("NICHEBRAND", "ONE", 499),
            row("JAGUAR LAND ROVER", "X", 5000),
        ])
        self.assertEqual(["Volkswagen"], [m["name"] for m in result["makes"]])
        make = result["makes"][0]
        self.assertEqual(5330, make["vehicles"])
        self.assertEqual(
            [{"name": "T-ROC", "firstYear": 2017, "lastYear": 2026, "vehicles": 320}], make["models"])
        self.assertEqual("CC0 1.0", result["meta"]["licence"])
        self.assertIsNone(result["meta"]["verification"])

    def test_small_make_uses_relative_model_threshold(self):
        result = build([row("GENESIS", "GV60", 520), row("GENESIS", "G70", 30), row("GENESIS", "ODD", 5)])
        self.assertEqual(["GV60", "G70"], [m["name"] for m in result["makes"][0]["models"]])

    def test_verification_flags_and_norway_additions(self):
        result = build(
            [row("VOLKSWAGEN", "GOLF", 900), row("VOLKSWAGEN", "LUPO", 400), row("VOLKSWAGEN", "UP!", 300)],
            [
                eea("VW", "Golf Variant", "NO", 2021),      # zaczyna się od "GOLF" -> ten sam model
                eea("VOLKSWAGEN", "GOLF", "DE", 2015),
                eea("VOLKSWAGEN", "GOLF", "SE", 2016),
                eea("VOLKSWAGEN", "UP", "FR", 2012),        # inny zapis tego samego modelu
                eea("VOLKSWAGEN", "ID.BUZZ", "NO", 2021),   # spoza RDW, dwa lata w Norwegii -> dopisany
                eea("VOLKSWAGEN", "ID.BUZZ", "NO", 2022),
                eea("VOLKSWAGEN", "ONEOFF", "NO", 2022),    # tylko jeden rok -> pomijany
                eea("VOLKSWAGEN", "GOLF", "M1", 2015),      # błędny kod kraju -> pomijany
                eea("UNKNOWNBRAND", "X", "NO", 2021),
            ],
        )
        models = {m["name"]: m for m in result["makes"][0]["models"]}
        self.assertEqual({"GOLF", "LUPO", "UP!", "ID.BUZZ"}, set(models))
        self.assertTrue(models["GOLF"]["norway"])
        self.assertEqual(2, models["GOLF"]["europeCountries"])
        self.assertNotIn("norway", models["LUPO"])
        self.assertNotIn("europeCountries", models["LUPO"])
        self.assertEqual(1, models["UP!"]["europeCountries"])
        self.assertEqual("EEA", models["ID.BUZZ"]["addedFrom"])
        self.assertEqual((2021, 2022), (models["ID.BUZZ"]["firstYear"], models["ID.BUZZ"]["lastYear"]))
        self.assertEqual(
            {"confirmedNorway": 1, "confirmedEurope": 2, "addedFromNorway": 1},
            {k: result["meta"]["verification"][k] for k in ("confirmedNorway", "confirmedEurope", "addedFromNorway")})


if __name__ == "__main__":
    unittest.main()
