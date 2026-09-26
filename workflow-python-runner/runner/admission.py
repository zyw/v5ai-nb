import asyncio


class AdmissionGate:
    """Fail-fast bounded execution admission; no unbounded request queue."""

    def __init__(self, limit: int):
        if limit < 1:
            raise ValueError("limit must be positive")
        self._semaphore = asyncio.Semaphore(limit)
        self._limit = limit
        self._active = 0
        self._lock = asyncio.Lock()

    @property
    def active(self) -> int:
        return self._active

    @property
    def limit(self) -> int:
        return self._limit

    async def try_acquire(self) -> bool:
        async with self._lock:
            if self._active >= self._limit:
                return False
            self._active += 1
            return True

    async def release(self) -> None:
        async with self._lock:
            if self._active < 1:
                raise RuntimeError("admission permit released without acquisition")
            self._active -= 1
