"""Run the software-only Server systemd/Compose recovery rehearsal.

This harness is deliberately guarded.  It may only install and control the
temporary ``poppy-server-rehearsal.service`` unit in a Linux systemd boot,
with a separate Compose project and named volume.  It never targets the
production unit or a physical robot.
"""

from __future__ import annotations

import json
import os
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from tempfile import mkdtemp
from typing import Any
from uuid import uuid4


class RehearsalError(RuntimeError):
    """Raised when a guarded rehearsal phase cannot be completed."""


SERVICE_NAME = "poppy-server-rehearsal.service"
CONFIRMATION = "I_UNDERSTAND_LOCAL_ONLY"
REQUIRED_OPT_IN = "1"


@dataclass(slots=True)
class Rehearsal:
    root: Path
    agent_root: Path
    run_id: str
    project: str
    volume: str
    app_port: int
    server_url: str
    agent_token: str
    db_password: str
    temp_dir: Path
    compose_file: Path
    env_file: Path
    unit_file: Path
    docker_unit_available: bool = False
    unit_installed: bool = False
    compose_started: bool = False

    @property
    def compose_base(self) -> list[str]:
        return [
            "docker",
            "compose",
            "--project-name",
            self.project,
            "--file",
            str(self.compose_file),
            "--env-file",
            str(self.env_file),
        ]

    def compose(
        self, *args: str, check: bool = True
    ) -> subprocess.CompletedProcess[str]:
        timeout = 900.0 if args and args[0] == "build" else 180.0
        return run_command([*self.compose_base, *args], check=check, timeout=timeout)


def main() -> int:
    rehearsal: Rehearsal | None = None
    primary_error: BaseException | None = None
    try:
        rehearsal = _create_rehearsal()
        _validate_host(rehearsal)
        _prepare_files(rehearsal)
        _validate_static_contract(rehearsal)
        _prepare_compose(rehearsal)
        _install_unit(rehearsal)
        _run_rehearsal(rehearsal)
        _assert_journal_safe(rehearsal)
        print("SYSTEMD SERVER RUNTIME REHEARSAL PASSED")
        return 0
    except BaseException as exc:  # noqa: BLE001 - cleanup also handles interrupts
        primary_error = exc
        print(
            f"SYSTEMD SERVER RUNTIME REHEARSAL FAILED: {type(exc).__name__}: {exc}",
            file=sys.stderr,
        )
        return 1
    finally:
        if rehearsal is not None:
            cleanup_errors = _cleanup(rehearsal)
            if cleanup_errors:
                print(
                    "SYSTEMD SERVER RUNTIME CLEANUP WARNINGS: "
                    + ", ".join(cleanup_errors),
                    file=sys.stderr,
                )
                if primary_error is None:
                    # The harness must not report a clean rehearsal when its
                    # owned service or volume could not be removed.
                    raise SystemExit(1)


def _create_rehearsal() -> Rehearsal:
    root = Path(__file__).resolve().parents[1]
    agent_root_value = os.environ.get("POPPY_AGENT_ROOT", "").strip()
    if not agent_root_value:
        raise RehearsalError("POPPY_AGENT_ROOT must point to a checked-out Mock Agent")
    agent_root = Path(agent_root_value).resolve()
    run_id = uuid4().hex[:12]
    project = f"poppy-server-rehearsal-{run_id}"
    volume = f"{project}-postgres-data"
    app_port = _free_local_port()
    temp_dir = Path(mkdtemp(prefix=f"{project}-"))
    return Rehearsal(
        root=root,
        agent_root=agent_root,
        run_id=run_id,
        project=project,
        volume=volume,
        app_port=app_port,
        server_url=f"http://127.0.0.1:{app_port}",
        # These values are generated only for the isolated local fixture and
        # are never printed or sent outside localhost.
        agent_token=f"rehearsal-agent-token-{uuid4().hex}",
        db_password=f"rehearsal-db-password-{uuid4().hex}",
        temp_dir=temp_dir,
        compose_file=temp_dir / "docker-compose.yml",
        env_file=temp_dir / "poppy-server.env",
        unit_file=Path("/run/systemd/system") / SERVICE_NAME,
    )


