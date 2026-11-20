#!/usr/bin/env python3
"""Cari elemen di layar lewat uiautomator lalu ketuk tengahnya. Dipakai untuk uji manual cepat."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = "/home/boxd/android-sdk/platform-tools/adb"


def dump():
    subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    out = subprocess.run([ADB, "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True)
    return out.stdout


def tap(teks, kunci="text", jeda=1.5):
    xml = dump()
    akar = ET.fromstring(xml[xml.index("<?xml"):])
    pola = re.compile(teks, re.I)
    for simpul in akar.iter("node"):
        nilai = simpul.get(kunci) or ""
        if pola.search(nilai):
            x1, y1, x2, y2 = (int(v) for v in re.findall(r"\d+", simpul.get("bounds")))
            subprocess.run([ADB, "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
            time.sleep(jeda)
            return nilai
    print("TIDAK KETEMU:", teks)
    return None


def ketik(teks):
    subprocess.run([ADB, "shell", "input", "text", teks.replace(" ", "%s")])
    time.sleep(1)


if __name__ == "__main__":
    perintah = sys.argv[1]
    if perintah == "tap":
        print(tap(sys.argv[2]))
    elif perintah == "ketik":
        ketik(sys.argv[2])
    elif perintah == "dump":
        for baris in re.findall(r'text="([^"]{1,60})"', dump()):
            print(baris)
