"""Black-box persistence check after the native history/dashboard test, on a test device."""
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
        text = [node.get("text", "") for node in root.iter("node") if node.get("text")]
        if check(text):
            return root
        time.sleep(2)
    raise AssertionError(message)


def tap(root, label):
    node = next(node for node in root.iter("node") if node.get("text") == label)
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def dashboard_counts(text):
    # Each tile shows the number above its label. Compose may expose them separately or as one merged node.
    for label in ["Forfalt", "Snart frist", "Trenger opplysninger", "Ingen hast"]:
        if not any((value == label and i > 0 and text[i - 1] == "1")
                   or re.fullmatch(r"1\s+" + re.escape(label), value)
                   for i, value in enumerate(text)):
            return False
    return True


adb("shell", "am", "force-stop", PACKAGE)
adb("shell", "am", "start", "-W", "-n", COMPONENT)
root = wait_for(
    lambda text: "Foundation test car" in text and "Modellår: 2022" in text
    and any(re.fullmatch(r"59[\s\u00a0\u202f]?000 km", value) for value in text)
    and "Gjeldende kilometerstand" in text,
    "Fresh process did not restore the profile, explicit current reading and Bokmål UI",
)
(OUT / "overview-no.png").write_bytes(adb("exec-out", "screencap", "-p"))
tap(root, "Se alle")  # the link next to the "Serviceoversikt" heading
wait_for(
    lambda text: "Ingen bekreftet serviceplan" in text and dashboard_counts(text),
    "Fresh process did not restore all four driver intervals and their separate categories",
)
(OUT / "dashboard-no-restarted.png").write_bytes(adb("exec-out", "screencap", "-p"))
for name in ["dashboard-no.png", "dashboard-en.png", "forecast-en.png", "history-en.png", "history-no.png"]:
    (OUT / name).write_bytes(adb("exec-out", "run-as", PACKAGE, "cat", "files/" + name))
result = {
    "process_force_stopped": True, "saved_profile_restored": True,
    "explicit_current_km_restored": 59000, "user_intervals_restored": 4,
    "dashboard_counts": {"overdue": 1, "due_soon": 1, "needs_data": 1, "no_urgency": 1},
    "selected_language_restored": "nb-NO", "manufacturer_schedule_present": False,
}
(OUT / "process-restart.json").write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps(result))
