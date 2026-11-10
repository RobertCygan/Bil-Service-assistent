"""Black-box check following FoundationSmokeTest, using only a test emulator/debug APK."""
from pathlib import Path
import json
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


# The connected test leaves the app in Bokmål, while the clean CI device uses English.
adb("shell", "am", "force-stop", PACKAGE)
adb("shell", "am", "start", "-W", "-n", COMPONENT)
found = False
last_text = []
for _ in range(15):
    adb("shell", "uiautomator", "dump", "/sdcard/foundation-window.xml")
    raw = adb("shell", "cat", "/sdcard/foundation-window.xml")
    root = ET.fromstring(raw)
    last_text = [node.get("text", "") for node in root.iter("node")]
    if "Foundation test car" in last_text and "Ingen bekreftet serviceplan" in last_text and "Modellår: 2022" in last_text:
        found = True
        break
    time.sleep(2)

if not found:
    raise AssertionError("After a fresh process start the saved profile and Bokmål UI were not both found")

(OUT / "foundation-no.png").write_bytes(adb("exec-out", "screencap", "-p"))
(OUT / "foundation-en.png").write_bytes(
    adb("exec-out", "run-as", PACKAGE, "cat", "files/foundation-en.png")
)
result = {"process_force_stopped": True, "saved_profile_restored": True, "model_year_restored": True,
          "selected_language_restored": "nb-NO", "manufacturer_schedule_present": False}
(OUT / "process-restart.json").write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps(result))
