import subprocess
import sys
import time


def test_supervisor_kills_script_process_group_at_server_deadline():
    started = time.monotonic()
    proc = subprocess.run(
        [sys.executable, "images/python-executor/supervisor.py", "1", "-c",
         "import subprocess,sys,time; subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(20)']); time.sleep(20)"],
        capture_output=True,
        check=False,
        timeout=4,
    )

    assert proc.returncode == 124
    assert time.monotonic() - started < 4


def test_supervisor_rejects_unbounded_or_invalid_timeout():
    for value in ("0", "301", "not-a-number"):
        proc = subprocess.run(
            [sys.executable, "images/python-executor/supervisor.py", value, "-c", "pass"],
            capture_output=True,
            check=False,
        )
        assert proc.returncode == 2
