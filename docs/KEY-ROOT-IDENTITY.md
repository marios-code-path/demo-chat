# Key and root identity data graph

These diagrams describe the approved design for `CHAT-avduuqwp`.
They do not report implementation completion.
`T` is the ID type, such as `Long` or UUID.

The sources are the [design specification](superpowers/specs/2026-09-24-key-root-identity-design.md)
and the [approved implementation plan at bb728f54](https://github.com/marios-code-path/demo-chat/blob/bb728f541a4c70d61cde3393f60f41574e27be9e/docs/superpowers/plans/2026-09-25-key-root-identity.md).
The plan defines the implementation steps and verification requirements.

## Key members and implementations

Arrows point from each implementation toward its interface.

```mermaid
flowchart BT
    K["Key&lt;T&gt;<br/>id: T<br/>root: T<br/>empty: Boolean"]
    SK["SimpleKey&lt;T&gt;<br/>id: T<br/>root: T<br/>empty = false"]
    NK["NoKey&lt;T&gt;<br/>Extends Key&lt;T&gt;"]
    EK["EmptyKey&lt;T&gt;<br/>id: T — placeholder<br/>root: T — domain root ID<br/>empty = true"]
    MK["MessageKey&lt;T&gt;<br/>Inherits id, root, empty<br/>from: T<br/>dest: T"]
    SMK["SimpleMessageKey&lt;T&gt;<br/>id: T<br/>root: T<br/>from: T<br/>dest: T<br/>empty = false"]

    SK -->|implements| K
    NK -->|extends| K
    EK -->|implements| NK
    MK -->|extends| K
    SMK -->|implements| MK
```

Both `id` and `root` are required.
The `empty` property distinguishes a placeholder from an ordinary key.
An empty key still carries the root of its domain.

All implementations compare `id`, `root`, and `empty` for equality.
They use the same fields for their hash code.
The message fields `from` and `dest` remain raw IDs and do not affect equality.
Equality does not verify a root against the registry.

| Factory | Result |
|---|---|
| `Key.of(id, root)` | An ordinary key with the supplied ID and root. |
| `Key.root(id)` | A root key whose `root` equals its `id`. |
| `Key.empty(placeholder, root)` | An empty key with an explicit domain root. |
| `MessageKey.of(id, root, from, dest)` | A message key with raw sender and destination IDs. |

These factories construct values.
They do not register IDs or verify caller claims.

## Domains and roots

`ChatDomain` selects a domain.
A key stores the domain's root ID, rather than the enum value or a class name.

```mermaid
flowchart LR
    D["ChatDomain<br/>wireName: String<br/><br/>USER<br/>MESSAGE<br/>MESSAGE_TOPIC<br/>TOPIC_MEMBERSHIP<br/>AUTH_METADATA<br/>KEY_VALUE_PAIR<br/>CONVERSATION_EPOCH<br/>FRANKING_TAG"]
    RS["RootKeyStore&lt;T&gt;<br/>Stored mapping<br/>ChatDomain → root ID"]
    LOAD["RootKeyLoader&lt;T&gt;<br/>Loads roots<br/>Creates missing roots conditionally"]
    RK["RootKeys&lt;T&gt;<br/>Domain mapping<br/>ChatDomain → Key&lt;T&gt;"]
    ROOT["Domain root key<br/>id = R<br/>root = R<br/>empty = false"]
    ENTITY["Key in that domain<br/>id = X<br/>root = R<br/>empty = false"]

    D -->|identifies domain| RS
    RS -->|supplies stored roots| LOAD
    LOAD -->|loads domain mapping| RK
    RK -->|of domain| ROOT
    ENTITY -->|root equals root key ID| ROOT
```

A root key is an ordinary key whose `id` equals its `root`.
Each domain has one root per key type in the configured root store.
Persistent backends retain these roots across restarts.
The memory backend does not retain roots across process restarts.

`Admin` and `Anon` remain user identities.
Their keys carry the `USER` root.
They are not additional domains.

## Minting and verification

```mermaid
flowchart TD
    DOMAIN["ChatDomain"]
    SERVICE["IKeyService&lt;T&gt;<br/>key(domain)<br/>rootOf(id)<br/>exists(key)<br/>rem(key)"]
    REGISTRY["Key registry<br/>ID → root ID"]
    KEY["Key&lt;T&gt;<br/>id, root, empty"]
    VERIFIER["KeyVerifier&lt;T&gt;<br/>verify(key, expectedDomain)<br/>resolve(id, expectedDomain)"]
    VERIFIED["VerifiedKey&lt;T&gt;<br/>key: Key&lt;T&gt;"]
    STORE["Typed store result"]
    TRUST["trustTypedStore(key, domain)<br/>Checks the domain root<br/>Does not read the registry"]

    DOMAIN -->|mint request| SERVICE
    SERVICE -->|records identity| REGISTRY
    SERVICE -->|returns minted key| KEY
    KEY -->|inbound verification| VERIFIER
    REGISTRY -->|supplies stored root| VERIFIER
    VERIFIER -->|returns wrapper| VERIFIED
    STORE --> TRUST
    TRUST -->|returns wrapper| VERIFIED
```

The key service also resolves domain root IDs from the root state.
Deleting ordinary registry rows does not remove the roots.
The key service refuses root deletion.

`VerifiedKey<T>` contains a key.
It does not extend `Key<T>`.
Its guarantee depends on the construction path.

| Path | Guarantee |
|---|---|
| `verify(key, expectedDomain)` | Reads the registry. Refuses an unknown ID, a forged root, or a mismatch with a supplied domain. |
| `resolve(id, expectedDomain)` | Reads the registry. Constructs the key with its stored root. Checks a supplied domain. |
| `trustTypedStore(key, domain)` | Checks only that the key root matches the store domain. Trusts the caller and can accept an unknown ID. |

Only `hasAccessToEntity` may call `trustTypedStore`.
The plan requires source guards and independent boundary tests for this restriction.
The `internal` constructor alone does not prove verification.

## Store rows and request values

```mermaid
flowchart LR
    ROW["Cassandra row classes<br/>Do not implement Key"]
    STORE["Typed store<br/>Maps rows to domain objects"]
    OBJECT["Domain object<br/>Carries a domain key"]
    REQUEST["ChatMessageKey<br/>Request value<br/>Does not implement MessageKey"]
    MESSAGE["MessageKey&lt;T&gt;<br/>id, root, from, dest"]

    ROW -->|read| STORE
    STORE -->|stamps domain root| OBJECT
    REQUEST -->|toKey with explicit root| MESSAGE
```

Store rows and request values do not become additional key implementations.
The three canonical classes retain the common equality rule.
Converting a request value to a key does not verify its identity.
