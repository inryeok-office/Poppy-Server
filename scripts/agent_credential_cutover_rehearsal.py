"""Exercise the Agent credential migration boundary with real local processes.

This harness intentionally requires explicit local-only opt-in and caller-provided
historical artifacts.  It never connects to a non-loopback URL and never prints a
credential, request header, or response body.
"""

from __future__ import annotations

import json
import os
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, TextIO
from uuid import UUID, uuid4


class RehearsalError(RuntimeError):
    """Raised when a cutover contract assertion fails."""


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *_args: Any, **_kwargs: Any) -> None:
        raise RehearsalError("redirects are not allowed in the local rehearsal")


@dataclass(frozen=True, slots=True)
class Config:
    server_legacy_jar: Path
    server_credential_jar: Path
    server_latest_jar: Path
    agent_legacy_root: Path
    agent_compatible_root: Path
    agent_latest_root: Path
    python_executable: str
    timeout_seconds: float
    bootstrap_token: str
    expected_legacy_server_sha: str
    expected_credential_server_sha: str
    expected_latest_server_sha: str
    expected_legacy_agent_sha: str
    expected_compatible_agent_sha: str
    expected_latest_agent_sha: str

    @classmethod
    def from_environment(cls) -> Config:
        if (
            os.name == "nt"
            and os.environ.get("POPPY_AGENT_CUTOVER_ALLOW_WINDOWS") != "1"
        ):
            raise RehearsalError(
                "run the rehearsal in Linux/WSL, or set POPPY_AGENT_CUTOVER_ALLOW_WINDOWS=1 "
                "only for a localhost mock run"
            )
        if os.environ.get("POPPY_AGENT_CREDENTIAL_CUTOVER_REHEARSAL") != "1":
            raise RehearsalError(
                "POPPY_AGENT_CREDENTIAL_CUTOVER_REHEARSAL=1 is required"
            )
        if (
            os.environ.get("POPPY_AGENT_CREDENTIAL_CUTOVER_CONFIRM")
            != "I_UNDERSTAND_LOCAL_ONLY"
        ):
            raise RehearsalError("explicit local-only confirmation is required")
        if os.environ.get("ROBOT_MODE", "mock").strip().lower() != "mock":
            raise RehearsalError(
                "the credential rehearsal only permits ROBOT_MODE=mock"
            )

        def required_path(name: str) -> Path:
            value = os.environ.get(name, "").strip()
            if not value:
                raise RehearsalError(f"{name} is required")
            path = Path(value).expanduser().resolve()
            if not path.exists():
                raise RehearsalError(f"{name} does not exist")
            return path

        timeout = float(os.environ.get("POPPY_AGENT_CUTOVER_TIMEOUT_SECONDS", "30"))
        if not 1 <= timeout <= 120:
            raise RehearsalError("POPPY_AGENT_CUTOVER_TIMEOUT_SECONDS must be 1..120")

        config = cls(
            server_legacy_jar=required_path("POPPY_CUTOVER_SERVER_LEGACY_JAR"),
            server_credential_jar=required_path("POPPY_CUTOVER_SERVER_CREDENTIAL_JAR"),
            server_latest_jar=required_path("POPPY_CUTOVER_SERVER_LATEST_JAR"),
            agent_legacy_root=required_path("POPPY_CUTOVER_AGENT_LEGACY_ROOT"),
            agent_compatible_root=required_path("POPPY_CUTOVER_AGENT_COMPATIBLE_ROOT"),
            agent_latest_root=required_path("POPPY_CUTOVER_AGENT_LATEST_ROOT"),
            python_executable=os.environ.get("POPPY_CUTOVER_PYTHON", sys.executable),
            timeout_seconds=timeout,
            bootstrap_token="poppy-cutover-bootstrap-local-only",
            expected_legacy_server_sha=os.environ.get(
                "POPPY_CUTOVER_LEGACY_SERVER_SHA",
                "4ac48f230830f497417fad6cd2e5ea9ff2e300d1",
            ),
            expected_credential_server_sha=os.environ.get(
                "POPPY_CUTOVER_CREDENTIAL_SERVER_SHA",
                "d61e8d58fd423d38b0aa991ed5a3485bbd5dcf24",
            ),
            expected_latest_server_sha=os.environ.get(
                "POPPY_CUTOVER_LATEST_SERVER_SHA",
                "25978db62749f854ccd9e1a7ccdf71dd1c243f08",
            ),
            expected_legacy_agent_sha=os.environ.get(
                "POPPY_CUTOVER_LEGACY_AGENT_SHA",
                "0c100bded99bd7661552671441ad013ec27d74cf",
            ),
            expected_compatible_agent_sha=os.environ.get(
                "POPPY_CUTOVER_COMPATIBLE_AGENT_SHA",
                "c345afba79baaf11987912f8b53470cffd0cd923",
            ),
            expected_latest_agent_sha=os.environ.get(
                "POPPY_CUTOVER_LATEST_AGENT_SHA",
                "8ec2ac232f1664acedfe4c3bc5b0dbdb18baf9e8",
            ),
        )
        _assert_git_revision(
            config.server_legacy_jar.parents[2], config.expected_legacy_server_sha
        )
        _assert_git_revision(
            config.server_credential_jar.parents[2],
            config.expected_credential_server_sha,
        )
        _assert_git_revision(
            config.server_latest_jar.parents[2], config.expected_latest_server_sha
        )
        _assert_git_revision(config.agent_legacy_root, config.expected_legacy_agent_sha)
        _assert_git_revision(
            config.agent_compatible_root, config.expected_compatible_agent_sha
        )
        _assert_git_revision(config.agent_latest_root, config.expected_latest_agent_sha)
        return config


