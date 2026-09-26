from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def _runner_service(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    start = text.index("  python-runner:\n")
    end = text.index("\n  nginx:", start)
    return text[start:end]


def test_compose_variants_deploy_private_runner_with_matching_environment():
    pg = ROOT / "script/docker/docker-compose-postgresql.yml"
    mysql = ROOT / "script/docker/docker-compose-mysql.yml"
    pg_service, mysql_service = _runner_service(pg), _runner_service(mysql)

    for service in (pg_service, mysql_service):
        assert "dockerfile: Dockerfile" in service
        assert "healthcheck:" in service
        assert '"8081"' in service
        assert "python-runner-control" in service
        assert "python-runner-runtime" in service
        assert "privileged:" not in service
        assert "/var/run/docker.sock:" not in service
        assert "docker.sock:/" not in service
    assert pg_service == mysql_service


def test_runner_api_has_no_published_host_port():
    for name in ("docker-compose-postgresql.yml", "docker-compose-mysql.yml"):
        service = _runner_service(ROOT / "script/docker" / name)
        assert "ports:" not in service
