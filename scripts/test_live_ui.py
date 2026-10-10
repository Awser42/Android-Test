#!/usr/bin/env python3
"""Tap the optimized live APK's native UI on a disposable, offline CI emulator.

This clears com.awser.counter's data once. CI=true, a single emulator, qemu=1,
and API 35 are mandatory. Networks are disabled before installing/launching the
owner-ad build. This checks UI behavior; it does not test real ad serving.
Only Python's standard library and adb are required.
"""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import time
from typing import Callable
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.awser.counter"
ACTIVITY = f"{PACKAGE}/.MainActivity"
APK = ROOT / "app/build/outputs/apk/live/app-live.apk"
REPORTS = ROOT / "app/build/reports/live-ui"
COUNTER = re.compile(r"Add one\. Count ([0-9]+)")
BOUNDS = re.compile(r"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]")
ARABIC = re.compile(r"[\u0621-\u064a]")
AD_WORDS = re.compile(r"\b(?:ads?|banner|advertisement|advertising|retry|privacy options)\b", re.I)


class SmokeTest:
    def __init__(self) -> None:
        self.adb = shutil.which("adb")
        self.serial: str | None = None
        self.safe_device = False
        self.dump_number = 0
        self.phase = "guard"
        self.report: dict[str, object] = {
            "package": PACKAGE,
            "apk": str(APK.relative_to(ROOT)),
            "variant": "minified live, offline",
            "result": "running",
            "completed_phases": [],
        }
        REPORTS.mkdir(parents=True, exist_ok=True)
        self.log = (REPORTS / "adb.log").open("w", encoding="utf-8")

    def command(self, *args: str, check: bool = True, timeout: int = 30) -> subprocess.CompletedProcess[str]:
        if not self.adb:
            raise RuntimeError("adb is required on PATH")
        command = [self.adb]
        if self.serial:
            command += ["-s", self.serial]
        command += args
        self.log.write(f"$ {shlex.join(command)}\n")
        self.log.flush()
        result = subprocess.run(command, capture_output=True, text=True, timeout=timeout)
        self.log.write(result.stdout + result.stderr + f"\n[exit {result.returncode}]\n")
        self.log.flush()
        if check and result.returncode:
            raise RuntimeError(f"adb failed: {shlex.join(args)}: {result.stderr or result.stdout}")
        return result

    def guard(self) -> None:
        if os.environ.get("CI", "").lower() != "true":
            raise RuntimeError("Refusing device changes: this smoke test requires CI=true")
        if not APK.is_file():
            raise RuntimeError(f"Build the live APK first: {APK}")
        self.report.update({"tested_apk_sha256": hashlib.sha256(APK.read_bytes()).hexdigest(), "tested_apk_bytes": APK.stat().st_size})
        devices = [line.split() for line in self.command("devices").stdout.splitlines()[1:] if line.strip()]
        if len(devices) != 1 or len(devices[0]) < 2 or devices[0][1] != "device":
            raise RuntimeError("Refusing device changes: exactly one online emulator must be attached")
        self.serial = devices[0][0]
        if not self.serial.startswith("emulator-"):
            raise RuntimeError("Refusing device changes: an emulator serial is required")
        qemu = self.command("shell", "getprop", "ro.kernel.qemu").stdout.strip()
        api = self.command("shell", "getprop", "ro.build.version.sdk").stdout.strip()
        if qemu != "1" or api != "35":
            raise RuntimeError(f"Refusing device changes: require qemu=1/API35, found qemu={qemu!r}/API={api!r}")
        self.report.update({"device": self.serial, "qemu": qemu, "api": 35})
        self.safe_device = True

    def go_offline(self) -> None:
        self.command("shell", "svc", "wifi", "disable")
        self.command("shell", "svc", "data", "disable")
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            wifi = self.command("shell", "settings", "get", "global", "wifi_on").stdout.strip()
            data = self.command("shell", "settings", "get", "global", "mobile_data").stdout.strip()
            if wifi == "0" and data == "0":
                self.report["network_mode"] = "WiFi and mobile data verified disabled before live install and launch"
                return
            time.sleep(0.2)
        raise RuntimeError(f"Cannot safely launch live APK: WiFi/mobile data are not disabled ({wifi!r}/{data!r})")

    def dump_ui(self, label: str) -> ET.Element:
        self.dump_number += 1
        remote = "/sdcard/tasbih-live-ui.xml"
        self.command("shell", "rm", "-f", remote)
        self.command("shell", "uiautomator", "dump", "--compressed", remote, timeout=20)
        xml = self.command("exec-out", "cat", remote).stdout
        (REPORTS / f"{self.dump_number:03d}-{label}.xml").write_text(xml, encoding="utf-8")
        return ET.fromstring(xml)

    def wait_ui(self, label: str, assertion: Callable[[ET.Element], object]) -> object:
        deadline = time.monotonic() + 45
        last_error = "UI assertion has not completed"
        while time.monotonic() < deadline:
            try:
                return assertion(self.dump_ui(label))
            except (RuntimeError, ET.ParseError, subprocess.TimeoutExpired) as error:
                last_error = str(error)
            time.sleep(0.3)
        raise RuntimeError(f"Timed out in {label}: {last_error}")

    @staticmethod
    def nodes(root: ET.Element) -> list[ET.Element]:
        return [node for node in root.iter("node") if node.get("package") == PACKAGE]

    @staticmethod
    def bounds(node: ET.Element) -> tuple[int, int, int, int]:
        match = BOUNDS.fullmatch(node.get("bounds", ""))
        if not match:
            raise RuntimeError("Native control has no valid bounds")
        left, top, right, bottom = map(int, match.groups())
        if right <= left or bottom <= top:
            raise RuntimeError("Native control has empty bounds")
        return left, top, right, bottom

    def tap_node(self, node: ET.Element) -> None:
        if node.get("clickable") != "true" or node.get("enabled") != "true":
            raise RuntimeError("Tap target must be an enabled, clickable native control")
        left, top, right, bottom = self.bounds(node)
        self.command("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    def button(self, root: ET.Element, text: str, resource_id: str | None = None) -> ET.Element:
        matches = [node for node in self.nodes(root) if node.get("class") == "android.widget.Button"
                   and node.get("text", "").casefold() == text.casefold() and node.get("enabled") == "true"
                   and node.get("clickable") == "true" and (resource_id is None or node.get("resource-id") == resource_id)]
        if len(matches) != 1:
            raise RuntimeError(f"Expected one enabled native {text!r} button, found {len(matches)}")
        self.bounds(matches[0])
        return matches[0]

    def tap_button(self, text: str, label: str, resource_id: str | None = None) -> None:
        node = self.wait_ui(label, lambda root: self.button(root, text, resource_id))
        self.tap_node(node)

    def require_text(self, root: ET.Element, text: str, starts_with: bool = False) -> None:
        if not any(node.get("text", "").startswith(text) if starts_with else node.get("text") == text for node in self.nodes(root)):
            raise RuntimeError(f"Missing native text {text!r}")

    def counter_node(self, root: ET.Element, expected: int) -> ET.Element:
        for node in self.nodes(root):
            match = COUNTER.fullmatch(node.get("content-desc", ""))
            if match and int(match.group(1)) == expected:
                if node.get("class") != "android.widget.Button" or node.get("clickable") != "true" or node.get("enabled") != "true":
                    raise RuntimeError("Counter must expose an enabled, clickable native Button")
                self.bounds(node)
                return node
        raise RuntimeError(f"Native counter does not display {expected}")

    def wait_counter(self, expected: int, label: str) -> ET.Element:
        return self.wait_ui(label, lambda root: self.counter_node(root, expected))

    def tap_counts(self, before: int, after: int) -> None:
        for count in range(before, after):
            self.tap_node(self.wait_counter(count, f"before-count-{count + 1}"))
        self.wait_counter(after, f"count-{after}")

    def history_row(self, root: ET.Element, name: str, count: int) -> ET.Element:
        self.require_text(root, "Saved counts")
        description = f"{name}, count {count}. Restore or delete saved count"
        matches = [node for node in self.nodes(root) if node.get("content-desc") == description]
        if len(matches) != 1:
            raise RuntimeError(f"Expected one saved snapshot {description!r}, found {len(matches)}")
        self.bounds(matches[0])
        return matches[0]

    def empty_history(self, root: ET.Element) -> None:
        self.require_text(root, "Saved counts")
        self.require_text(root, "No saved counts yet.", starts_with=True)
        if any("Restore or delete saved count" in node.get("content-desc", "") for node in self.nodes(root)):
            raise RuntimeError("Deleted snapshot still appears in history")

    def pids(self) -> set[str]:
        result = self.command("shell", "pidof", PACKAGE, check=False)
        if result.returncode not in (0, 1) or any(not value.isdigit() for value in result.stdout.split()):
            raise RuntimeError(f"Unexpected pidof output: {result.stdout!r}")
        return set(result.stdout.split())

    def wait_pids(self, running: bool) -> set[str]:
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            pids = self.pids()
            if bool(pids) == running:
                return pids
            time.sleep(0.2)
        raise RuntimeError(f"App process did not become {'running' if running else 'absent'}")

    def launch(self) -> None:
        result = self.command("shell", "am", "start", "-W", "-n", ACTIVITY).stdout
        if "Status: ok" not in result:
            raise RuntimeError(f"MainActivity failed to launch: {result}")

    def restart(self, expected: int, label: str) -> None:
        before = self.wait_pids(True)
        self.command("shell", "am", "force-stop", PACKAGE)
        self.wait_pids(False)
        self.launch()
        after = self.wait_pids(True)
        if before & after:
            raise RuntimeError("Force-stop/relaunch must create a fresh process")
        self.wait_counter(expected, label)
        self.report[f"{label}_pids"] = {"before": sorted(before), "after": sorted(after)}

    def activity(self, name: str) -> None:
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            output = self.command("shell", "dumpsys", "activity", "activities").stdout
            resumed = [line for line in output.splitlines() if "mResumedActivity" in line or "topResumedActivity" in line]
            if any(f"{PACKAGE}/.{name}" in line or f"{PACKAGE}/{PACKAGE}.{name}" in line for line in resumed):
                return
            time.sleep(0.2)
        raise RuntimeError(f"Expected resumed {name}, found {resumed!r}")

    def no_quran_ads(self, root: ET.Element) -> None:
        for node in root.iter("node"):
            resource = node.get("resource-id", "").lower()
            classname = node.get("class", "").lower()
            if "com.google.android.gms.ads" in classname or any(key in resource for key in ("ad_banner", "ad_status", "ad_retry")):
                raise RuntimeError("Quran screen contains an advertising view")
            if node.get("package") == PACKAGE and AD_WORDS.search(node.get("text", "") + " " + node.get("content-desc", "")):
                raise RuntimeError("Quran screen contains ad/status/retry UI")

    def reading(self, root: ET.Element, title: str, total: str) -> None:
        self.no_quran_ads(root)
        self.require_text(root, "Quran")
        self.require_text(root, title)
        self.require_text(root, total)
        choice = self.button(root, title)
        if choice.get("content-desc") != f"{title}, selected":
            raise RuntimeError(f"{title} is not the selected reading")
        if not any(node.get("class") == "android.widget.TextView" and ARABIC.search(node.get("text", "")) for node in self.nodes(root)):
            raise RuntimeError(f"{title} has no accessible native Arabic text")

    def verse_marker(self, title: str, number: int, label: str) -> None:
        # Long Ayatul Kursi can put its verse number below the initial viewport.
        for attempt in range(8):
            root = self.wait_ui(f"{label}-viewport-{attempt}", lambda tree: self.quran_viewport(tree, title))
            if any(node.get("text") == f"Verse {number}" for node in self.nodes(root)):
                return
            scrolls = [node for node in self.nodes(root) if node.get("class") == "android.widget.ScrollView"]
            if len(scrolls) != 1:
                raise RuntimeError("Expected one native Quran reader ScrollView")
            left, top, right, bottom = self.bounds(scrolls[0])
            x = (left + right) // 2
            self.command("shell", "input", "swipe", str(x), str(bottom - (bottom - top) // 5), str(x), str(top + (bottom - top) // 5), "400")
        raise RuntimeError(f"{title} has no visible Verse {number} marker after scrolling")

    def quran_viewport(self, root: ET.Element, title: str) -> ET.Element:
        self.no_quran_ads(root)
        choice = self.button(root, title)
        if choice.get("content-desc") != f"{title}, selected":
            raise RuntimeError(f"{title} is not selected")
        return root

    def screenshot(self, label: str) -> None:
        if not self.adb or not self.serial:
            return
        result = subprocess.run([self.adb, "-s", self.serial, "exec-out", "screencap", "-p"], capture_output=True, timeout=20)
        if result.returncode != 0 or not result.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("Failed to capture a PNG screenshot")
        (REPORTS / f"{label}.png").write_bytes(result.stdout)

    def complete(self, phase: str) -> None:
        self.report["completed_phases"].append(phase)
        print(f"Live UI smoke: {phase} passed", flush=True)

    def run(self) -> None:
        self.guard()
        # Offline first, including before replacing the earlier debug fixture.
        self.go_offline()
        self.command("shell", "am", "force-stop", PACKAGE)
        self.command("install", "-r", "-t", str(APK), timeout=60)
        result = self.command("shell", "pm", "clear", PACKAGE).stdout.strip()
        if result != "Success":
            raise RuntimeError(f"Failed to clear disposable fixture: {result}")
        self.launch()
        self.phase = "counter"
        self.wait_counter(0, "fresh-counter")
        self.tap_counts(0, 3)
        self.complete("native counter taps")

        self.phase = "save"
        self.tap_button("Save count", "open-save")
        def save_dialog(root: ET.Element) -> str:
            self.require_text(root, "Save count")
            inputs = [node for node in self.nodes(root) if node.get("class") == "android.widget.EditText"]
            if len(inputs) != 1 or not inputs[0].get("text", "").strip():
                raise RuntimeError("Save dialog must have one default named native EditText")
            self.button(root, "Save", "android:id/button1")
            return inputs[0].get("text", "").strip()
        name = self.wait_ui("save-dialog", save_dialog)
        self.report["saved_label"] = name
        self.tap_button("Save", "confirm-save", "android:id/button1")
        self.wait_counter(3, "after-save")
        self.tap_button("Saved counts", "open-history")
        self.wait_ui("saved-snapshot", lambda root: self.history_row(root, name, 3))
        self.screenshot("saved-counts")
        self.tap_button("Done", "close-history", "android:id/button1")
        self.restart(3, "saved-process-restart")
        self.tap_button("Saved counts", "reopen-saved-history")
        self.wait_ui("snapshot-retained-after-restart", lambda root: self.history_row(root, name, 3))
        self.tap_button("Done", "close-retained-history", "android:id/button1")
        self.complete("Save and saved snapshot survive a real process restart")

        self.phase = "restore"
        self.tap_counts(3, 5)
        self.tap_button("Saved counts", "open-history-to-restore")
        self.tap_node(self.wait_ui("select-restore-snapshot", lambda root: self.history_row(root, name, 3)))
        self.tap_button("Restore", "restore-snapshot", "android:id/button1")
        self.wait_counter(3, "restored-count")
        self.complete("saved count replaces current count on Restore")

        self.phase = "delete"
        self.tap_button("Saved counts", "open-history-to-delete")
        self.tap_node(self.wait_ui("select-delete-snapshot", lambda root: self.history_row(root, name, 3)))
        self.tap_button("Delete", "request-delete", "android:id/button3")
        self.wait_ui("delete-confirmation", lambda root: self.require_text(root, "Delete saved count?"))
        self.tap_button("Delete", "confirm-delete", "android:id/button1")
        self.wait_ui("empty-history", self.empty_history)
        self.tap_button("Done", "close-empty-history", "android:id/button1")
        self.restart(3, "deleted-process-restart")
        self.tap_button("Saved counts", "reopen-deleted-history")
        self.wait_ui("deletion-retained-after-restart", self.empty_history)
        self.tap_button("Done", "close-deleted-history", "android:id/button1")
        self.wait_counter(3, "count-after-deletion")
        self.complete("Delete removes snapshot persistently and preserves active count")

        self.phase = "quran"
        self.tap_button("Quran", "open-quran")
        self.activity("QuranActivity")
        readings = [("Surah Yaseen", "83 verses", 1, "yaseen"),
                    ("Ayat-e-Karima", "1 verse", 87, "karima"),
                    ("Ayatul Kursi", "1 verse", 255, "kursi")]
        for index, (title, total, verse, label) in enumerate(readings):
            if index:
                self.tap_button(title, f"choose-{label}")
            self.wait_ui(f"reading-{label}", lambda root: self.reading(root, title, total))
            self.screenshot(f"quran-{label}")
            self.verse_marker(title, verse, label)
            self.complete(f"ad-free {title}: {total}, Arabic text and Verse {verse}")
        self.tap_button("Back", "quran-back")
        self.activity("MainActivity")
        self.wait_counter(3, "counter-after-quran")
        self.screenshot("counter-after-quran")
        self.complete("Quran Back preserves active count")
        self.report["result"] = "passed"

    def finish(self, error: Exception | None, duration: float) -> None:
        self.report["duration_seconds"] = round(duration, 2)
        self.report["last_phase"] = self.phase
        if error:
            self.report.update({"result": "failed", "error": str(error)})
            if self.safe_device:
                for label, diagnostic in (("failure_xml", lambda: self.dump_ui("failure")), ("failure_screenshot", lambda: self.screenshot("failure"))):
                    try:
                        diagnostic()
                    except Exception as diagnostic_error:
                        self.report[f"{label}_error"] = str(diagnostic_error)
        (REPORTS / "report.json").write_text(json.dumps(self.report, indent=2) + "\n", encoding="utf-8")
        self.log.close()


def main() -> int:
    started = time.monotonic()
    smoke = SmokeTest()
    error = None
    try:
        smoke.run()
    except Exception as caught:
        error = caught
    finally:
        smoke.finish(error, time.monotonic() - started)
    if error:
        print(f"Live UI smoke failed: {error}", file=sys.stderr)
        return 1
    print(f"Offline live UI passed: counter, Save/Restore/Delete, Quran and Back. Reports: {REPORTS}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
