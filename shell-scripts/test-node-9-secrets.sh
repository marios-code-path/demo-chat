#!/usr/bin/env bash
# Offline checks for node-9/bin/setup-secrets and the Ansible Vault source.
# No container starts. See CHAT-jmnjvupr.
#
# The tests build a fixture Vault in a temporary Ansible directory. They
# never read the repository Vault, and they never write to node-9/secrets/.
set -uo pipefail

cd "$(dirname "$0")/.."
repo_root="$PWD"
setup="$repo_root/node-9/bin/setup-secrets"

work="$(mktemp -d)"
if [[ "${KEEP_WORK:-0}" == '1' ]]; then
    printf 'work=%s\n' "$work"
else
    trap 'rm -rf "$work"' EXIT
fi

config="$work/config"
mkdir -p "$config/chat-node-9"
printf 'fixture-vault-password\n' > "$config/chat-node-9/vault-pass"
chmod 0600 "$config/chat-node-9/vault-pass"

owner_pass="$work/owner-pass"
printf 'fixture-owner-vault-password\n' > "$owner_pass"
chmod 0600 "$owner_pass"

ansible_dir="$work/ansible"
mkdir -p "$ansible_dir/vault"
if [[ -d "$repo_root/node-9/ansible" ]]; then
    cp "$repo_root/node-9/ansible/secrets-shared.yml" "$ansible_dir/" 2>/dev/null
    cp "$repo_root/node-9/ansible/secrets-owner.yml" "$ansible_dir/" 2>/dev/null
    cp "$repo_root/node-9/ansible/ansible.cfg" "$ansible_dir/" 2>/dev/null
fi

cat > "$ansible_dir/vault/shared.yml" <<'EOF'
service_password: fixture-service-password
admin_password: fixture-admin-password
embedding_api_key: fixture-embedding-key
sigma_password: fixture-sigma-password
theta_password: fixture-theta-password
phi_password: fixture-phi-password
EOF
ansible-vault encrypt --vault-id "shared@$config/chat-node-9/vault-pass" \
    "$ansible_dir/vault/shared.yml" > /dev/null 2>&1

cat > "$ansible_dir/vault/owner.yml" <<'EOF'
mario_password: fixture-mario-password
EOF
ansible-vault encrypt --vault-id "owner@$owner_pass" \
    "$ansible_dir/vault/owner.yml" > /dev/null 2>&1

failures=0
checks=0
ok() { checks=$((checks + 1)); printf 'ok   %s\n' "$1"; }
bad() { checks=$((checks + 1)); failures=$((failures + 1)); printf 'FAIL %s: %s\n' "$1" "$2"; }

# mode_of PATH  Print the octal mode of PATH.
#
# The two stat commands take different flags, and one order cannot serve both.
# GNU stat reads -f as --file-system, and it then prints the file system of the
# path and returns 0. So a Darwin-first order never reaches its fallback on
# Linux, and the caller compares a file system report with a mode. The same
# switch stands in node-9/bin/register-import. See CHAT-tqsteufr.
mode_of() {
    case "$(uname -s)" in
        Darwin) stat -f '%Lp' "$1" ;;
        *) stat -c '%a' "$1" ;;
    esac
}

# run_setup NAME [ARGS...]  Run setup-secrets against the fixture Vault.
# The secrets file and directory land in $work. Status lands in $status_of.
run_setup() {
    local name="$1"; shift
    ( cd "$repo_root" \
      && XDG_CONFIG_HOME="${RUN_CONFIG:-$config}" \
         NODE9_ALLOW_SOURCE_TREE=1 \
         NODE9_ANSIBLE_DIR="$ansible_dir" \
         NODE9_SECRETS_FILE="$work/$name.env" \
         NODE9_SECRETS_DIR="$work/$name.d" \
         PATH="${RUN_PATH:-$PATH}" \
         "$setup" "$@" ) > "$work/$name.out" 2> "$work/$name.err" < /dev/null
    status_of=$?
}

has_key() { grep -qx "$1=$2" "$3"; }

# -- The shared run -----------------------------------------------------

run_setup shared
if [[ "$status_of" -eq 0 ]]; then ok 'shared_run_succeeds'; else bad 'shared_run_succeeds' "status=$status_of err=$(head -1 "$work/shared.err")"; fi