def _validate_host(rehearsal: Rehearsal) -> None:
    if os.name != "posix" or sys.platform != "linux":
        raise RehearsalError(
            "the rehearsal requires Linux, not Windows or a host shell shim"
        )
    if os.environ.get("POPPY_SERVER_SYSTEMD_REHEARSAL") != REQUIRED_OPT_IN:
        raise RehearsalError("POPPY_SERVER_SYSTEMD_REHEARSAL=1 is required")
    if os.environ.get("ROBOT_MODE", "mock").strip().lower() != "mock":
        raise RehearsalError(
            "ROBOT_MODE=mock is mandatory; physical execution is blocked"
        )
    if os.environ.get("POPPY_SERVER_REHEARSAL_CONFIRM") != CONFIRMATION:
        raise RehearsalError(
            "POPPY_SERVER_REHEARSAL_CONFIRM has not explicitly opted in"
        )
    if os.geteuid() != 0:
        raise RehearsalError("run inside a disposable systemd environment as root")
    if rehearsal.unit_file.name == "poppy-server.service":
        raise RehearsalError("production service name is forbidden")
    if not (rehearsal.agent_root / "src/poppy_agent").is_dir():
        raise RehearsalError("POPPY_AGENT_ROOT does not contain the Agent source")
    if not (rehearsal.agent_root / "scripts/server_restart_recovery_e2e.py").is_file():
        raise RehearsalError("POPPY_AGENT_ROOT is missing the recovery E2E helper")
    if _systemctl("is-system-running", check=False).stdout.strip() not in {
        "running",
        "degraded",
    }:
        raise RehearsalError("systemd PID 1 is not running")
    _require_command("docker")
    compose_version = run_command(["docker", "compose", "version"], check=False)
    if compose_version.returncode != 0:
        raise RehearsalError("Docker Compose plugin is unavailable")
    rehearsal.docker_unit_available = _systemd_unit_exists("docker.service")
    if rehearsal.unit_file.exists():
        raise RehearsalError(f"refusing to overwrite existing {SERVICE_NAME}")
    if _compose_project_exists(rehearsal.project):
        raise RehearsalError("generated Compose project name is already in use")


def _prepare_files(rehearsal: Rehearsal) -> None:
    rehearsal.env_file.write_text(
        "\n".join(
            [
                "SPRING_PROFILES_ACTIVE=local",
                "DB_NAME=poppy_rehearsal",
                "DB_USERNAME=poppy_rehearsal",
                f"DB_PASSWORD={rehearsal.db_password}",
                f"POPPY_AGENT_TOKEN={rehearsal.agent_token}",
                "",
            ]
        ),
        encoding="utf-8",
    )
    rehearsal.env_file.chmod(0o600)
    image = os.environ.get("POPPY_SERVER_REHEARSAL_IMAGE", "").strip()
    jar_value = os.environ.get("POPPY_SERVER_REHEARSAL_JAR", "").strip()
    if image:
        build_or_image = f"    image: {image}\n"
    elif jar_value:
        jar = Path(jar_value).resolve()
        if not jar.is_file():
            raise RehearsalError("POPPY_SERVER_REHEARSAL_JAR does not point to a file")
        shutil.copy2(jar, rehearsal.temp_dir / "app.jar")
        (rehearsal.temp_dir / "rehearsal.Dockerfile").write_text(
            "FROM eclipse-temurin:25-jre-alpine\n"
            "WORKDIR /app\n"
            "COPY app.jar app.jar\n"
            "EXPOSE 8080\n"
            'ENTRYPOINT ["java", "-jar", "app.jar"]\n',
            encoding="utf-8",
        )
        build_or_image = (
            f"    build:\n      context: {_yaml_path(rehearsal.temp_dir)}\n"
            "      dockerfile: rehearsal.Dockerfile\n"
        )
    else:
        build_or_image = f"    build:\n      context: {_yaml_path(rehearsal.root)}\n"
    rehearsal.compose_file.write_text(
        f"""services:
  app:
{build_or_image.rstrip()}
    environment:
      SPRING_PROFILES_ACTIVE: local
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: poppy_rehearsal
      DB_USERNAME: poppy_rehearsal
      DB_PASSWORD: ${{DB_PASSWORD}}
      POPPY_AGENT_TOKEN: ${{POPPY_AGENT_TOKEN}}
    ports:
      - \"127.0.0.1:{rehearsal.app_port}:8080\"
    depends_on:
      postgres:
        condition: service_healthy
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: poppy_rehearsal
      POSTGRES_USER: poppy_rehearsal
      POSTGRES_PASSWORD: ${{DB_PASSWORD}}
    volumes:
      - rehearsal-postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: [\"CMD-SHELL\", \"pg_isready -U poppy_rehearsal -d poppy_rehearsal\"]
      interval: 2s
      timeout: 3s
      retries: 15

volumes:
  rehearsal-postgres-data:
    name: {rehearsal.volume}
""",
        encoding="utf-8",
    )
    rehearsal.compose_file.chmod(0o600)