@dataclass(frozen=True, slots=True)
class HttpResult:
    status: int
    data: dict[str, Any] | None


class Api:
    """Small secret-safe JSON client for the public and Agent APIs."""

    def __init__(self, base_url: str, timeout: float) -> None:
        parsed = urllib.parse.urlparse(base_url)
        if parsed.scheme != "http" or parsed.hostname not in {
            "localhost",
            "127.0.0.1",
            "::1",
        }:
            raise RehearsalError("rehearsal URL must be an http loopback URL")
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.opener = urllib.request.build_opener(_NoRedirect())

    def request(
        self,
        method: str,
        path: str,
        *,
        token: str | None = None,
        payload: dict[str, object] | None = None,
        params: dict[str, str] | None = None,
    ) -> HttpResult:
        query = f"?{urllib.parse.urlencode(params)}" if params else ""
        headers = {"Accept": "application/json"}
        if token is not None:
            headers["X-Agent-Token"] = token
        encoded = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            encoded = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}{path}{query}",
            data=encoded,
            headers=headers,
            method=method,
        )
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                status = response.status
                raw = response.read()
        except urllib.error.HTTPError as exc:
            raw = exc.read()
            return HttpResult(exc.code, _json_data(raw))
        except (urllib.error.URLError, TimeoutError) as exc:
            raise RehearsalError(
                f"{method} {path} transport failure: {type(exc).__name__}"
            ) from exc
        return HttpResult(status, _json_data(raw))

    def expect_data(
        self,
        method: str,
        path: str,
        expected_status: int,
        *,
        token: str | None = None,
        payload: dict[str, object] | None = None,
        params: dict[str, str] | None = None,
    ) -> dict[str, Any]:
        result = self.request(method, path, token=token, payload=payload, params=params)
        if result.status != expected_status or result.data is None:
            raise RehearsalError(
                f"{method} {path} returned unexpected HTTP {result.status}"
            )
        return result.data