if has_key CHAT_SERVICE_PASSWORD fixture-service-password "$work/shared.env" \
   && has_key CHAT_ADMIN_PASSWORD fixture-admin-password "$work/shared.env" \
   && has_key CHAT_EMBEDDING_OPENAI_API_KEY fixture-embedding-key "$work/shared.env" \
   && has_key CHAT_USER_SIGMA_PASSWORD fixture-sigma-password "$work/shared.env" \
   && has_key CHAT_USER_THETA_PASSWORD fixture-theta-password "$work/shared.env" \
   && has_key CHAT_USER_PHI_PASSWORD fixture-phi-password "$work/shared.env"; then
    ok 'shared_run_writes_every_key'
else
    bad 'shared_run_writes_every_key' "file=$(tr '\n' ' ' 2>/dev/null < "$work/shared.env")"
fi

if [[ "$(mode_of "$work/shared.env")" == '600' ]] && [[ "$(mode_of "$work/shared.d")" == '700' ]]; then
    ok 'shared_run_sets_modes'
else
    bad 'shared_run_sets_modes' "env=$(mode_of "$work/shared.env" 2>/dev/null) dir=$(mode_of "$work/shared.d" 2>/dev/null)"
fi

if [[ "$(mode_of "$work/shared.d/Sigma.password")" == '600' ]] \
   && [[ "$(cat "$work/shared.d/Sigma.password")" == 'fixture-sigma-password' ]] \
   && [[ "$(cat "$work/shared.d/Theta.password")" == 'fixture-theta-password' ]] \
   && [[ "$(cat "$work/shared.d/Phi.password")" == 'fixture-phi-password' ]]; then
    ok 'shared_run_writes_agent_files'
else
    bad 'shared_run_writes_agent_files' "sigma=$(cat "$work/shared.d/Sigma.password" 2>/dev/null)"
fi

if [[ ! -e "$work/shared.d/Mario.password" ]] \
   && ! grep -q '^CHAT_USER_MARIO_PASSWORD=' "$work/shared.env" 2>/dev/null; then
    ok 'shared_run_writes_no_mario_value'
else
    bad 'shared_run_writes_no_mario_value' 'the shared run wrote an owner value'
fi

if ! grep -q 'fixture-' "$work/shared.out" "$work/shared.err" 2>/dev/null; then
    ok 'shared_run_prints_no_secret'
else
    bad 'shared_run_prints_no_secret' 'a fixture value appeared in the output'
fi

# -- Repeated setup keeps every byte ------------------------------------

before_env="$(shasum -a 256 "$work/shared.env" | awk '{print $1}')"
before_sigma="$(shasum -a 256 "$work/shared.d/Sigma.password" | awk '{print $1}')"
run_setup shared
after_env="$(shasum -a 256 "$work/shared.env" | awk '{print $1}')"
after_sigma="$(shasum -a 256 "$work/shared.d/Sigma.password" | awk '{print $1}')"
if [[ "$status_of" -eq 0 ]] && [[ "$before_env" == "$after_env" ]] && [[ "$before_sigma" == "$after_sigma" ]]; then
    ok 'repeat_preserves_bytes'
else
    bad 'repeat_preserves_bytes' "status=$status_of env=$before_env/$after_env sigma=$before_sigma/$after_sigma"
fi

# -- The default run never falls back ----------------------------------

mkdir -p "$work/nopass"
RUN_CONFIG="$work/nopass" run_setup novault
if [[ "$status_of" -ne 0 ]] && [[ ! -e "$work/novault.env" ]] \
   && grep -q -- '--local' "$work/novault.err"; then
    ok 'missing_vault_password_stops'
else
    bad 'missing_vault_password_stops' "status=$status_of file=$([[ -e "$work/novault.env" ]] && echo yes || echo no) err=$(head -1 "$work/novault.err")"
fi

# A path of this host can hold ansible-playbook in /usr/bin, and node3 does.
# So the case builds one directory of the commands that the script needs, and
# it leaves ansible-playbook out. See CHAT-tqsteufr.
noansible_bin="$work/noansible-bin"
mkdir -p "$noansible_bin"
for tool in bash sh dirname basename uname mkdir chmod mv cp rm sed tr cat \
            grep head tail ls env printf openssl git docker; do
    tool_path="$(command -v "$tool" 2> /dev/null)" || continue
    ln -sf "$tool_path" "$noansible_bin/$tool"
