# Message commands

A message send is a captured command. Independent backend handlers run it. The caller waits for a declared set of backends.

Spec: `docs/superpowers/specs/2026-10-07-domain-command-bus-design.md`. This document describes Stage 1.

## Stage 1 limits

- The command bus is `memory`. It runs in one process.
- Stage 1 gives no crash-recovery guarantee. A restart loses request mappings, command status, and unresolved commands.
- The `replicas` check reads a declaration. It does not detect other processes. Do not run two processes with the `memory` bus against one store.
- Command TTL is disabled. Startup rejects a finite value.

## Properties

| Property | Default | Meaning |
|---|---|---|
| `app.command.bus` | none | Required. Stage 1 accepts `memory`. Startup rejects an unset value, an unknown value, and `kafka`. |
| `app.command.ttl` | `disabled` | Startup rejects a finite value. |
| `app.command.replicas` | `1` | A declaration. Startup rejects any other value. |
| `app.command.completion.requirement` | `P,I` | The backends a caller waits for. `none` waits for none. |
| `app.command.completion.timeout` | `5s` | The caller wait. A starting value, not a production measurement. |
| `app.command.recovery.interval` | `30s` | The interval between recovery attempts. A starting value, not a production measurement. |

A duration is an integer and one unit: `ms`, `s`, `m`, `h`, or `d`. Example: `30s`.

`P` is Persistence, `I` is Index, `V` is Vector, and `U` is Pubsub.

## Submit

| Transport | Route | Request ID |
|---|---|---|
| REST | `POST /message/submit/{roomId}`, body is the text | Header `Idempotency-Key` |
| RSocket | `message.message-submit` with `MessageSubmitRequest` | Field `requestId` |

A request ID has 1 to 128 visible ASCII characters. Keep it for each intentional send. Reuse it on every retry. A retry with the same text and room recovers the same receipt. A retry with other text under the same ID is a conflict.

The server binds the sender to the authenticated user. A submission carries no sender.

| Outcome | REST status | Meaning |
|---|---|---|
| `COMPLETED` | 201 | Every required backend succeeded. |
| `ACCEPTED` | 202 | The requirement is `none`. Admission committed. |
| `PENDING` | 202 | The wait ended first. Execution continues. |
| `INCOMPLETE` | 424 | A required backend failed. |

A conflict answers 409. A missing or invalid request ID answers 400.

With `none`, a message lookup can answer not found until `P` completes.

## Status

REST: `GET /message/command/{commandId}`. RSocket: `message.message-command-status`.

Only the owner reads a status. Another caller receives not found.

An anonymous caller cannot submit, cannot use the legacy send, and cannot read a status. Every anonymous caller holds one shared `Anon` key, and request identity needs an authenticated owner.

## Legacy send

`POST /message/send/{roomId}` and `message.message-send` stay as adapters.

- **They give no retry safety.** The server creates a new request ID for each call. A retry can store a second message.
- **Behavior change:** the RSocket `message-send` rejects a `from` value that is not the authenticated user. Before Stage 1 it accepted any sender.
- **Behavior change:** both legacy routes refuse an anonymous caller.
- They wait for the default requirement. A wait that ends answers 504 on REST, with the command ID. A failed required backend answers 424.

## Delivery to listeners

A listener receives the room history, the messages that `U` published before it subscribed, and the live messages. It receives each message ID once per subscription. A message whose `U` failed reaches a listener only through the history, after `I` indexes it.

## Recovery

Only a refusal before any effect is a definitive failure. Every other error is uncertain, because it can follow an effect.

Each backend makes at most five attempts, including the first. A backend that is still uncertain then retries once per recovery interval with the same command. An uncertain command holds the later commands of its room for that backend. Other rooms and other backends continue.

An attempt that runs longer than 30 seconds becomes uncertain. The runtime never cancels it, because a cancel does not prove that the backend stopped. Nothing else in that room and backend starts until it ends.

## Supported providers

Stage 1 starts only with providers that have safe-repeat evidence in CI:

| Selector | Values |
|---|---|
| `app.service.core.key` | `memory`, `redis`, `cassandra` |
| `app.service.core.persistence` | `memory`, `redis`, `cassandra` |
| `app.service.core.index` | `lucene`, `cassandra` |
| `app.service.core.vector` | `simple`, `embedded`, `redis` |
| `app.service.core.pubsub` | `memory`, `redis-pubsub`, `kafka` |

Startup rejects any other value, such as `redis-xstream`.
