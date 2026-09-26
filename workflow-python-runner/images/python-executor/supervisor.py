"""PID 1 watchdog: enforces the server-selected deadline even if the API disappears."""
import os
import signal
import subprocess
import sys


def terminate_group(process):
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=1)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait()


def main():
    if len(sys.argv) < 3:
        return 2
    try:
        timeout_seconds = int(sys.argv[1])
    except ValueError:
        return 2
    if not 1 <= timeout_seconds <= 300:
        return 2
    process = subprocess.Popen(
        [sys.executable, *sys.argv[2:]],
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
        close_fds=True,
    )
    try:
        return process.wait(timeout=timeout_seconds)
    except subprocess.TimeoutExpired:
        terminate_group(process)
        return 124
    except BaseException:
        terminate_group(process)
        raise


if __name__ == "__main__":
    raise SystemExit(main())