done
RUN_PATH="$noansible_bin" run_setup noansible
if [[ "$status_of" -ne 0 ]] && [[ ! -e "$work/noansible.env" ]] \
   && grep -q -- '--local' "$work/noansible.err"; then
    ok 'missing_ansible_stops'
else
    bad 'missing_ansible_stops' "status=$status_of file=$([[ -e "$work/noansible.env" ]] && echo yes || echo no) err=$(head -1 "$work/noansible.err")"
fi

# -- The local mode is explicit ----------------------------------------

printf 'CHAT_USER_MARIO_PASSWORD=fixture-mario-password\n' > "$work/local.env"
run_setup local --local
if [[ "$status_of" -eq 0 ]] \
   && grep -qx 'CHAT_USER_MARIO_PASSWORD=fixture-mario-password' "$work/local.env" \
   && grep -q '^CHAT_SERVICE_PASSWORD=' "$work/local.env" \
   && [[ -f "$work/local.d/Theta.password" ]] \
   && [[ "$(grep '^CHAT_USER_THETA_PASSWORD=' "$work/local.env" | cut -d= -f2)" == "$(cat "$work/local.d/Theta.password")" ]]; then
    ok 'local_run_creates_missing_values'
else
    bad 'local_run_creates_missing_values' "status=$status_of err=$(head -1 "$work/local.err")"
fi

run_setup local_ttyless --local
if [[ "$status_of" -ne 0 ]] && [[ ! -e "$work/local_ttyless.env" ]] \
   && [[ "$(wc -l < "$work/local_ttyless.err" | tr -d ' ')" -eq 1 ]]; then
    ok 'local_run_without_terminal_fails'
else
    bad 'local_run_without_terminal_fails' "status=$status_of file=$([[ -e "$work/local_ttyless.env" ]] && echo yes || echo no) err=$(head -1 "$work/local_ttyless.err")"
fi

# -- The owner playbook -------------------------------------------------

( cd "$repo_root" \
  && NODE9_SECRETS_FILE="$work/owner.env" NODE9_SECRETS_DIR="$work/owner.d" \
     ansible-playbook "$ansible_dir/secrets-owner.yml" \
       --vault-id "owner@$owner_pass" -i localhost, -c local ) \
  > "$work/owner-playbook.out" 2> "$work/owner-playbook.err"
owner_status=$?
if [[ "$owner_status" -eq 0 ]] \
   && has_key CHAT_USER_MARIO_PASSWORD fixture-mario-password "$work/owner.env" \
   && [[ ! -e "$work/owner.d/Mario.password" ]]; then
    ok 'owner_playbook_writes_mario_value'
else
    bad 'owner_playbook_writes_mario_value' "status=$owner_status err=$(head -1 "$work/owner-playbook.err")"
fi

# The owner run asks for the owner Vault password. It never stores it.
stub_bin="$work/stub-bin"
mkdir -p "$stub_bin"
cat > "$stub_bin/ansible-playbook" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$@" > "${STUB_ARGS:?}"
exit 0
STUB
chmod +x "$stub_bin/ansible-playbook"
STUB_ARGS="$work/owner-call.args" RUN_PATH="$stub_bin:$PATH" run_setup owner --owner
if [[ "$status_of" -eq 0 ]] \
   && grep -qx 'owner@prompt' "$work/owner-call.args" \
   && ! grep -q 'fixture-' "$work/owner-call.args" \
   && [[ ! -e "$work/owner.d/Mario.password" ]]; then
    ok 'owner_run_prompts_and_writes_no_file'
else
    bad 'owner_run_prompts_and_writes_no_file' "status=$status_of args=$(tr '\n' ' ' 2>/dev/null < "$work/owner-call.args")"
fi

# -- A symbolic link at a secret path is refused ------------------------

mkdir -p "$work/link.d"
target="$work/escaped-Sigma"
: > "$target"
ln -s "$target" "$work/link.d/Sigma.password"
run_setup link
if [[ "$status_of" -ne 0 ]] && [[ ! -s "$target" ]]; then
    ok 'symlink_is_refused'
else
    bad 'symlink_is_refused' "status=$status_of target_bytes=$(wc -c 2>/dev/null < "$target")"
fi

# -- Summary ------------------------------------------------------------

printf '\n%d checks, %d failures\n' "$checks" "$failures"
[[ "$failures" -eq 0 ]]
