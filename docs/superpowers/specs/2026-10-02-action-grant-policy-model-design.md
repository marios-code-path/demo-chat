# Action-triggered grant policy model

## Purpose

Define a policy model for grant work that follows a named operational action.

This work does not enable new permissions. It does not change `joinRoom`.

The model supports actions such as `joinsend`.

## Policy shape

Each named policy contains these values:

- An action name.
- A trigger operation.
- A principal source.
- A target source.
- One or more permission values.
- An expiry rule.

The policy returns logical grant work. It does not select a persistence key.

Example:

```text
joinsend:
  trigger: successful join
  grants:
    - principal: joining principal
      target: joined topic
      permission: SEND
      expiry: never
```

The model must support more than one grant for one action. Each grant must
preserve its principal, target, permission, and expiry values.

## Handler form

An operation may name a policy after it completes its primary work.

```text
@GRANTPOLICY(after = "joinsend")
public Result joinTopic(principal, topic)
```

The annotation is a policy reference. It is not an authorization grant.

The primary operation must complete before policy evaluation starts.

The policy model must define failure behavior separately from the operation.
This issue does not choose compensation or retry behavior.

## Identity and target rules

The principal comes from the authenticated operation context.

The target comes from the named operation result.

The policy must not infer a principal from a topic key.

The policy must not widen a grant by replacing an object target with a domain
root.

## Key rule

The policy model does not reuse an `AuthMetadata` key as a `MessageTopic` key.

`AuthMetadata` keys use the `AUTH_METADATA` root. Topic keys use the
`MESSAGE_TOPIC` root. Key equality includes the root, and the key registry
records the root for each generated identifier.

The implementation issue must keep the topic key and the grant row key
independent. A topic may reference a grant row, but it must not become the
grant row.

## Deferred implementation

A separate child issue defines execution and storage.

That issue may evaluate an authorization topic such as
`authmetadata-<key>`. It must define an explicit relation between that topic
and the stored `AuthMetadata` row.

It must not create two root-typed keys from one generated identifier unless the
key registry and every persistence index support that relation.

## Acceptance

- The model names action triggers without activating them.
- The model returns one or more logical grant intents.
- Each intent carries principal, target, permission, and expiry.
- The model supports named object targets.
- The model keeps storage keys outside the policy contract.
- The model records `joinsend` as an example only.
- No production permission changes occur in this issue.

