#!/usr/bin/env python3
"""Check the UI-only boundary against the supplied smali; does not modify the app."""
import argparse
import re
from pathlib import Path


def method(path, signature):
    text = path.read_text()
    blocks = re.findall(r"^\.method[^\n]*\n.*?^\.end method", text, re.M | re.S)
    matches = [b for b in blocks if b.splitlines()[0].endswith(" " + signature)]
    assert len(matches) == 1, (path, signature, len(matches))
    return matches[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("smali_root", type=Path)
    root = parser.parse_args().smali_root
    rel = "com/baidu/baidumaps/process/callback/CarTransferCallback"
    owner = root / (rel + ".smali")
    task = root / (rel + "$checkTopPageAndShow$1.smali")
    body = method(task, "invoke()V")
    expected = {
        "Lcom/baidu/mapframework/app/fpstack/TaskManagerFactory;->getTaskManager()Lcom/baidu/mapframework/app/fpstack/TaskManager;",
        "Lcom/baidu/mapframework/app/fpstack/TaskManager;->getLatestRecord()Lcom/baidu/mapframework/app/fpstack/HistoryRecord;",
        "Ljava/lang/Class;->getName()Ljava/lang/String;",
        "Ljava/lang/String;->equals(Ljava/lang/Object;)Z",
        "L" + rel + ";->c(L" + rel + ";)V",
        "Lu1/d;->h(Ljava/lang/String;)V",
    }
    calls = set(re.findall(r"invoke-\S+\s+\{[^}]*\},\s+(\S+)", body))
    calls = {c for c in calls if not c.startswith("Lcom/baidu/titan/")}
    assert calls == expected, ("UI task body changed", calls ^ expected)
    assert 'const-string v0, "auto"' in body
    assert "->resetToRootRecord()V" in method(owner, "j()V")
    assert "->j()V" in method(owner, "c(L" + rel + ";)V")
    scheduler = method(owner, "e()V")
    assert "$checkTopPageAndShow$1;-><init>" in scheduler
    assert "->runOnMainForData$default" in scheduler
    receive = method(owner, "onReceiveMessage(Lr1/c;Ljava/lang/String;Ljava/lang/String;)V")
    branch = receive[receive.index('"车机开始导航"'):receive.index("L" + rel + ";->e()V")]
    assert "CarTransferConfig;->t(I)V" in branch and "->k()V" in branch
    route = method(root / "u1/d.smali", "h(Ljava/lang/String;)V")
    assert '"com.baidu.baidumaps.carconnect.CarConnectControlPage"' in route
    assert "->navigateTo(" in route
    print("PASS: auto UI task contains only known UI calls; state updates precede the skipped task")


if __name__ == "__main__":
    main()