class Postgres:
    def __init__(self, timeout: float) -> None:
        run_id = uuid4().hex[:12]
        self.name = f"poppy-cutover-postgres-{run_id}"
        self.volume = f"poppy-cutover-volume-{run_id}"
        self.port = _free_port()
        self.timeout = timeout
        self.owned = False

    def start(self) -> None:
        _run(
            "docker",
            "volume",
            "create",
            "--label",
            "poppy.rehearsal=agent-credential-cutover",
            "--label",
            f"poppy.rehearsal.volume={self.volume}",
            self.volume,
        )
        self.owned = True
        _run(
            "docker",
            "run",
            "--detach",
            "--name",
            self.name,
            "--label",
            "poppy.rehearsal=agent-credential-cutover",
            "--label",
            f"poppy.rehearsal.container={self.name}",
            "--publish",
            f"127.0.0.1:{self.port}:5432",
            "--health-cmd",
            "pg_isready -U poppy -d poppy",
            "--health-interval",
            "1s",
            "--health-timeout",
            "2s",
            "--health-retries",
            "30",
            "--env",
            "POSTGRES_DB=poppy",
            "--env",
            "POSTGRES_USER=poppy",
            "--env",
            "POSTGRES_PASSWORD=poppy-cutover-db-local-only",
            "--volume",
            f"{self.volume}:/var/lib/postgresql/data",
            "postgres:16-alpine",
        )
        _wait_for(
            "PostgreSQL health",
            lambda: (
                _run(
                    "docker",
                    "inspect",
                    "--format",
                    "{{.State.Health.Status}}",
                    self.name,
                    check=False,
                ).stdout.strip()
                == "healthy"
            ),
            self.timeout,
        )
        _wait_for(
            "PostgreSQL published port",
            lambda: _port_open("127.0.0.1", self.port),
            self.timeout,
        )

    def cleanup(self) -> None:
        if not self.owned:
            return
        inspect = _run(
            "docker",
            "inspect",
            "--format",
            '{{ index .Config.Labels "poppy.rehearsal" }}',
            self.name,
            check=False,
        )
        if (
            inspect.returncode == 0
            and inspect.stdout.strip() == "agent-credential-cutover"
        ):
            _run("docker", "rm", "--force", self.name, check=False)
        volume = _run(
            "docker",
            "volume",
            "inspect",
            "--format",
            '{{ index .Labels "poppy.rehearsal" }}',
            self.volume,
            check=False,
        )
        if (
            volume.returncode == 0
            and volume.stdout.strip() == "agent-credential-cutover"
        ):
            _run("docker", "volume", "rm", self.volume, check=False)


class ServerProcess:
    def __init__(
        self, jar: Path, postgres: Postgres, config: Config, label: str
    ) -> None:
        self.jar = jar
        self.postgres = postgres
        self.config = config
        self.label = label
        self.port = _free_port()
        self.process: subprocess.Popen[str] | None = None
        self.log_file: TextIO | None = None
        self.log_path: Path | None = None

    @property
    def url(self) -> str:
        return f"http://127.0.0.1:{self.port}"

    def start(self) -> None:
        if self.process is not None:
            raise RehearsalError(f"{self.label} Server is already running")
        fd, raw_log_path = tempfile.mkstemp(
            prefix="poppy-cutover-server-", suffix=".log"
        )
        os.close(fd)
        self.log_path = Path(raw_log_path)
        self.log_file = self.log_path.open("w", encoding="utf-8")
        env = os.environ.copy()
        env.update(
            {
                "SPRING_PROFILES_ACTIVE": "local",
                "DB_HOST": "127.0.0.1",
                "DB_PORT": str(self.postgres.port),
                "DB_NAME": "poppy",
                "DB_USERNAME": "poppy",
                "DB_PASSWORD": "poppy-cutover-db-local-only",
                "POPPY_AGENT_TOKEN": self.config.bootstrap_token,
                "SPRING_MAIN_BANNER_MODE": "off",
            }
        )
        self.process = subprocess.Popen(
            ["java", "-jar", str(self.jar), f"--server.port={self.port}"],
            cwd=self.jar.parent,
            env=env,
            stdout=self.log_file,
            stderr=subprocess.STDOUT,
            text=True,
        )
        _wait_for(
            f"{self.label} Server health", self._healthy, self.config.timeout_seconds
        )

    def _healthy(self) -> bool:
        if self.process is None:
            return False
        if self.process.poll() is not None:
            return False
        try:
            result = Api(self.url, 1.0).request("GET", "/actuator/health")
        except RehearsalError:
            return False
        return result.status == 200

    def has_schema_compatibility_failure(self) -> bool:
        """Identify only a Flyway/schema-version rollback incompatibility."""
        if self.log_path is None or not self.log_path.exists():
            return False
        try:
            log = self.log_path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            return False
        markers = (
            "Migration validation failed",
            "Validate failed",
            "Migration checksum mismatch",
            "Detected resolved migration not applied to database",
            "contains migrations newer than the latest available migration",
        )
        return any(marker in log for marker in markers)

    def stop(self) -> None:
        process = self.process
        self.process = None
        if process is None:
            return
        if process.poll() is None:
            if os.name == "nt":
                # java.exe can outlive the Popen wrapper on Windows.  The
                # process was started by this owned harness, so terminate its
                # process tree without touching unrelated services.
                _run("taskkill", "/PID", str(process.pid), "/T", "/F", check=False)
            else:
                process.terminate()
            try:
                process.wait(timeout=min(15.0, self.config.timeout_seconds))
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
        if self.log_file is not None:
            path = self.log_path
            self.log_file.close()
            if path is not None:
                try:
                    path.unlink(missing_ok=True)
                except PermissionError:
                    # Windows may release a child process handle slightly after
                    # wait(); leave the non-secret temporary log for inspection.
                    pass
            self.log_file = None
            self.log_path = None


