# Agent Execution Recovery v1

## Scope

This contract reconciles an execution left assigned to a Robot after an Agent
restart. It is a software lifecycle operation only. It does not replay a command
payload, resume progress, or issue a physical stop.

## Discovery

An authenticated Agent may query:

```text
GET /api/v1/internal/agents/{agentId}/robots/{robotId}/active-execution
```

The response contains `activeExecution: null` when the Server has no active
assignment for that bound Robot. Otherwise it contains the execution ID, Robot ID,
and `ASSIGNED` or `RUNNING` status.

## Reconciliation

When an active execution is discovered after restart, the Agent calls:

```text
POST /api/v1/internal/agents/{agentId}/robots/{robotId}/active-execution/recover
```

The Server locks the Execution before the Robot, revalidates Agent/Robot and
Execution/Robot ownership, transitions `ASSIGNED` or `RUNNING` to `FAILED`,
records the terminal timestamp, publishes the normal status event, and releases
the Robot. A repeated request returns `NO_ACTIVE_EXECUTION` after the first
successful reconciliation.

Terminal executions and inconsistent ownership fail closed. They are not silently
released or changed.

## Ownership and restart

Agent registration rotates the Agent credential for an existing Agent name. The
recovery endpoint requires the current authenticated Agent and its Robot binding;
stale Agent credentials cannot use the endpoint. This is a bounded handoff
contract, not a distributed lease system.

## Safety boundary

The Server never infers a command checkpoint and never sends the command payload
during recovery. Automatic resume, retry, physical stop, Unitree SDK calls, DDS,
and real Robot control are outside this contract.
