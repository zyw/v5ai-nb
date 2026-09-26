FROM python:3.12.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PYTHONNOUSERSITE=1 V5AI_RUNNER_WAIT_FOR_INPUTS=1
RUN groupadd --gid 65532 runner && useradd --uid 65532 --gid 65532 --no-create-home --shell /usr/sbin/nologin runner
COPY --chown=0:0 entrypoint.py /opt/runner/entrypoint.py
COPY --chown=0:0 supervisor.py /opt/runner/supervisor.py
RUN chmod 0444 /opt/runner/entrypoint.py /opt/runner/supervisor.py && mkdir -p /work && chmod 0777 /work
USER 65532:65532
WORKDIR /work
ENTRYPOINT ["python", "/opt/runner/supervisor.py"]
CMD ["30", "/opt/runner/entrypoint.py", "/work/script.py", "/work/input.json", "/work/result.json", "1048576"]