def _prepare_compose(rehearsal: Rehearsal) -> None:
    rehearsal.compose("config", "--quiet")
    # Build before systemd starts the foreground Compose process.  The unit
    # itself mirrors production and uses --no-build on every restart.
    if os.environ.get("POPPY_SERVER_REHEARSAL_IMAGE", "").strip():
        image = os.environ["POPPY_SERVER_REHEARSAL_IMAGE"].strip()
        if (
            run_command(["docker", "image", "inspect", image], check=False).returncode
            != 0
        ):
            raise RehearsalError("POPPY_SERVER_REHEARSAL_IMAGE is not a local image")
    else:
        rehearsal.compose("build", "app")


def _install_unit(rehearsal: Rehearsal) -> None:
    docker_dependency = (
        "Requires=docker.service\nAfter=network-online.target docker.service"
        if rehearsal.docker_unit_available
        else "After=network-online.target"
    )
    rehearsal.unit_file.write_text(
        f"""[Unit]
Description=POPPY Server isolated systemd rehearsal
{docker_dependency}
Wants=network-online.target

[Service]
Type=simple
User=root
Group=root
WorkingDirectory={rehearsal.temp_dir}
EnvironmentFile={rehearsal.env_file}
ExecStart=/usr/bin/docker compose --project-name {rehearsal.project} --file {rehearsal.compose_file} --env-file {rehearsal.env_file} up --no-build --abort-on-container-exit --exit-code-from app
ExecStop=/usr/bin/docker compose --project-name {rehearsal.project} --file {rehearsal.compose_file} --env-file {rehearsal.env_file} stop --timeout 30
Restart=on-failure
RestartSec=5s
RestartPreventExitStatus=SIGTERM 130 143
SuccessExitStatus=130 143
KillSignal=SIGTERM
KillMode=control-group
TimeoutStopSec=45s

[Install]
WantedBy=multi-user.target
""",
        encoding="utf-8",
    )
    rehearsal.unit_file.chmod(0o644)
    _systemctl("daemon-reload")
    rehearsal.unit_installed = True


def _validate_static_contract(rehearsal: Rehearsal) -> None:
    production = rehearsal.root / "deploy/systemd/poppy-server.service"
    if not production.is_file():
        raise RehearsalError("production systemd unit is missing")
    content = production.read_text(encoding="utf-8")
    for expected in (
        "Requires=docker.service",
        "After=network-online.target docker.service",
        "Restart=on-failure",
        "RestartSec=5s",
        "ExecStop=/usr/bin/docker compose",
        "TimeoutStopSec=45s",
    ):
        if expected not in content:
            raise RehearsalError(f"production unit contract missing: {expected}")


