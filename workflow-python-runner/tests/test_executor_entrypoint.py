import json
import subprocess
import sys


def test_executor_reads_inputs_and_writes_json_object_result(tmp_path):
    script = tmp_path / "script.py"
    inputs = tmp_path / "input.json"
    result = tmp_path / "result.json"
    script.write_text("result = {'double': inputs['value'] * 2}", encoding="utf-8")
    inputs.write_text('{"value": 21}', encoding="utf-8")

    proc = subprocess.run(
        [sys.executable, "images/python-executor/entrypoint.py", str(script), str(inputs), str(result), "1048576"],
        capture_output=True,
        text=True,
        check=False,
    )

    assert proc.returncode == 0
    assert json.loads(result.read_text(encoding="utf-8")) == {"double": 42}


def test_executor_rejects_non_object_and_oversized_results(tmp_path):
    script = tmp_path / "script.py"
    inputs = tmp_path / "input.json"
    result = tmp_path / "result.json"
    inputs.write_text("{}", encoding="utf-8")
    script.write_text("result = [1]", encoding="utf-8")
    proc = subprocess.run([sys.executable, "images/python-executor/entrypoint.py", str(script), str(inputs), str(result), "100"], capture_output=True)
    assert proc.returncode != 0

    script.write_text("result = {'value': 'x' * 1000}", encoding="utf-8")
    proc = subprocess.run([sys.executable, "images/python-executor/entrypoint.py", str(script), str(inputs), str(result), "100"], capture_output=True)
    assert proc.returncode != 0
