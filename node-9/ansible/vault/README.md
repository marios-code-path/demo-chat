# Node 9 vaults

Two Ansible Vault files hold the node 9 secrets. Both are per host. The
current Git tree tracks neither file. See CHAT-cnwkjsrp.

| File | Contents | Vault ID |
|------|----------|----------|
| `shared.yml` | `service_password`, `admin_password`, `embedding_api_key`, `sigma_password`, `theta_password`, `phi_password` | `shared` |
| `owner.yml` | `mario_password` | `owner` |

The shared vault holds the values that agents may use. The owner vault
holds the owner password alone. Agents can unlock the shared vault, so it
must not hold the owner password.

## Where the files live

`node-9/install --vault-source DIR` copies the three bundle files to fixed
per-host paths:

| Bundle file | Target | Mode |
|-------------|--------|------|
| `shared.yml` | `<parent of install prefix>/vault/shared.yml` | 0600 |
| `owner.yml` | `<parent of install prefix>/vault/owner.yml` | 0600 |
| `vault-pass` | `${XDG_CONFIG_HOME:-$HOME/.config}/chat-node-9/vault-pass` | 0600 |

The installer links the installed playbook paths
`ansible/vault/shared.yml` and `ansible/vault/owner.yml` to the stable
files. A code replacement therefore removes neither vault.

The prefix defaults to `/opt/demo-chat/node-9`, so the stable directory
defaults to `/opt/demo-chat/vault`.
The vault password target must also stay outside every Git work tree.

## Make a bundle for one host

Choose one directory outside every Git work tree. This example uses a
directory in your home:

```bash
bundle="$HOME/chat-node-9-vault"
umask 077
mkdir -p "$bundle"
openssl rand -hex 32 > "$bundle/vault-pass"
ansible-vault create --vault-id "shared@$bundle/vault-pass" "$bundle/shared.yml"
ansible-vault create --vault-id owner@prompt "$bundle/owner.yml"
```

Enter these YAML names in the shared vault editor:
`service_password`, `admin_password`, `embedding_api_key`,
`sigma_password`, `theta_password`, and `phi_password`.
Enter `mario_password` alone in the owner vault editor.
The installer needs all three files:

```bash
node-9/install --vault-source "$bundle"
```

The bundle holds exactly `shared.yml`, `owner.yml`, and `vault-pass`.
The install refuses a symlink at any of the three files.

The owner playbook prompts for its password on each run. The bundle does
not hold that password.

## Read and edit

A check that prints no value:

```bash
ansible-vault view --vault-id "shared@${XDG_CONFIG_HOME:-$HOME/.config}/chat-node-9/vault-pass" \
    /opt/demo-chat/vault/shared.yml > /dev/null && echo "opens"
```

Edit in place:

```bash
ansible-vault edit --vault-id "shared@${XDG_CONFIG_HOME:-$HOME/.config}/chat-node-9/vault-pass" \
    /opt/demo-chat/vault/shared.yml
```

The installed playbooks read `vault/shared.yml` and `vault/owner.yml`
through the links in the installed `ansible/` directory. The wrapper
`node-9/bin/setup-secrets` runs the shared playbook. The wrapper with
`--owner` runs the owner playbook from the owner's terminal.

## After the install

An install with `--vault-source` copies ciphertext alone. The marker records
`plaintext: pending`. Finish with:

```bash
/opt/demo-chat/node-9/bin/setup-secrets
/opt/demo-chat/node-9/bin/setup-secrets --owner
```

The first command writes the six shared values and the three agent password
files. The second command prompts for the owner vault password and writes
`CHAT_USER_MARIO_PASSWORD`.

## History

Git tracked `shared.yml` until 2026-10-25. The earlier artifact remains in
Git history. It is not a current source. An installed home may hold a copy
inside `ansible/vault/`. The installer treats a copy that matches the last
tracked artifact as inert and replaces it with the code. A different file
stops the run and is never removed.
