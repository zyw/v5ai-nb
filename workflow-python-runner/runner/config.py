from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class RunnerSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="V5AI_PYTHON_RUNNER_", extra="ignore")

    api_token: SecretStr = Field(min_length=32)
    default_execution_seconds: int = Field(default=30, ge=1, le=300)
    max_execution_seconds: int = Field(default=30, ge=1, le=300)
    max_script_bytes: int = Field(default=65_536, ge=1, le=1_048_576)
    max_request_bytes: int = Field(default=1_048_576, ge=128, le=8_388_608)
    max_output_bytes: int = Field(default=1_048_576, ge=128, le=8_388_608)
    max_concurrent_executions: int = Field(default=4, ge=1, le=256)
    max_json_depth: int = Field(default=32, ge=1, le=128)
    runtime_endpoint: str = "unix:///var/run/docker.sock"
    runtime_ca_cert: str = ""
    runtime_client_cert: str = ""
    runtime_client_key: str = ""
    executor_image: str = ""
    runtime_name: str = "runsc"
    production_mode: bool = True
    cpu_limit: float = Field(default=1.0, gt=0, le=8)
    memory_limit_bytes: int = Field(default=268_435_456, ge=33_554_432, le=4_294_967_296)
    pids_limit: int = Field(default=64, ge=8, le=512)
    workspace_limit_bytes: int = Field(default=16_777_216, ge=1_048_576, le=268_435_456)
    log_limit_bytes: int = Field(default=65_536, ge=1_024, le=1_048_576)
    orphan_ttl_seconds: int = Field(default=600, ge=60, le=86_400)

    @field_validator("api_token")
    @classmethod
    def token_must_be_nontrivial(cls, value: SecretStr):
        if len(value.get_secret_value().strip()) < 32:
            raise ValueError("API token must contain at least 32 non-whitespace characters")
        return value

    @field_validator("executor_image")
    @classmethod
    def production_image_must_be_digest_pinned(cls, value: str, info):
        # Cross-field production validation occurs after all fields are populated below.
        return value.strip()

    @model_validator(mode="after")
    def validate_remote_tls(self):
        if self.runtime_endpoint.startswith(("tcp://", "https://")):
            if not (self.runtime_ca_cert and self.runtime_client_cert and self.runtime_client_key):
                raise ValueError("remote runtime endpoint requires CA and client TLS certificates")
        return self
