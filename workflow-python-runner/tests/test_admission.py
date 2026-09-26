import asyncio

import pytest

from runner.admission import AdmissionGate


@pytest.mark.asyncio
async def test_gate_rejects_immediately_at_capacity_and_releases():
    gate = AdmissionGate(limit=1)

    assert await gate.try_acquire() is True
    assert await gate.try_acquire() is False

    await gate.release()
    assert await gate.try_acquire() is True
    await gate.release()


@pytest.mark.asyncio
async def test_gate_never_exceeds_limit_under_concurrent_acquisition():
    gate = AdmissionGate(limit=2)
    acquired = await asyncio.gather(*(gate.try_acquire() for _ in range(10)))

    assert sum(acquired) == 2
    for _ in range(sum(acquired)):
        await gate.release()
