# Register portal path: live proof record

Date: 2026-10-28.
Branch: `chat-lmwuxiiq-portal-path`.
Issue: CHAT-lmwuxiiq.
Script: `docs/superpowers/proofs/2026-10-28-register-portal-path/acceptance.sh`.

## What this proof shows

1. The `register` command reads the node-9 register through the portal.
2. The `register` command writes one entry through the portal.
3. A read falls back to the one-shot login when the portal socket is absent.

The proof uses one private filesystem root. It starts one portal. It stops
only that portal. It never moves a shared file and never stops a portal that
it did not start. It shares the backend register store by design.

## Command

```bash
PROOF_PASSWORD_FILE=/tmp/rpp-secrets.PK26hW/Theta.password \
PROOF_READ_TARGET_MS=20000 \
bash docs/superpowers/proofs/2026-10-28-register-portal-path/acceptance.sh
```

`PROOF_READ_TARGET_MS=20000` sets the read target. Each `register` call starts
one JVM, so the default target of 10 s is tight on a loaded machine. The
portal budget is not changed. The portal keeps its fixed deadline of 30 s.

`PROOF_PASSWORD_FILE` names a private copy of the Theta password. The script
reads it in place. The script copies it nowhere. The value is not recorded
here.

## Output

```text
ok   portal_started
ok   ownership_recorded
ok   register_created=demo-chat-register-proof-125301e3
ok   config_written
ok   portal_read=ok
ok   portal_open=ok
ok   portal_write=ok
ok   portal_readback=ok
ok   portal_stopped
ok   fallback=ok
portal_read=ok portal_write=ok fallback=ok
register=demo-chat-register-proof-125301e3
rc=0
```

Exit status: 0. Ten checks passed.

## Portal ownership record

The proof wrote these values to its own `ownership.txt`. It read the two
process command lines and applied one rule to each process. Both rules passed
before the script sent any stop command.

```text
ownership: socket=/var/folders/kl/ykc9r7y56899ly09hphb8wf00000gn/T//register-portal-proof.YubK8u/portal/p
ownership: state=/var/folders/kl/ykc9r7y56899ly09hphb8wf00000gn/T//register-portal-proof.YubK8u/state/node2-portal-theta
ownership: register=demo-chat-register-proof-125301e3
ownership: portal.pid=79285
ownership: tunnel.pid=79270
```

The portal pid held `-Dchat.shell.portal.socket=<proof_root>/portal/p`.
The tunnel pid held `-- node2.lan` and `127.0.0.1:6890`.

## Register created by this run

`demo-chat-register-proof-125301e3`.

The owner is Mario. The source date is 2026-10-28. The node-side
`register-create` command made the register. The register holds one fixture
entry under the key `proof-key`.

Retention: keep this register, or clear the node-9 data at the next
reinstall. The proof deletes nothing, because no delete command exists.

Earlier proof runs left these registers. All six are in the same store:

- `demo-chat-register-proof-112952ed`
- `demo-chat-register-proof-113613d2`
- `demo-chat-register-proof-114218ee`
- `demo-chat-register-proof-11574420`
- `demo-chat-register-proof-122754e5`
- `demo-chat-register-proof-12350537`

## Fallback leg used a stub shell

Step 10 of the proof removes the private socket file. It points
`CHAT_SHELL_CLI` at a stub. The stub prints one fixture record and records its
arguments. `NODE9_SECRETS_DIR` points at a directory with a fixture
`Theta.password` at mode 600. That value is a test value, not a real secret.

The stub proves that the one-shot path runs when the portal is absent. It does
not prove a live one-shot login. The live one-shot login is outside this
proof. It needs a real credential, and the portal path is the path that needs
no credential.

## Read-target override

Step 5 read the register through the portal with an empty secrets directory.
That read succeeded, so the portal path used no password. This is the point of
the proof. The read target was raised to 20 s for the reason given above.

## Notes on the run

- The fixture heading is `## Imported section`. An earlier heading,
  `## Proof entry`, made `register-create` mint a topic that the pointer step
  then owned by another key. The core refused the write. The refusal was
  correct. The fixture changed, and the product code did not.
- This run left no private directory. The proof removes its own root when
  every check passes. Three roots remain from earlier failed runs. They hold
  only logs. Remove them when the review closes:
  `/var/folders/kl/ykc9r7y56899ly09hphb8wf00000gn/T/register-portal-proof.4woKX8`,
  `/var/folders/kl/ykc9r7y56899ly09hphb8wf00000gn/T/register-portal-proof.R4VN8a`,
  `/var/folders/kl/ykc9r7y56899ly09hphb8wf00000gn/T/register-portal-proof.ZoxcQ3`.
