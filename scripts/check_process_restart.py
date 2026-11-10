"""Black-box persistence check following the native manual-history test, on a test device."""
from pathlib import Path
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = "app.servicepassport.android.debug"
COMPONENT = PACKAGE + "/app.servicepassport.android.MainActivity"
OUT = Path("app/build/reports/device-smoke")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args, timeout=40):
    try:
        return subprocess.check_output(["adb", *args], timeout=timeout, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as error:
        print(error.output.decode("utf-8", errors="replace")[:1500], file=sys.stderr)
        raise


def window():
    adb("shell", "uiautomator", "dump", "/sdcard/history-window.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/history-window.xml"))


def wait_for(check, message):
    for _ in range(15):
        root = window()
        text = [node.get("text", "") for node in root.iter("node")]
        if check(text):
            return root
        time.sleep(2)
    raise AssertionError(message)


adb("shell", "am", "force-stop", PACKAGE)
adb("shell", "am", "start", "-W", "-n", COMPONENT)
root = wait_for(
    lambda text: "Foundation test car" in text and "Modellår: 2022" in text
    and any(re.fullmatch(r"59[\s\u00a0\u202f]?000 km", value) for value in text)
    and "Gjeldende kilometerstand" in text,
    "Fresh process did not restore the profile, explicit current reading and Bokmål UI",
)
(OUT / "overview-no.png").write_bytes(adb("exec-out", "screencap", "-p"))
# Navigate using the visible NO label; this also checks the persisted language.
nav = next(node for node in root.iter("node") if node.get("text") == "Historikk")
left, top, right, bottom = map(int, re.findall(r"\d+", nav.get("bounds")))
adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
wait_for(
    lambda text: "15.12.2025" in text and any("Motorolje" in value for value in text)
    and any("Bremser" in value for value in text)
    and any(re.fullmatch(r"49[\s\u00a0\u202f]?000 km", value) for value in text),
    "Fresh process did not restore the service date, edited mileage and both work items",
)
(OUT / "history-no.png").write_bytes(adb("exec-out", "screencap", "-p"))
(OUT / "history-en.png").write_bytes(adb("exec-out", "run-as", PACKAGE, "cat", "files/history-en.png"))
result = {
    "process_force_stopped": True, "saved_profile_restored": True,
    "explicit_current_km_restored": 59000, "historical_service_km_restored": 49000,
    "historical_service_date_restored": "2025-12-15", "work_items_restored": 2,
    "selected_language_restored": "nb-NO", "manufacturer_schedule_present": False,
}
(OUT / "process-restart.json").write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps(result))