class AgentProcess:
    def __init__(
        self,
        root: Path,
        api: Api,
        config: Config,
        name: str,
        robot_id: UUID,
        label: str,
    ) -> None:
        self.root = root
        self.api = api
        self.config = config
        self.name = name
        self.robot_id = robot_id
        self.label = label
        self.process: subprocess.Popen[str] | None = None
        self.log_path: Path | None = None

    def start(self, *, heartbeat_interval: float = 0.5) -> None:
        fd, raw_path = tempfile.mkstemp(prefix="poppy-cutover-agent-", suffix=".log")
        os.close(fd)
        self.log_path = Path(raw_path)
        handle = self.log_path.open("w", encoding="utf-8")
        env = os.environ.copy()
        env.update(
            {
                "PYTHONPATH": str(self.root / "src"),
                "PYTHONUNBUFFERED": "1",
                "ROBOT_MODE": "mock",
                "POPPY_ROBOT_ID": str(self.robot_id),
                "POPPY_SERVER_URL": self.api.base_url,
                "POPPY_AGENT_TOKEN": self.config.bootstrap_token,
                "POPPY_AGENT_NAME": self.name,
                "POPPY_AGENT_VERSION": self.label,
                "POPPY_SDK_VERSION": "cutover-rehearsal",
                "POPPY_AGENT_PLATFORM": "local-mock",
                "POPPY_HEARTBEAT_INTERVAL_SECONDS": str(heartbeat_interval),
                "POPPY_EXECUTION_POLL_INTERVAL_SECONDS": "0.2",
                "POPPY_SERVER_MAX_RETRIES": "0",
                "POPPY_SERVER_CONNECT_TIMEOUT_SECONDS": "1",
                "POPPY_SERVER_READ_TIMEOUT_SECONDS": "2",
            }
        )
        self.process = subprocess.Popen(
            [self.config.python_executable, "-c", _HEARTBEAT_ONLY_RUNNER],
            cwd=self.root,
            env=env,
            stdout=handle,
            stderr=subprocess.STDOUT,
            text=True,
        )
        handle.close()

    def stop(self) -> None:
        process = self.process
        self.process = None
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
        if self.log_path is not None:
            self.log_path.unlink(missing_ok=True)
            self.log_path = None


