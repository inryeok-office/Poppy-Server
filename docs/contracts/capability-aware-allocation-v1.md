# Capability-aware Robot allocation v1

Execution allocation uses the immutable `requiredCapabilities` snapshot created with the Execution.

## Capability matching

| Command type | Required capability |
| --- | --- |
| `WAIT` | none |
| `MOVE` | `COMMAND_MOVE` |
| `TURN` | `COMMAND_TURN` |
| `STOP` | `COMMAND_STOP` |
| `POSTURE` | `COMMAND_POSTURE` |
| `PRESET` | `COMMAND_PRESET` |

A Robot is eligible only when it is active, `ONLINE`, `READY`, unoccupied, and supports every required capability with status `VERIFIED`.

`UNVERIFIED` and `UNSUPPORTED` capabilities do not satisfy a requirement. Unknown extra Robot capabilities are retained and do not affect matching.

## Selection and locking

Candidates are considered in deterministic ID order. Each candidate is reloaded with the existing pessimistic write lock before its eligibility and capability state are checked. The first matching candidate is assigned; when none matches, the Execution remains `QUEUED`.

Executions without a compiled snapshot are legacy data and retain the previous availability-only allocation behavior for compatibility. Newly created Session executions always contain a compiled snapshot, including an empty requirement set.

This contract does not change Queue ordering, Agent delivery, or physical Robot control.