def _run_rehearsal(rehearsal: Rehearsal) -> None:
    os.environ["POPPY_E2E_SERVER_URL"] = rehearsal.server_url
    os.environ["POPPY_E2E_AGENT_TOKEN"] = rehearsal.agent_token
    os.environ["POPPY_E2E_TIMEOUT_SECONDS"] = os.environ.get(
        "POPPY_E2E_TIMEOUT_SECONDS", "120"
    )
    os.environ["POPPY_E2E_HTTP_TIMEOUT_SECONDS"] = os.environ.get(
        "POPPY_E2E_HTTP_TIMEOUT_SECONDS", "5"
    )
    agent_modules = _load_agent_helpers(rehearsal.agent_root)
    config = agent_modules["E2EConfig"].from_environment()
    http = agent_modules["E2EHttpClient"](
        config.server_url, config.http_timeout_seconds, config.agent_token
    )
    runtime = None
    robot_id = None
    executor = None
    session = None
    baseline = None
    interrupted = None
    try:
        _start_service(rehearsal, config.timeout_seconds)
        _phase(
            "clean systemd/Compose startup", _clean_startup(rehearsal, agent_modules)
        )
        robot_id = agent_modules["create_robot"](http, f"systemd-{rehearsal.run_id}")
        runtime = agent_modules["start_runtime"](
            config,
            robot_id,
            f"systemd-rehearsal-agent-{rehearsal.run_id}",
            rehearsal.temp_dir / "agent-status.json",
        )
        agent_modules["prepare_robot"](http, runtime.agent, config)
        executor = _RehearsalExecutor(agent_modules)
        agent_modules["start_loop"](runtime, executor)
        _phase(
            "Mock Agent READY", agent_modules["assert_clean_startup"](runtime, config)
        )

        session = agent_modules["create_session"](http)
        baseline = agent_modules["create_execution"](http, session)
        _wait_running(http, session, baseline, robot_id, config, agent_modules)
        agent_modules["wait_event"](executor, 1, config)
        executor.baseline_release.set()
        _wait_status(http, session, baseline, "COMPLETED", config, agent_modules)
        agent_modules["wait_robot_release"](http, robot_id, config)
        _phase("baseline Execution", True)

        interrupted = agent_modules["create_execution"](http, session)
        _wait_running(http, session, interrupted, robot_id, config, agent_modules)
        agent_modules["wait_event"](executor, 2, config)
        _crash_owned_app(rehearsal)
        _phase(
            "app container crash and Agent fail-closed",
            _assert_transport_outage(
                runtime, executor, interrupted, config, agent_modules
            ),
        )
        _wait_for_health(rehearsal, config.timeout_seconds)
        _phase(
            "systemd automatic recovery and Execution reconciliation",
            agent_modules["assert_recovery"](
                http, runtime, session, interrupted, robot_id, config
            ),
        )
        _assert_volume_exists(rehearsal)

        active_restart = agent_modules["create_execution"](http, session)
        _wait_running(http, session, active_restart, robot_id, config, agent_modules)
        agent_modules["wait_event"](executor, 3, config)
        restart = subprocess.Popen(
            ["systemctl", "restart", SERVICE_NAME],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        try:
            _phase(
                "active Execution systemctl restart fail-closed",
                _assert_transport_outage(
                    runtime, executor, active_restart, config, agent_modules
                ),
            )
            if restart.wait(timeout=config.timeout_seconds) != 0:
                raise RehearsalError("systemctl restart failed")
        finally:
            if restart.poll() is None:
                restart.kill()
        _wait_for_health(rehearsal, config.timeout_seconds)
        _phase(
            "active restart reconciliation",
            agent_modules["assert_recovery"](
                http, runtime, session, active_restart, robot_id, config
            ),
        )
        _assert_volume_exists(rehearsal)

        _systemctl("restart", SERVICE_NAME)
        _wait_for_health(rehearsal, config.timeout_seconds)
        _phase(
            "idle systemctl restart", _ready_snapshot(runtime, agent_modules, config)
        )

        _systemctl("stop", SERVICE_NAME)
        _wait_for_unreachable(rehearsal, config.timeout_seconds)
        if _systemctl_state() != "inactive":
            raise RehearsalError("systemd service did not stop cleanly")
        _systemctl("start", SERVICE_NAME)
        _wait_for_health(rehearsal, config.timeout_seconds)
        _phase(
            "systemctl stop/start and PostgreSQL persistence",
            _ready_snapshot(runtime, agent_modules, config),
        )
        _assert_volume_exists(rehearsal)

        final_execution = agent_modules["create_execution"](http, session)
        _wait_running(http, session, final_execution, robot_id, config, agent_modules)
        agent_modules["wait_event"](executor, 4, config)
        executor.final_release.set()
        _wait_status(http, session, final_execution, "COMPLETED", config, agent_modules)
        agent_modules["wait_robot_release"](http, robot_id, config)
        _phase("post-recovery Execution", True)

        _final_audit(
            http,
            runtime,
            session,
            baseline,
            interrupted,
            active_restart,
            final_execution,
            robot_id,
            config,
            agent_modules,
        )
        _phase("final consistency audit", True)
    finally:
        if runtime is not None:
            _safe_cleanup_runtime(runtime, agent_modules)
        if robot_id is not None and _server_reachable(rehearsal.server_url):
            _safe_call(lambda: agent_modules["retire_robot"](http, robot_id, config))
        if _systemctl_state() in {"active", "activating", "deactivating"}:
            _safe_call(lambda: _stop_rehearsal_service())


class _RehearsalExecutor:
    """Software-only executor with observable dispatch and release gates."""

    def __init__(self, modules: dict[str, Any]) -> None:
        from threading import Event

        self._modules = modules
        self.invocation_count = 0
        self.command_dispatch_count = 0
        self.started: dict[int, Event] = {}
        self.baseline_release = Event()
        self.final_release = Event()

    def execute(self, task: Any, cancellation_token: Any = None) -> Any:
        from threading import Event

        token = cancellation_token
        self.invocation_count += 1
        self.command_dispatch_count += 1
        invocation = self.invocation_count
        self.started.setdefault(invocation, Event()).set()
        result = self._modules["ExecutionResult"]
        status = self._modules["ExecutionStatus"]
        if invocation == 1:
            while not self.baseline_release.wait(0.01):
                if token is not None and token.is_cancelled():
                    return result(task.execution_id, status.CANCELLED)
            return result(task.execution_id, status.COMPLETED)
        if invocation in {2, 3}:
            while token is None or not token.is_cancelled():
                Event().wait(0.01)
            return result(task.execution_id, status.CANCELLED)
        while not self.final_release.wait(0.01):
            if token is not None and token.is_cancelled():
                return result(task.execution_id, status.CANCELLED)
        return result(task.execution_id, status.COMPLETED)


def _load_agent_helpers(agent_root: Path) -> dict[str, Any]:
    scripts = agent_root / "scripts"
    for path in (agent_root / "src", scripts):
        if str(path) not in sys.path:
            sys.path.insert(0, str(path))
    from full_mock_e2e import (  # type: ignore
        E2EConfig,
        E2EHttpClient,
        _execution_status,
        _find_robot,
        _wait_for,
    )
    from operational_recovery_rehearsal import (  # type: ignore
        _assert_snapshot_safe,
        _cleanup_runtime,
        _prepare_robot,
        _read_snapshot,
        _reconcile_before_cleanup,
        _start_loop,
        _start_runtime,
        _wait_for_assignment_or_running,
        _wait_for_event,
        _wait_for_robot_release,
        _wait_for_snapshot,
    )
    from poppy_agent.execution import ExecutionResult, ExecutionStatus  # type: ignore
    from server_restart_recovery_e2e import (  # type: ignore
        _assert_clean_startup,
        _assert_recovery,
        _create_execution,
        _create_robot,
        _create_session_fixture,
    )
    from stale_agent_fencing_e2e import _retire_robot_fixture  # type: ignore

    return {
        "E2EConfig": E2EConfig,
        "E2EHttpClient": E2EHttpClient,
        "execution_status": _execution_status,
        "find_robot": _find_robot,
        "wait_for": _wait_for,
        "cleanup_runtime": _cleanup_runtime,
        "prepare_robot": _prepare_robot,
        "reconcile_before_cleanup": _reconcile_before_cleanup,
        "read_snapshot": _read_snapshot,
        "start_loop": _start_loop,
        "start_runtime": _start_runtime,
        "wait_for_assignment_or_running": _wait_for_assignment_or_running,
        "wait_for_event": _wait_for_event,
        "wait_event": _wait_for_event,
        "wait_for_snapshot": _wait_for_snapshot,
        "wait_robot_release": _wait_for_robot_release,
        "assert_clean_startup": _assert_clean_startup,
        "assert_snapshot_safe": _assert_snapshot_safe,
        "assert_recovery": _assert_recovery,
        "create_execution": _create_execution,
        "create_robot": _create_robot,
        "create_session": _create_session_fixture,
        "retire_robot": _retire_robot_fixture,
        "ExecutionResult": ExecutionResult,
        "ExecutionStatus": ExecutionStatus,
    }


def _start_service(rehearsal: Rehearsal, timeout: float) -> None:
    _systemctl("start", SERVICE_NAME)
    _wait_for_health(rehearsal, timeout)
    if _systemctl_state() != "active":
        raise RehearsalError("rehearsal service is not active after start")
    rehearsal.compose_started = True


def _crash_owned_app(rehearsal: Rehearsal) -> None:
    result = rehearsal.compose("ps", "-q", "app")
    container_id = (
        result.stdout.strip().splitlines()[0] if result.stdout.strip() else ""
    )
    if not container_id:
        raise RehearsalError("owned app container was not running")
    labels = run_command(
        [
            "docker",
            "inspect",
            "--format",
            '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}',
            container_id,
        ]
    ).stdout.strip()
    if labels != f"{rehearsal.project}|app":
        raise RehearsalError("refusing to stop a container not owned by this rehearsal")
    run_command(["docker", "kill", "--signal", "SIGKILL", container_id])


def _clean_startup(rehearsal: Rehearsal, modules: dict[str, Any]) -> bool:
    if not _server_reachable(rehearsal.server_url):
        raise RehearsalError("Spring Boot health was not reachable after clean start")
    return True


def _ready_snapshot(runtime: Any, modules: dict[str, Any], config: Any) -> bool:
    modules["wait_for_snapshot"](
        runtime.status_path,
        "Agent READY snapshot",
        lambda value: (
            value.get("lifecycleState") == "READY"
            and value.get("connectivityState") == "CONNECTED"
            and value.get("operationalReady") is True
            and value.get("acceptingNewExecution") is True
            and value.get("activeExecutionId") is None
        ),
        config,
    )
    return True


def _assert_transport_outage(
    runtime: Any,
    executor: Any,
    execution_id: Any,
    config: Any,
    modules: dict[str, Any],
) -> bool:
    """Observe a stable degraded window without racing a reconnect success."""

    degraded = modules["wait_for_snapshot"](
        runtime.status_path,
        "Agent DEGRADED during Server outage",
        lambda value: (
            value.get("connectivityState") == "DEGRADED"
            and value.get("operationalReady") is False
            and value.get("acceptingNewExecution") is False
            and value.get("activeExecutionId") == str(execution_id)
        ),
        config,
    )
    if not runtime.loop.is_alive() or runtime.errors:
        raise RehearsalError("Agent loop stopped during the Server outage")
    dispatches = executor.command_dispatch_count
    time.sleep(max(0.5, config.poll_interval_seconds * 2))
    stable = modules["read_snapshot"](runtime.status_path)
    if stable.get("connectivityState") != "DEGRADED":
        raise RehearsalError(
            "Server outage window ended before the degraded snapshot check"
        )
    if stable.get("activeExecutionId") != str(execution_id):
        raise RehearsalError("Agent changed active Execution during the Server outage")
    if executor.command_dispatch_count != dispatches:
        raise RehearsalError("Server outage allowed another command dispatch")
    for timestamp_name in ("lastServerSuccessAt", "lastHeartbeatSuccessAt"):
        if stable.get(timestamp_name) != degraded.get(timestamp_name):
            raise RehearsalError(
                f"failed Server request changed {timestamp_name} during the degraded window"
            )
    modules["assert_snapshot_safe"](stable, runtime.agent.runtime_token)
    return True


def _wait_running(
    http: Any,
    session: Any,
    execution: Any,
    robot_id: Any,
    config: Any,
    modules: dict[str, Any],
) -> None:
    modules["wait_for_assignment_or_running"](
        http, session.session_token, execution, robot_id, config
    )
    _wait_status(http, session, execution, "RUNNING", config, modules)


def _wait_status(
    http: Any,
    session: Any,
    execution: Any,
    expected: str,
    config: Any,
    modules: dict[str, Any],
) -> None:
    modules["wait_for"](
        f"Execution {expected}",
        lambda: modules["execution_status"](http, session.session_token, execution),
        lambda value: value.get("status") == expected,
        config,
    )


def _final_audit(
    http: Any,
    runtime: Any,
    session: Any,
    baseline: Any,
    interrupted: Any,
    active_restart: Any,
    final_execution: Any,
    robot_id: Any,
    config: Any,
    modules: dict[str, Any],
) -> None:
    for execution, expected in (
        (baseline, "COMPLETED"),
        (interrupted, "FAILED"),
        (active_restart, "FAILED"),
        (final_execution, "COMPLETED"),
    ):
        _wait_status(http, session, execution, expected, config, modules)
    robot = modules["find_robot"](http, robot_id)
    if (
        robot.get("occupied") is not False
        or robot.get("currentExecutionId") is not None
    ):
        raise RehearsalError("Robot ownership was not released")
    snapshot = modules["read_snapshot"](runtime.status_path)
    if (
        snapshot.get("operationalReady") is not True
        or snapshot.get("activeExecutionId") is not None
    ):
        raise RehearsalError("Agent did not finish READY with no active Execution")


def _assert_journal_safe(rehearsal: Rehearsal) -> None:
    result = run_command(
        ["journalctl", "-u", SERVICE_NAME, "-n", "300", "--no-pager", "--output=cat"],
        check=False,
    )
    if result.returncode not in {0, 1}:
        raise RehearsalError("journalctl could not inspect the rehearsal unit")
    journal = result.stdout
    for secret in (rehearsal.agent_token, rehearsal.db_password):
        if secret and secret in journal:
            raise RehearsalError("rehearsal secret appeared in journald")
    for forbidden in ("X-Agent-Token", "Authorization:", "commandPayload"):
        if forbidden in journal:
            raise RehearsalError(
                "credential/header/payload marker appeared in journald"
            )


def _assert_volume_exists(rehearsal: Rehearsal) -> None:
    result = run_command(["docker", "volume", "inspect", rehearsal.volume], check=False)
    if result.returncode != 0:
        raise RehearsalError("rehearsal PostgreSQL volume disappeared during recovery")


def _cleanup(rehearsal: Rehearsal) -> list[str]:
    failures: list[str] = []
    if rehearsal.unit_installed:
        _cleanup_step(failures, "systemd stop", _stop_rehearsal_service)
        _cleanup_step(failures, "systemd unit removal", lambda: _remove_unit(rehearsal))
        _cleanup_step(
            failures, "systemd daemon-reload", lambda: _systemctl("daemon-reload")
        )
    _cleanup_step(
        failures,
        "owned Compose teardown",
        lambda: rehearsal.compose("down", "--volumes", "--remove-orphans", check=False),
    )
    _cleanup_step(
        failures,
        "temporary files",
        lambda: shutil.rmtree(rehearsal.temp_dir, ignore_errors=False),
    )
    return failures


def _remove_unit(rehearsal: Rehearsal) -> None:
    if rehearsal.unit_file.exists():
        rehearsal.unit_file.unlink()


def _stop_rehearsal_service() -> None:
    deadline = time.monotonic() + 15.0
    while time.monotonic() < deadline:
        _systemctl("stop", SERVICE_NAME, check=False)
        state = _systemctl_state()
        if state in {"inactive", "failed", ""}:
            _systemctl("reset-failed", SERVICE_NAME, check=False)
            return
        time.sleep(0.25)
    raise RehearsalError("rehearsal systemd service did not become inactive")


def _cleanup_step(failures: list[str], name: str, action: Callable[[], Any]) -> None:
    try:
        action()
    except Exception as exc:  # noqa: BLE001 - cleanup must continue
        failures.append(f"{name}: {type(exc).__name__}")


def _safe_cleanup_runtime(runtime: Any, modules: dict[str, Any]) -> None:
    try:
        modules["reconcile_before_cleanup"](runtime)
        modules["cleanup_runtime"](runtime)
    except Exception as exc:  # noqa: BLE001 - cleanup must not mask the primary failure
        print(f"Agent cleanup warning: {type(exc).__name__}", file=sys.stderr)


def _safe_call(action: Callable[[], Any]) -> None:
    try:
        action()
    except Exception as exc:  # noqa: BLE001 - cleanup must not mask the primary failure
        print(f"cleanup warning: {type(exc).__name__}", file=sys.stderr)


def _wait_for_health(rehearsal: Rehearsal, timeout: float) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if _server_reachable(rehearsal.server_url):
            return
        time.sleep(0.2)
    raise RehearsalError("Server actuator health did not become reachable")


def _wait_for_unreachable(rehearsal: Rehearsal, timeout: float) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if not _server_reachable(rehearsal.server_url):
            return
        time.sleep(0.1)
    raise RehearsalError("Server remained reachable after systemd stop")


def _server_reachable(url: str) -> bool:
    try:
        request = urllib.request.Request(f"{url}/actuator/health", method="GET")
        with urllib.request.urlopen(request, timeout=1.0) as response:
            return response.status == 200
    except (OSError, urllib.error.URLError):
        return False


def _systemctl(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return run_command(["systemctl", *args], check=check, timeout=60.0)


def _systemctl_state() -> str:
    return _systemctl(
        "show", "--value", "--property=ActiveState", SERVICE_NAME, check=False
    ).stdout.strip()


def _systemd_unit_exists(name: str) -> bool:
    result = _systemctl("list-unit-files", name, check=False)
    return result.returncode == 0 and any(
        line.split(maxsplit=1)[0] == name
        for line in result.stdout.splitlines()
        if line.strip()
    )


def _compose_project_exists(project: str) -> bool:
    result = run_command(
        ["docker", "compose", "ls", "--all", "--format", "json"], check=False
    )
    return result.returncode == 0 and project in result.stdout


def _free_local_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def _yaml_path(path: Path) -> str:
    value = str(path)
    if any(character in value for character in " :#"):
        return json.dumps(value)
    return value


def _require_command(name: str) -> None:
    if shutil.which(name) is None:
        raise RehearsalError(f"required command is unavailable: {name}")


def run_command(
    command: list[str], *, check: bool = True, timeout: float = 60.0
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        command,
        stdin=subprocess.DEVNULL,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
        check=False,
    )
    if check and result.returncode != 0:
        detail = (
            result.stderr.strip().splitlines()[-1]
            if result.stderr.strip()
            else "no stderr"
        )
        raise RehearsalError(
            f"command failed: {command[0]} {command[1] if len(command) > 1 else ''}: {detail}"
        )
    return result


def _phase(name: str, passed: bool) -> None:
    if not passed:
        raise RehearsalError(f"phase failed: {name}")
    print(f"PASS: {name}")


if __name__ == "__main__":
    raise SystemExit(main())