_HEARTBEAT_ONLY_RUNNER = r"""
import sys
from threading import Event

from poppy_agent.agent import create_agent
from poppy_agent.config import AgentConfig, ConfigurationError
from poppy_agent.execution import MockExecutionExecutor
from poppy_agent.main import register_signal_handlers
from poppy_agent.server import (
    AgentServerRuntime,
    AgentServerRuntimeError,
    ServerClient,
    ServerClientError,
    ServerConfig,
    ServerConfigurationError,
)

runtime = None
try:
    agent_config = AgentConfig.from_environment()
    server_config = ServerConfig.from_environment()
    server_client = ServerClient(server_config)
    runtime = AgentServerRuntime(create_agent(agent_config), server_client, server_config)
    stop_event = Event()
    register_signal_handlers(stop_event)
    registration = runtime.start()
    print(f"Agent registered: {registration.agent_id}", flush=True)
    print("Heartbeat and polling loop started", flush=True)
    runtime.run_loop(stop_event, MockExecutionExecutor())
except (ConfigurationError, ServerConfigurationError) as exc:
    print(f"Configuration error: {exc}", file=sys.stderr, flush=True)
    raise SystemExit(1)
except (AgentServerRuntimeError, ServerClientError, ValueError) as exc:
    print(f"Poppy-Agent failed: {exc}", file=sys.stderr, flush=True)
    raise SystemExit(1)
except Exception:
    print(
        "Poppy-Agent failed due to an unexpected runtime error",
        file=sys.stderr,
        flush=True,
    )
    raise SystemExit(1)
finally:
    if runtime is not None:
        runtime.shutdown()
        print("Agent stopped", flush=True)
"""


def main() -> int:
    try:
        config = Config.from_environment()
        print("Credential cutover rehearsal: local mock mode")
        _run_matrix(config)
        _run_controlled_cutover(config)
        print("AGENT CREDENTIAL CUTOVER REHEARSAL PASSED")
        return 0
    except (RehearsalError, ValueError) as exc:
        print(f"Credential cutover rehearsal FAILED: {exc}", file=sys.stderr)
        return 1


def _run_matrix(config: Config) -> None:
    print("[1/3] Building historical compatibility matrix ...", flush=True)
    postgres = Postgres(config.timeout_seconds)
    server: ServerProcess | None = None
    try:
        postgres.start()
        server = ServerProcess(config.server_legacy_jar, postgres, config, "legacy")
        server.start()
        _run_matrix_case(
            server,
            config,
            config.agent_legacy_root,
            "legacy-server-legacy-agent",
            False,
            False,
        )
        _run_matrix_case(
            server,
            config,
            config.agent_compatible_root,
            "legacy-server-compatible-agent",
            False,
            True,
        )
        server.stop()

        server = ServerProcess(
            config.server_credential_jar, postgres, config, "credential"
        )
        server.start()
        _run_matrix_case(
            server,
            config,
            config.agent_legacy_root,
            "credential-server-legacy-agent",
            True,
            False,
        )
        _run_matrix_case(
            server,
            config,
            config.agent_compatible_root,
            "credential-server-compatible-agent",
            True,
            True,
        )
        server.stop()

        server = ServerProcess(config.server_latest_jar, postgres, config, "latest")
        server.start()
        _run_matrix_case(
            server,
            config,
            config.agent_latest_root,
            "latest-server-latest-agent",
            True,
            True,
        )
        server.stop()
        print("[1/3] Historical matrix: PASS", flush=True)
    finally:
        if server is not None:
            server.stop()
        postgres.cleanup()


