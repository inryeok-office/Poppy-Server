# Poppy-Server systemd / Docker Compose Recovery Rehearsal

## Purpose

This is a software-only rehearsal of the deployment chain:

`systemd -> Docker Compose -> Spring Boot -> PostgreSQL`

It verifies that an Agent using Mock mode fails closed while the Server
application is unavailable, that the Compose project is restarted by systemd,
and that PostgreSQL state and the authoritative Execution recovery contract
survive the restart.

This is an operational test contract, not a production installation command.

## Safety boundary

The rehearsal must run in a disposable Linux environment with real systemd
PID 1 and Docker Compose. It requires all of the following explicit guards:

```bash
export POPPY_SERVER_SYSTEMD_REHEARSAL=1
export POPPY_SERVER_REHEARSAL_CONFIRM=I_UNDERSTAND_LOCAL_ONLY
export ROBOT_MODE=mock
export POPPY_AGENT_ROOT=/mnt/c/Users/user/poppy-worktrees/agent-server-restart-recovery
sudo -E python3 scripts/systemd_runtime_rehearsal.py
```

The harness refuses Windows, a non-systemd host, a non-root invocation, a
non-Mock mode, or the production service name. It creates a unique temporary
systemd unit named `poppy-server-rehearsal.service`, a unique Compose project,
an isolated PostgreSQL named volume, and a localhost-only application port.

It never installs or starts `poppy-server.service`, never touches the
production `poppy-postgres-data` volume, and never changes a host network
interface. The Agent source is consumed as a Mock runtime only.

## Rehearsal phases

The harness executes these phases against one persistent database:

1. Clean systemd/Compose start and Actuator health.
2. Mock Agent registration and operational READY snapshot.
3. Normal Execution: `QUEUED -> ASSIGNED -> RUNNING -> COMPLETED`.
4. Kill only the owned app container and verify Agent `DEGRADED`, no further
   command dispatch, systemd automatic recovery, and authoritative FAILED
   reconciliation.
5. Run `systemctl restart` during another Mock RUNNING Execution and verify
   the same fail-closed/recovery contract.
6. Run an idle `systemctl restart`.
7. Run `systemctl stop` followed by `systemctl start` and verify volume and
   session persistence.
8. Complete a new post-recovery Execution and audit Robot release, snapshot,
   and terminal states.

The rehearsal does not replay command payloads, infer progress, or locally
invent a terminal state. The Server database remains the Execution lifecycle
source of truth.

## Deployment contract covered

The temporary unit is derived from the production contract:

- `Requires=docker.service`
- `After=network-online.target docker.service`
- Compose foreground `up --no-build --abort-on-container-exit --exit-code-from app`
- Compose `stop --timeout 30` for graceful service stop
- `Restart=on-failure`, `RestartSec=5s`
- `SuccessExitStatus=130 143` leaves a normal Compose stop `inactive` rather
  than `failed`, while an app crash exit remains a failure.
- `KillMode=control-group`, `TimeoutStopSec=45s`
- PostgreSQL `service_healthy` dependency
- named volume retention across service restart

The temporary unit runs as root only because the disposable WSL/VM harness
must access the Docker socket without changing a host service account. The
production unit's `User=poppy`, `Group=poppy`, and `SupplementaryGroups=docker`
contract remains statically checked by `scripts/test-systemd-deployment.sh`.

## Cleanup ownership

The harness records its unit, Compose project, container labels, temporary
files, and named volume. Cleanup stops the temporary service, removes the
temporary unit, reloads systemd, and tears down only the owned Compose project
and volume. Cleanup warnings never replace the primary rehearsal failure.

## Manual-only checks

Automatic host reboot is intentionally excluded from the harness. A
disposable VM operator may separately run:

```bash
sudo systemctl enable poppy-server-rehearsal.service
sudo reboot
sudo systemctl is-active poppy-server-rehearsal.service
```

This must not be performed on a developer workstation, production host, or a
machine connected to a physical Robot. The repository CI performs static
deployment checks; it does not run privileged systemd or Docker-in-Docker.

## Observability and secrets

The unit output is available through journald. The harness checks the unit
journal for the generated database password, Agent bootstrap token, raw
authorization/header markers, and command payload markers. None may appear in
the journal or phase output.

## Explicit non-goals

- Unitree GO2, SportClient, DDS, movement, or physical emergency stop.
- Client or UI changes.
- Poppy-Server domain/API changes.
- Host reboot automation.
- Production service, credential, volume, or network-interface changes.
