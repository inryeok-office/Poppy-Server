# Agent Credential Controlled Cutover

## Purpose

This document records the software-only compatibility boundary between the
shared bootstrap credential and the per-Agent runtime credential. It is an
operational contract, not a physical execution readiness approval.

## Historical Compatibility Matrix

The migration boundary is verified with the exact historical refs from Server
PR #66 and Agent PR #28:

| Server | Agent | Registration | Runtime API | Support |
| --- | --- | --- | --- | --- |
| PR #66 base (`4ac48f2`) | PR #28 base (`0c100bd`) | PASS | bootstrap credential | SUPPORTED |
| PR #66 base (`4ac48f2`) | PR #28 head (`c345afb`) | PASS | bootstrap fallback when `agentToken` is absent | SUPPORTED |
| PR #66 head (`d61e8d5`) | PR #28 base (`0c100bd`) | PASS | bootstrap request rejected | EXPECTED_FAIL |
| PR #66 head (`d61e8d5`) | PR #28 head (`c345afb`) | PASS | issued runtime credential | SUPPORTED |
| latest Server develop | latest Agent develop | PASS | issued runtime credential | SUPPORTED |

Registration success alone is not compatibility. A supported combination must
also complete an authenticated heartbeat and polling request. A status probe
must not be rejected as authentication failure. The historical Server may
return its legacy domain error (HTTP 500) for a synthetic missing execution;
that is distinct from a 401/403 authentication rejection.

## Preconditions

- Use an isolated local PostgreSQL container and volume.
- Use `ROBOT_MODE=mock` only.
- Drain active executions before changing Server authentication behavior.
- Keep bootstrap and runtime credentials in process memory or environment only.
- Verify every URL is HTTP loopback (`localhost`, `127.0.0.1`, or `::1`).

The historical rehearsal requires caller-provided JARs and detached Agent
worktrees. The harness verifies the legacy refs and refuses to run without an
explicit opt-in.

## Agent-first Deployment

The safe sequence is:

1. Deploy the credential-compatible Agent binary while the legacy Server is
   still running.
2. Confirm registration, heartbeat, and polling continue using the bootstrap
   credential because the legacy response has no `agentToken`.
3. Drain active executions.
4. Upgrade the Server and allow the credential migration to run on the same
   PostgreSQL database.
5. Treat bootstrap-only internal requests as authentication failures.
6. Explicitly restart or re-register each compatible Agent.
7. Confirm the new runtime credential, heartbeat, and polling before resuming
   work.

The Server-first order is unsupported. A legacy Agent can register, but it
ignores the new `agentToken` and its subsequent bootstrap-authenticated
heartbeat is rejected. The Agent does not automatically re-register on 401 or
403, preventing credential rotation ping-pong between old processes.

## Credential Rotation

Registration for an existing logical Agent reuses the Agent ID and rotates the
stored credential digest. The old runtime credential is immediately invalid;
the newly issued credential is valid. The Server resolves the principal from
the credential and still checks the Agent ID in the request path.

Raw bootstrap tokens, runtime credentials, credential digests, headers, and
request bodies are never printed by the harness.

## Rollback

Rollback is tested by starting the historical legacy Server against the
migrated database. If Flyway rejects a database with newer migrations, the
result is recorded as:

`ROLLBACK_NOT_SUPPORTED_WITHOUT_DB_RESTORE`

No downgrade migration is invented. If a legacy Server can start, a runtime
credential is expected to fail against it; an Agent restart and bootstrap
registration are required before legacy operation can resume.

## Rehearsal

Build the four historical artifacts in isolated worktrees, then run:

```text
POPPY_AGENT_CREDENTIAL_CUTOVER_REHEARSAL=1
POPPY_AGENT_CREDENTIAL_CUTOVER_CONFIRM=I_UNDERSTAND_LOCAL_ONLY
POPPY_CUTOVER_SERVER_LEGACY_JAR=...
POPPY_CUTOVER_SERVER_CREDENTIAL_JAR=...
POPPY_CUTOVER_SERVER_LATEST_JAR=...
POPPY_CUTOVER_AGENT_LEGACY_ROOT=...
POPPY_CUTOVER_AGENT_COMPATIBLE_ROOT=...
POPPY_CUTOVER_AGENT_LATEST_ROOT=...
python scripts/agent_credential_cutover_rehearsal.py
```

The command owns only its uniquely labelled PostgreSQL container and volume.
It does not alter Server or Agent source, does not use a production database,
and does not send Robot commands.

## Explicitly Unsupported

- Server-first deployment while legacy Agents remain active.
- Automatic 401/403 re-registration.
- Bootstrap-token fallback for authenticated internal APIs.
- Credential-bearing requests to non-loopback hosts.
- Actual GO2, Unitree SportClient, DDS, movement, or emergency-stop testing.

Physical Readiness remains `BLOCKED`.