def _run_matrix_case(
    server: ServerProcess,
    config: Config,
    agent_root: Path,
    label: str,
    expects_token: bool,
    agent_supports_token: bool,
) -> None:
    print(f"  {label} ...", flush=True)
    api = Api(server.url, config.timeout_seconds)
    direct_robot = _create_robot(api, label + "-direct")
    direct = _register(api, config, agent_root, direct_robot, label + "-direct")
    has_token = direct.get("agentToken") is not None
    if has_token != expects_token:
        raise RehearsalError(f"{label} returned unexpected runtime credential contract")
    credential = direct.get("agentToken") if expects_token else config.bootstrap_token
    if expects_token and not isinstance(credential, str):
        raise RehearsalError(f"{label} did not issue a runtime credential")
    assert credential is not None
    _assert_runtime_api(api, direct, direct_robot, credential)

    process_robot = _create_robot(api, label + "-process")
    process = AgentProcess(
        agent_root, api, config, label + "-process", process_robot, label
    )
    process.start()
    try:
        if expects_token and not agent_supports_token:
            _wait_for(
                f"{label} Agent authentication failure",
                lambda: (
                    process.process is not None and process.process.poll() is not None
                ),
                config.timeout_seconds,
            )
            if process.process is None or process.process.returncode == 0:
                raise RehearsalError(f"{label} legacy Agent did not fail closed")
        else:
            _wait_robot_online(api, process_robot, config.timeout_seconds)
            if process.process is None or process.process.poll() is not None:
                raise RehearsalError(
                    f"{label} Agent exited during a supported combination"
                )
    finally:
        process.stop()


def _run_controlled_cutover(config: Config) -> None:
    print("[2/3] Running persistent Agent-first cutover ...", flush=True)
    postgres = Postgres(config.timeout_seconds)
    legacy: ServerProcess | None = None
    current: ServerProcess | None = None
    agent: AgentProcess | None = None
    try:
        postgres.start()
        legacy = ServerProcess(
            config.server_legacy_jar, postgres, config, "legacy-cutover"
        )
        legacy.start()
        api = Api(legacy.url, config.timeout_seconds)
        robot_id = _create_robot(api, "controlled-cutover-robot")
        agent_name = "controlled-cutover-agent"
        first = _register(
            api, config, config.agent_compatible_root, robot_id, agent_name
        )
        if first.get("agentToken") is not None:
            raise RehearsalError(
                "legacy Server unexpectedly issued a runtime credential"
            )
        agent_id = _uuid(first, "agentId")
        _heartbeat(api, agent_id, robot_id, config.bootstrap_token, expected={200})

        agent = AgentProcess(
            config.agent_compatible_root,
            api,
            config,
            agent_name,
            robot_id,
            "compatible-agent",
        )
        agent.start(heartbeat_interval=30.0)
        _wait_robot_online(api, robot_id, config.timeout_seconds)
        agent.stop()
        legacy.stop()
        legacy = None

        current = ServerProcess(
            config.server_credential_jar, postgres, config, "credential-cutover"
        )
        current.start()
        api = Api(current.url, config.timeout_seconds)
        result = _heartbeat(
            api, agent_id, robot_id, config.bootstrap_token, expected={401, 403}
        )
        if result.status not in {401, 403}:
            raise RehearsalError(
                "bootstrap-only request remained valid after Server cutover"
            )
        print("  bootstrap-only request: EXPECTED_FAIL", flush=True)

        agent = AgentProcess(
            config.agent_compatible_root,
            api,
            config,
            agent_name,
            robot_id,
            "compatible-agent-restart",
        )
        agent.start()
        _wait_robot_online(api, robot_id, config.timeout_seconds)
        agent.stop()

        rotated_one = _register(
            api, config, config.agent_compatible_root, robot_id, agent_name
        )
        token_one = rotated_one.get("agentToken")
        rotated_two = _register(
            api, config, config.agent_compatible_root, robot_id, agent_name
        )
        if (
            _uuid(rotated_one, "agentId") != agent_id
            or _uuid(rotated_two, "agentId") != agent_id
        ):
            raise RehearsalError("same logical Agent did not retain its Agent ID")
        token_two = rotated_two.get("agentToken")
        if (
            not isinstance(token_one, str)
            or not isinstance(token_two, str)
            or token_one == token_two
        ):
            raise RehearsalError(
                "credential rotation did not produce distinct runtime credentials"
            )
        if _heartbeat(
            api, agent_id, robot_id, token_one, expected={401, 403}
        ).status not in {401, 403}:
            raise RehearsalError("previous runtime credential remained valid")
        _assert_runtime_api(api, rotated_two, robot_id, token_two)
        print("  controlled restart and credential rotation: PASS", flush=True)

        current.stop()
        current = ServerProcess(
            config.server_latest_jar, postgres, config, "latest-after-cutover"
        )
        current.start()
        latest_api = Api(current.url, config.timeout_seconds)
        latest_robot = _create_robot(latest_api, "latest-after-cutover-robot")
        latest = AgentProcess(
            config.agent_latest_root,
            latest_api,
            config,
            "latest-after-cutover-agent",
            latest_robot,
            "latest-agent",
        )
        latest.start()
        try:
            _wait_robot_online(latest_api, latest_robot, config.timeout_seconds)
        finally:
            latest.stop()
        print("[2/3] Persistent cutover: PASS", flush=True)
    finally:
        if agent is not None:
            agent.stop()
        if current is not None:
            current.stop()
        if legacy is not None:
            legacy.stop()
        postgres.cleanup()

    _run_rollback_probe(config)


