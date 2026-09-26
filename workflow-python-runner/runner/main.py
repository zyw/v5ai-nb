import logging

from runner.app import create_app
from runner.config import RunnerSettings
from runner.sandbox import OciSandboxBackend

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
settings = RunnerSettings()
backend = OciSandboxBackend(settings)
app = create_app(settings, backend)
