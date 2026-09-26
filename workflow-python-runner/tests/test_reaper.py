import asyncio
import time
from unittest.mock import MagicMock

from runner.reaper import OrphanReaper
from runner.sandbox import LABEL_KEY


def test_reaper_only_removes_old_managed_containers():
    client = MagicMock()
    old = MagicMock()
    old.labels = {"v5ai.workflow-python-runner.created-at": str(time.time() - 1000)}
    fresh = MagicMock()
    fresh.labels = {"v5ai.workflow-python-runner.created-at": str(time.time())}
    client.containers.list.return_value = [old, fresh]

    count = asyncio.run(OrphanReaper(client, 600).reap_once())

    assert count == 1
    old.remove.assert_called_once_with(force=True)
    fresh.remove.assert_not_called()
    client.containers.list.assert_called_once_with(all=True, filters={"label": f"{LABEL_KEY}=true"})


def test_reaper_fails_closed_when_runtime_is_unavailable():
    client = MagicMock()
    client.containers.list.side_effect = OSError("connection details must not escape")

    assert asyncio.run(OrphanReaper(client, 600).reap_once()) == 0
