# Stage 4 Migration Preflight

Measured on 2026-10-21. No import message or archive pointer was posted.

## Frozen source

The source commit is `2d75f0cdd09115da422cdc0a693f0227f31a6ef5` (H).
It holds `forward-register.md` and six dated archive files.
The seven files hold 117 sections and 317,268 bytes.
Each working-tree file matches its blob at H.
Each file is valid UTF-8 and holds no NUL byte.

`bash shell-scripts/test-register-import.sh` reconstructed all seven blobs.
It joined the sections in file order and used `cmp` for each source.
Every comparison passed.

`node-9/bin/import-register prepare` created 131 stable requests.
They cover 117 sections, seven headers, and seven pointers.
It made no shell call.

## Credential restore

The main checkout had `node-9/secrets.env` but no `node-9/secrets/` directory.
The first Sigma read returned `77` because the password file was absent.

The approved shared Vault run returned `0`.
It created the Sigma, Theta, and Phi files with mode `0600`.
It added their three missing keys to the main checkout's `secrets.env`.
The playbook keeps each existing key unchanged by design.
This run did not inspect or record any password value.

## Node 9 stop

The restored Sigma file has not passed a live login check.
The first read after restore stopped before Java because the CLI classpath was absent.
`just build-shell-cli` then passed in the stage 4 worktree.
The next read returned `69` with `Connection refused`.

Docker showed the node-9 core as `Exited (0)` at `2026-10-21T20:17:16Z`.
Its Cassandra, Consul, and Redis containers were up.
A direct restart failed before Java started.
The core still mounts `userinit.yml` from the removed stage 3 worktree.

The main checkout has no `CHAT_USER_MARIO_PASSWORD` line.
Its `node-9/ansible/vault/owner.yml` is absent.
The owner must create that vault with the original Mario password.
The owner must run `node-9/bin/setup-secrets --owner`.
Then `node-9/bin/start` can recreate the core from the main checkout.

Do not use `setup-secrets --local` for this recovery.
It would make new passwords that can differ from the stored credentials.

## Next stop gate

Require node-9 health on 6891 and a Sigma read with status `0`.
Require 15 records, one approved duplicate-pointer warning, and no denied warning.
Check the other users before the import writes any archive content.
