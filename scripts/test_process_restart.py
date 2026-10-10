#!/usr/bin/env python3
"""Verify on-disk counts across a real process restart on a throwaway CI emulator.

This clears com.awser.counter's data. It refuses physical devices, multiple devices,
non-API-35 devices, and runs without CI=true. No third-party Python packages needed.
"""

from __future__ import annotations

import json
import hashlib
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.awser.counter"
ACTIVITY = f"{PACKAGE}/.MainActivity"
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
REPORTS = ROOT / "app/build/reports/process-restart"
PREFERENCES = "shared_prefs/a_counter.xml"
EXPECTED_COUNT = 7
COUNTER_DESCRIPTION = re.compile(r"Add one\. Count ([0-9]+)")
BOUNDS = re.compile(r"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]")


class SmokeTest:
    def __init__(self) -> None:
        self.adb = shutil.which("adb")
        self.serial: str | None = None
        self.safe_device = False
        self.dump_number = 0
        self.report: dict[str, object] = {
            "package": PACKAGE,
            "apk": str(APK.relative_to(ROOT)),
            "expected_count": EXPECTED_COUNT,
            "result": "running",
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
        self.log.write(result.stdout + result.stderr)
        self.log.write(f"\n[exit {result.returncode}]\n")
        self.log.flush()
        if check and result.returncode:
            raise RuntimeError(f"adb command failed: {shlex.join(args)}: {result.stderr or result.stdout}")
        return result

    def guard(self) -> None:
        if os.environ.get("CI", "").lower() != "true":
            raise RuntimeError("Refusing to clear app data: this test requires CI=true")
        if not APK.is_file():
            raise RuntimeError(f"Build the debug APK first: {APK}")
        self.report.update({"tested_apk_sha256": hashlib.sha256(APK.read_bytes()).hexdigest(), "tested_apk_bytes": APK.stat().st_size})
        output = self.command("devices").stdout
        devices = [line.split() for line in output.splitlines()[1:] if line.strip()]
        if len(devices) != 1 or len(devices[0]) < 2 or devices[0][1] != "device":
            raise RuntimeError("Refusing to clear app data: exactly one online emulator must be attached")
        self.serial = devices[0][0]
        if not self.serial.startswith("emulator-"):
            raise RuntimeError("Refusing to clear app data: the device must have an emulator serial")
        qemu = self.command("shell", "getprop", "ro.kernel.qemu").stdout.strip()
        if qemu != "1":
            raise RuntimeError("Refusing to clear app data: ro.kernel.qemu must be 1")
        api = self.command("shell", "getprop", "ro.build.version.sdk").stdout.strip()
        if api != "35":
            raise RuntimeError(f"This CI smoke test requires API 35, found {api!r}")
        self.report.update({"device": self.serial, "api": 35, "qemu": qemu})
        self.safe_device = True

    def dump_ui(self, label: str) -> ET.Element:
        self.dump_number += 1
        remote = "/sdcard/tasbih-process-restart.xml"
        # A failed dump must never let a stale pre-restart XML make an assertion pass.
        self.command("shell", "rm", "-f", remote)
        self.command("shell", "uiautomator", "dump", "--compressed", remote, timeout=20)
        xml = self.command("exec-out", "cat", remote).stdout
        (REPORTS / f"{self.dump_number:02d}-{label}.xml").write_text(xml, encoding="utf-8")
        return ET.fromstring(xml)

    def wait_counter(self, expected: int, label: str) -> tuple[int, int]:
        deadline = time.monotonic() + 60
        last_error = "counter button was not present"
        while time.monotonic() < deadline:
            try:
                root = self.dump_ui(label)
                for node in root.iter("node"):
                    match = COUNTER_DESCRIPTION.fullmatch(node.get("content-desc", ""))
                    if not match or node.get("package") != PACKAGE:
                        continue
                    count = int(match.group(1))
                    if count != expected:
                        last_error = f"UI count was {count}, expected {expected}"
                        continue
                    bounds = BOUNDS.fullmatch(node.get("bounds", ""))
                    if not bounds or node.get("clickable") != "true" or node.get("enabled") != "true":
                        last_error = "counter must be an enabled, clickable native button with valid bounds"
                        continue
                    left, top, right, bottom = map(int, bounds.groups())
                    if right > left and bottom > top:
                        return (left + right) // 2, (top + bottom) // 2
                    last_error = "counter button bounds were empty"
            except (RuntimeError, ET.ParseError, subprocess.TimeoutExpired) as error:
                last_error = str(error)
            time.sleep(0.4)
        raise RuntimeError(f"Timed out waiting for count {expected}: {last_error}")

    def read_preferences(self, label: str) -> dict[str, str]:
        xml = self.command("exec-out", "run-as", PACKAGE, "cat", PREFERENCES).stdout
        (REPORTS / f"prefs-{label}.xml").write_text(xml, encoding="utf-8")
        root = ET.fromstring(xml)
        if root.tag != "map":
            raise RuntimeError("Unexpected SharedPreferences XML root")
        return {node.get("name", ""): node.get("value", node.text or "") for node in root}

    def assert_disk(self, label: str) -> dict[str, object]:
        preferences = self.read_preferences(label)
        if preferences.get("count") != str(EXPECTED_COUNT):
            raise RuntimeError(f"Disk count must be {EXPECTED_COUNT}, found {preferences.get('count')!r}")
        goal = preferences.get("goal", "0")
        if goal != "0":
            raise RuntimeError(f"Fresh free counting must keep goal absent/0, found {goal!r}")
        return {"count": EXPECTED_COUNT, "goal": int(goal), "goal_present": "goal" in preferences}

    def pids(self) -> set[str]:
        result = self.command("shell", "pidof", PACKAGE, check=False)
        if result.returncode not in (0, 1):
            raise RuntimeError(f"pidof failed: {result.stderr or result.stdout}")
        pids = set(result.stdout.split())
        if any(not pid.isdigit() for pid in pids):
            raise RuntimeError(f"Unexpected pidof output: {result.stdout!r}")
        return pids

    def wait_pids(self, running: bool) -> set[str]:
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            pids = self.pids()
            if bool(pids) == running:
                return pids
            time.sleep(0.2)
        raise RuntimeError(f"Timed out waiting for app process to be {'running' if running else 'gone'}")

    def launch(self) -> None:
        output = self.command("shell", "am", "start", "-W", "-n", ACTIVITY).stdout
        if "Status: ok" not in output:
            raise RuntimeError(f"Activity launch did not report success: {output}")

    def run(self) -> None:
        self.guard()
        self.command("install", "-r", "-t", str(APK), timeout=60)
        # Verify the fixed target really is debuggable before changing any of its app data.
        self.command("shell", "run-as", PACKAGE, "true")
        cleared = self.command("shell", "pm", "clear", PACKAGE).stdout.strip()
        if cleared != "Success":
            raise RuntimeError(f"Clearing the throwaway fixture failed: {cleared}")

        # This is an offline persistence check. Avoid ad/consent arrival shifting the UI;
        # no consent dialogs are accepted or arbitrary third-party buttons are tapped.
        self.command("shell", "svc", "wifi", "disable")
        self.command("shell", "svc", "data", "disable")
        self.report["network_mode"] = "wifi and mobile data disabled for offline persistence check"
        self.launch()
        self.wait_counter(0, "fresh-count")
        for before in range(EXPECTED_COUNT):
            # Re-read native button bounds every tap, rather than assuming fixed coordinates.
            x, y = self.wait_counter(before, f"before-tap-{before + 1}")
            self.command("shell", "input", "tap", str(x), str(y))
        self.wait_counter(EXPECTED_COUNT, "before-force-stop")
        self.report["before_stop_preferences"] = self.assert_disk("before-stop")
        before_pids = self.wait_pids(True)
        self.report["pids_before_stop"] = sorted(before_pids)

        # No pm clear or preference mutation between these launches.
        self.command("shell", "am", "force-stop", PACKAGE)
        self.wait_pids(False)
        self.report["process_gone_after_force_stop"] = True
        self.launch()
        after_pids = self.wait_pids(True)
        if before_pids & after_pids:
            raise RuntimeError("The restarted app must have a fresh process ID")
        self.report["pids_after_restart"] = sorted(after_pids)
        self.wait_counter(EXPECTED_COUNT, "after-process-restart")
        self.report["after_restart_preferences"] = self.assert_disk("after-restart")
        # Same debug APK/certificate only: this checks reinstall -r retention, not a
        # production signing upgrade or migration from a historical app version.
        self.command("install", "-r", "-t", str(APK), timeout=60)
        self.launch()
        self.wait_counter(EXPECTED_COUNT, "after-same-apk-reinstall")
        self.report["after_same_apk_reinstall_preferences"] = self.assert_disk("after-same-apk-reinstall")
        self.report["same_apk_reinstall_kept_data"] = True
        self.report["result"] = "passed"

    def finish(self, error: Exception | None, duration: float) -> None:
        self.report["duration_seconds"] = round(duration, 2)
        if error:
            self.report["result"] = "failed"
            self.report["error"] = str(error)
            if self.safe_device:
                try:
                    self.dump_ui("failure")
                except Exception as diagnostic_error:
                    self.report["diagnostic_error"] = str(diagnostic_error)
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
        print(f"Process-restart smoke test failed: {error}", file=sys.stderr)
        return 1
    print(f"Process restart passed: UI and disk retained {EXPECTED_COUNT}, no default target. Reports: {REPORTS}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
