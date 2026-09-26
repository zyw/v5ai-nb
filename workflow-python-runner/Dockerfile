FROM python:3.12.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY pyproject.toml ./
COPY runner ./runner
RUN pip install --no-cache-dir .
USER 65532:65532
EXPOSE 8081
CMD ["uvicorn", "runner.main:app", "--host", "0.0.0.0", "--port", "8081", "--no-access-log"]