def _run_rollback_probe(config: Config) -> None:
    print("[3/3] Investigating rollback on migrated database ...", flush=True)
    postgres = Postgres(config.timeout_seconds)
    credential: ServerProcess | None = None
    server: ServerProcess | None = None
    try:
        postgres.start()
        credential = ServerProcess(
            config.server_credential_jar, postgres, config, "rollback-forward"
        )
        credential.start()
        api = Api(credential.url, config.timeout_seconds)
        robot_id = _create_robot(api, "rollback-robot")
        registration = _register(
            api, config, config.agent_compatible_root, robot_id, "rollback-agent"
        )
        token = registration.get("agentToken")
        if not isinstance(token, str):
            raise RehearsalError(
                "credential Server did not issue a rollback probe token"
            )
        agent_id = _uuid(registration, "agentId")
        credential.stop()
        server = ServerProcess(
            config.server_legacy_jar, postgres, config, "rollback-legacy"
        )
        try:
            server.start()
        except RehearsalError:
            if server.has_schema_compatibility_failure():
                print(
                    "[3/3] Rollback: ROLLBACK_NOT_SUPPORTED_WITHOUT_DB_RESTORE",
                    flush=True,
                )
                return
            raise
        legacy_api = Api(server.url, config.timeout_seconds)
        if _heartbeat(
            legacy_api, agent_id, robot_id, token, expected={401, 403}
        ).status not in {401, 403}:
            raise RehearsalError(
                "legacy Server accepted a credential-only token during rollback"
            )
        _heartbeat(
            legacy_api, agent_id, robot_id, config.bootstrap_token, expected={200}
        )
        print("[3/3] Rollback: SUPPORTED_WITH_AGENT_RESTART", flush=True)
    finally:
        if server is not None:
            server.stop()
        if credential is not None:
            credential.stop()
        postgres.cleanup()


def _register(
    api: Api, config: Config, agent_root: Path, robot_id: UUID, agent_name: str
) -> dict[str, Any]:
    # The request mirrors the historical AgentRegistrationRequest.  The root is
    # intentionally accepted so the caller records which real Agent binary owns
    # this protocol probe; the wire contract itself is version-stable.
    del agent_root
    data = api.expect_data(
        "POST",
        "/api/v1/internal/agents/register",
        201,
        token=config.bootstrap_token,
        payload={
            "agentName": agent_name,
            "agentVersion": "credential-cutover-probe",
            "sdkVersion": "not-applicable",
            "platform": "local-mock",
            "robots": [
                {
                    "robotId": str(robot_id),
                    "model": "mock",
                    "edition": "development",
                    "firmwareVersion": "mock",
                    "capabilities": [],
                }
            ],
        },
    )
    return data


