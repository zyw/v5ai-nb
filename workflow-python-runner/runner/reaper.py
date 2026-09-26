import asyncio
import logging
import time

from docker.errors import DockerException, NotFound

from runner.sandbox import LABEL_KEY

logger = logging.getLogger("runner.reaper")


class OrphanReaper:
    def __init__(self, client, ttl_seconds: int):
        self.client = client
        self.ttl_seconds = ttl_seconds

    async def reap_once(self) -> int:
        try:
            containers = await asyncio.to_thread(
                self.client.containers.list, all=True, filters={"label": f"{LABEL_KEY}=true"}
            )
        except (DockerException, OSError):
            logger.warning("orphan_reaper_list_failed")
            return 0
        removed = 0
        cutoff = time.time() - self.ttl_seconds
        for container in containers:
            try:
                created = float(container.labels.get("v5ai.workflow-python-runner.created-at", "0"))
                if created <= cutoff:
                    await asyncio.to_thread(container.remove, force=True)
                    removed += 1
            except (DockerException, OSError, ValueError, TypeError, NotFound):
                logger.warning("orphan_reaper_remove_failed")
        return removed

    async def run(self, stop: asyncio.Event, interval_seconds: int = 60) -> None:
        while not stop.is_set():
            await self.reap_once()
            try:
                await asyncio.wait_for(stop.wait(), timeout=interval_seconds)
            except asyncio.TimeoutError:
                continue
