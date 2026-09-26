import json
import math
import os
import runpy
import sys
import time
import traceback

UNSAFE_KEYS = {"__proto__", "prototype", "constructor"}


class BoundedText:
    def __init__(self, limit):
        self.limit = limit
        self.data = []
        self.size = 0

    def write(self, value):
        encoded = value.encode("utf-8", errors="replace")
        remaining = max(0, self.limit - self.size)
        if remaining:
            self.data.append(encoded[:remaining].decode("utf-8", errors="ignore"))
            self.size += min(len(encoded), remaining)
        return len(value)

    def flush(self):
        return None


def validate(value, depth=0):
    if depth > 32:
        raise ValueError("result nesting exceeds limit")
    if value is None or isinstance(value, (str, bool, int)):
        return
    if isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("non-finite float")
        return
    if isinstance(value, list):
        for item in value:
            validate(item, depth + 1)
        return
    if isinstance(value, dict):
        if any(not isinstance(key, str) for key in value):
            raise ValueError("result keys must be strings")
        if any(key in UNSAFE_KEYS for key in value):
            raise ValueError("result contains an unsafe key")
        for item in value.values():
            validate(item, depth + 1)
        return
    raise ValueError("result is not JSON serializable")


def main():
    if len(sys.argv) != 5:
        return 2
    script_path, input_path, result_path, output_limit = sys.argv[1:]
    cap = min(int(output_limit), int(os.environ.get("RESULT_MAX_BYTES", output_limit)))
    if os.environ.get("V5AI_RUNNER_WAIT_FOR_INPUTS") == "1":
        deadline = time.monotonic() + 15
        ready_path = os.path.join(os.path.dirname(script_path), ".ready")
        while not all(os.path.isfile(path) for path in (script_path, input_path, ready_path)):
            if time.monotonic() >= deadline:
                return 4
            time.sleep(0.05)
    with open(input_path, "r", encoding="utf-8") as source:
        inputs = json.load(source)
    if not isinstance(inputs, dict):
        return 3
    stdout, stderr = BoundedText(65536), BoundedText(65536)
    namespace = {"__name__": "__main__", "inputs": inputs}
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout, sys.stderr = stdout, stderr
    try:
        with open(script_path, "r", encoding="utf-8") as source:
            code = compile(source.read(), "<workflow-script>", "exec")
        exec(code, namespace, namespace)
        result = namespace.get("result")
        if not isinstance(result, dict):
            raise ValueError("result must be an object")
        validate(result)
        encoded = json.dumps(result, ensure_ascii=False, allow_nan=False, separators=(",", ":")).encode("utf-8")
        if len(encoded) > cap:
            raise ValueError("result exceeds limit")
        with open(result_path, "wb") as target:
            target.write(encoded)
        return 0
    except BaseException:
        traceback.print_exc(file=stderr)
        return 1
    finally:
        sys.stdout, sys.stderr = old_out, old_err


if __name__ == "__main__":
    raise SystemExit(main())