def _assert_runtime_api(
    api: Api, registration: dict[str, Any], robot_id: UUID, token: str
) -> None:
    agent_id = _uuid(registration, "agentId")
    _heartbeat(api, agent_id, robot_id, token, expected={200})
    polled = api.request(
        "GET",
        f"/api/v1/internal/agents/{agent_id}/executions/next",
        token=token,
        params={"robotId": str(robot_id)},
    )
    if polled.status != 200:
        raise RehearsalError(f"authenticated polling returned HTTP {polled.status}")
    status = api.request(
        "GET",
        f"/api/v1/internal/agents/{agent_id}/executions/{uuid4()}/status",
        token=token,
        params={"robotId": str(robot_id)},
    )
    # The PR #66 base returns a legacy domain error for a synthetic missing
    # execution. It is still an authenticated response; 401/403 are the only
    # statuses that indicate the credential was rejected at this probe.
    if status.status not in {200, 404, 500}:
        raise RehearsalError(
            f"authenticated status request returned HTTP {status.status}"
        )


def _heartbeat(
    api: Api, agent_id: UUID, robot_id: UUID, token: str, *, expected: set[int]
) -> HttpResult:
    result = api.request(
        "POST",
        f"/api/v1/internal/agents/{agent_id}/heartbeat",
        token=token,
        payload={
            "sentAt": datetime.now(UTC).replace(tzinfo=None).isoformat(),
            "robots": [
                {
                    "robotId": str(robot_id),
                    "connectionStatus": "ONLINE",
                    "operationalStatus": "READY",
                    "batteryPercent": 100,
                }
            ],
        },
    )
    if result.status not in expected:
        raise RehearsalError(f"heartbeat returned unexpected HTTP {result.status}")
    return result


def _create_robot(api: Api, alias: str) -> UUID:
    data = api.expect_data(
        "POST",
        "/api/v1/admin/robots",
        201,
        payload={
            "alias": alias,
            "model": "mock",
            "edition": "development",
            "firmwareVersion": "mock",
            "sdkVersion": "not-applicable",
            "agentId": None,
            "capabilities": [],
            "safetyProfileId": None,
            "isExternal": False,
        },
    )
    return _uuid(data, "robotId")


def _wait_robot_online(api: Api, robot_id: UUID, timeout: float) -> None:
    def online() -> bool:
        result = api.request(
            "GET", "/api/v1/admin/robots", params={"connectionStatus": "ONLINE"}
        )
        if result.status != 200 or result.data is None:
            return False
        robots = result.data.get("robots")
        return isinstance(robots, list) and any(
            isinstance(robot, dict)
            and robot.get("robotId") == str(robot_id)
            and robot.get("connectionStatus") == "ONLINE"
            for robot in robots
        )

    _wait_for("Agent heartbeat", online, timeout)


def _assert_git_revision(root: Path, expected: str) -> None:
    result = _run("git", "-C", str(root), "rev-parse", "HEAD")
    actual = result.stdout.strip()
    if actual != expected:
        raise RehearsalError(f"historical Agent ref mismatch: expected {expected[:8]}")


def _uuid(data: dict[str, Any], name: str) -> UUID:
    value = data.get(name)
    if not isinstance(value, str):
        raise RehearsalError(f"response field {name} is malformed")
    try:
        return UUID(value)
    except ValueError as exc:
        raise RehearsalError(f"response field {name} is malformed") from exc


def _json_data(raw: bytes) -> dict[str, Any] | None:
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return None
    return (
        value.get("data")
        if isinstance(value, dict) and isinstance(value.get("data"), dict)
        else None
    )


def _free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def _port_open(host: str, port: int) -> bool:
    try:
        with socket.create_connection((host, port), timeout=0.5):
            return True
    except OSError:
        return False


def _run(
    *args: str, check: bool = True, timeout: float = 60
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        args,
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
    )
    if check and result.returncode != 0:
        raise RehearsalError(f"command failed: {args[0]} exit={result.returncode}")
    return result


def _wait_for(label: str, predicate: Any, timeout: float) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(0.2)
    raise RehearsalError(f"timed out waiting for {label}")


if __name__ == "__main__":
    raise SystemExit(main())
