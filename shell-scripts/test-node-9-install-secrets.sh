#!/usr/bin/env bash
# Offline checks for the node-9/install secrets choice. No container starts.
#
# The repository holds no vault ciphertext, so the legacy tracked artifact
# case uses the NODE9_INSTALL_LEGACY_SHA256 and NODE9_INSTALL_LEGACY_SIZE
# test hooks. No case reads a live secret. See CHAT-cnwkjsrp.
set -uo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/test-support/clear-legacy-inputs.sh" || exit $?

cd "$(dirname "$0")/.."
repo_root="$PWD"
install="$repo_root/node-9/install"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

failures=0
checks=0
ok() { checks=$((checks + 1)); printf 'ok   %s\n' "$1"; }
bad() { checks=$((checks + 1)); failures=$((failures + 1)); printf 'FAIL %s: %s\n' "$1" "$2"; }

# has_line FILE LINE  True when FILE holds LINE as a whole line.
has_line() {
    grep -q -x "$2" "$1"
}

mode_of() {
    case "$(uname -s)" in
        Darwin) stat -f '%Lp' "$1" ;;
        *) stat -c '%a' "$1" ;;
    esac
}

sha256_of() {
    case "$(uname -s)" in
        Darwin) shasum -a 256 "$1" | awk '{print $1}' ;;
        *) sha256sum "$1" | awk '{print $1}' ;;
    esac
}

inode_of() {
    ls -i "$1" | awk '{print $1}'
}

# value_of KEY FILE  Print the value of KEY. The caller compares it in memory
# and never prints the answer.
value_of() {
    sed -n "s/^$1=//p" "$2"
}

# new_home NAME  Print a fresh prefix. Each case has its own parent, so the
# stable vault directory never collides between cases.
new_home() {
    mkdir -p "$work/$1"
    printf '%s/node-9' "$work/$1"
}

# run_install CONFIG PREFIX ARGS...  Run the installer with one config home.
# It reads no stdin unless the caller gives it one.
run_install() {
    local config="$1" prefix="$2"
    shift 2
    env XDG_CONFIG_HOME="$config" bash "$install" --skip-cli --prefix "$prefix" "$@"
}

# A complete plaintext state. Every agent file equals its key value.
state="$work/state"
mkdir -p "$state/secrets"
cat > "$state/secrets.env" <<'EOF'
CHAT_SERVICE_PASSWORD=fixture-service-password
CHAT_ADMIN_PASSWORD=fixture-admin-password
CHAT_EMBEDDING_OPENAI_API_KEY=fixture-embedding-key
CHAT_USER_SIGMA_PASSWORD=fixture-sigma-password
CHAT_USER_THETA_PASSWORD=fixture-theta-password
CHAT_USER_PHI_PASSWORD=fixture-phi-password
CHAT_USER_MARIO_PASSWORD=fixture-mario-password
EOF
for name in Sigma Theta Phi; do
    upper="$(printf '%s' "$name" | tr '[:lower:]' '[:upper:]')"
    value_of "CHAT_USER_${upper}_PASSWORD" "$state/secrets.env" \
        > "$state/secrets/$name.password"
    chmod 0600 "$state/secrets/$name.password"
done
chmod 0600 "$state/secrets.env"
chmod 0700 "$state/secrets"

# A complete vault bundle. The installer copies these bytes and never
# decrypts them.
bundle="$work/bundle"
mkdir -p "$bundle"
printf 'fixture-shared-vault-pass\n' > "$bundle/vault-pass"
printf '$ANSIBLE_VAULT;1.2;AES256;shared\nfixture-shared\n' > "$bundle/shared.yml"
printf '$ANSIBLE_VAULT;1.2;AES256;owner\nfixture-owner\n' > "$bundle/owner.yml"

# A second bundle. Only shared.yml differs, so a conflict case names one path.
bundle_b="$work/bundle-b"
mkdir -p "$bundle_b"
cp "$bundle/vault-pass" "$bundle_b/vault-pass"
printf '$ANSIBLE_VAULT;1.2;AES256;shared\nfixture-shared-b\n' > "$bundle_b/shared.yml"
cp "$bundle/owner.yml" "$bundle_b/owner.yml"

cfg_a="$work/config-a"
mkdir -p "$cfg_a/chat-node-9"

# -- Choice count -------------------------------------------------------

home="$(new_home no-choice)"
out="$(run_install "$cfg_a" "$home" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'--vault-source'* \
      && "$out" == *'--import-state'* && "$out" == *'--local-secrets'* \
      && "$out" == *'--keep-secrets'* && ! -e "$home" ]]; then
    ok 'install_requires_exactly_one_choice'
else
    bad 'install_requires_exactly_one_choice' "exit $status, output: $out"
fi

home="$(new_home two-choices)"
out="$(run_install "$cfg_a" "$home" --local-secrets --keep-secrets 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'exactly one'* && ! -e "$home" ]]; then
    ok 'install_refuses_two_choices'
else
    bad 'install_refuses_two_choices' "exit $status, output: $out"
fi

home="$(new_home marker-link)"
mkdir -p "$home"
marker_target="$work/marker-target"
printf 'installed-at: fixture\n' > "$marker_target"
ln -s "$marker_target" "$home/.installed"
out="$(run_install "$cfg_a" "$home" --vault-source "$bundle" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'.installed'* \
      && "$(cat "$marker_target")" == 'installed-at: fixture' ]]; then
    ok 'install_refuses_a_marker_symlink'
else
    bad 'install_refuses_a_marker_symlink' "exit $status, output: $out"
fi

# -- Vault source -------------------------------------------------------

home="$(new_home vault-ok)"
out="$(run_install "$cfg_a" "$home" --vault-source "$bundle" 2>&1)"
status=$?
stable_shared="$(dirname "$home")/vault/shared.yml"
stable_owner="$(dirname "$home")/vault/owner.yml"
pass_file="$cfg_a/chat-node-9/vault-pass"
if [[ $status -eq 0 \
      && -f "$stable_shared" && -f "$stable_owner" && -f "$pass_file" \
      && -x "$home/bin/chat-shell-portal" \
      && -x "$home/bin/chat-shell-portal-tunnel" \
      && -x "$home/bin/chat-mcp-stdio" \
      && -x "$home/bin/generate-agent-instructions" \
      && -x "$home/bin/setup-portal" \
      && "$(mode_of "$stable_shared")" == '600' \
      && "$(mode_of "$stable_owner")" == '600' \
      && "$(mode_of "$pass_file")" == '600' \
      && "$(cat "$stable_shared")" == "$(cat "$bundle/shared.yml")" \
      && "$(cat "$stable_owner")" == "$(cat "$bundle/owner.yml")" \
      && "$(cat "$pass_file")" == "$(cat "$bundle/vault-pass")" \
      && -L "$home/ansible/vault/shared.yml" \
      && -L "$home/ansible/vault/owner.yml" \
      && "$(readlink "$home/ansible/vault/shared.yml")" == "$stable_shared" \
      && "$(readlink "$home/ansible/vault/owner.yml")" == "$stable_owner" \
      && ! -e "$home/secrets.env" ]] \
   && has_line "$home/.installed" 'secrets: vault-source' \
   && has_line "$home/.installed" 'plaintext: pending' \
   && [[ "$out" == *"$home/bin/setup-secrets"* \
      && "$out" == *"$home/bin/setup-secrets --owner"* ]]; then
    ok 'vault_source_installs_ciphertext_and_links'
else
    bad 'vault_source_installs_ciphertext_and_links' "exit $status, output: $out"
fi

home="$(new_home vault-incomplete)"
mkdir -p "$work/incomplete"
cp "$bundle/vault-pass" "$work/incomplete/"
cp "$bundle/shared.yml" "$work/incomplete/"
out="$(run_install "$cfg_a" "$home" --vault-source "$work/incomplete" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'owner.yml'* && ! -e "$home" ]]; then
    ok 'vault_source_requires_all_three_files'
else
    bad 'vault_source_requires_all_three_files' "exit $status, output: $out"
fi

home="$(new_home vault-in-git)"
out="$(run_install "$cfg_a" "$home" --vault-source "$repo_root" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'work tree'* && ! -e "$home" ]]; then
    ok 'vault_source_refuses_a_source_inside_git'
else
    bad 'vault_source_refuses_a_source_inside_git' "exit $status, output: $out"
fi

config_git="$work/config-git"
mkdir -p "$config_git"
git -C "$config_git" init -q
home="$(new_home vault-pass-in-git)"
out="$(run_install "$config_git/config" "$home" --vault-source "$bundle" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'work tree'* && ! -e "$home" \
      && ! -e "$config_git/config/chat-node-9/vault-pass" ]]; then
    ok 'vault_source_refuses_a_password_target_inside_git'
else
    bad 'vault_source_refuses_a_password_target_inside_git' "exit $status, output: $out"
fi

source_dir_link="$work/source-dir-link"
ln -s "$bundle" "$source_dir_link"
home="$(new_home vault-dir-link)"
out="$(run_install "$cfg_a" "$home" --vault-source "$source_dir_link" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'symbolic link'* && ! -e "$home" ]]; then
    ok 'vault_source_refuses_a_directory_symlink'
else
    bad 'vault_source_refuses_a_directory_symlink' "exit $status, output: $out"
fi

extra_bundle="$work/extra-bundle"
cp -R "$bundle" "$extra_bundle"
printf 'extra\n' > "$extra_bundle/extra.txt"
home="$(new_home vault-extra-file)"
out="$(run_install "$cfg_a" "$home" --vault-source "$extra_bundle" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'extra.txt'* && ! -e "$home" ]]; then
    ok 'vault_source_refuses_an_extra_file'
else
    bad 'vault_source_refuses_an_extra_file' "exit $status, output: $out"
fi

bundle_link="$work/bundle-link"
mkdir -p "$bundle_link"
cp "$bundle/vault-pass" "$bundle_link/"
cp "$bundle/owner.yml" "$bundle_link/"
ln -s "$bundle/shared.yml" "$bundle_link/shared.yml"
home="$(new_home vault-source-link)"
out="$(run_install "$cfg_a" "$home" --vault-source "$bundle_link" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'symbolic link'* && ! -e "$home" ]]; then
    ok 'vault_source_refuses_a_source_symlink'
else
    bad 'vault_source_refuses_a_source_symlink' "exit $status, output: $out"
fi

# A link in an earlier home must name its fixed stable vault file.
home="$(new_home vault-foreign-link)"
mkdir -p "$home/ansible/vault"
ln -s "$bundle/shared.yml" "$home/ansible/vault/shared.yml"
printf 'installed-at: fixture\n' > "$home/.installed"
out="$(run_install "$cfg_a" "$home" --vault-source "$bundle" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *"$home/ansible/vault/shared.yml"* \
      && -L "$home/ansible/vault/shared.yml" \
      && ! -e "$(dirname "$home")/vault/shared.yml" ]]; then
    ok 'vault_source_refuses_a_foreign_in_home_link'
else
    bad 'vault_source_refuses_a_foreign_in_home_link' "exit $status, output: $out"
fi

# A broken plaintext link still counts as state. It must stop before copying.
home="$(new_home vault-broken-plaintext-link)"
mkdir -p "$home"
ln -s "$work/absent-secret" "$home/secrets.env"
printf 'installed-at: fixture\n' > "$home/.installed"
out="$(run_install "$cfg_a" "$home" --vault-source "$bundle" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'--keep-secrets'* \
      && -L "$home/secrets.env" \
      && ! -e "$(dirname "$home")/vault/shared.yml" ]]; then
    ok 'vault_source_refuses_a_broken_plaintext_link'
else
    bad 'vault_source_refuses_a_broken_plaintext_link' "exit $status, output: $out"
fi

home="$(new_home vault-target-link)"
mkdir -p "$(dirname "$home")/vault"
ln -s "$bundle/shared.yml" "$(dirname "$home")/vault/shared.yml"
out="$(run_install "$cfg_a" "$home" --vault-source "$bundle" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'symbolic link'* && ! -e "$home/.installed" ]]; then
    ok 'vault_source_refuses_a_stable_target_symlink'
else
    bad 'vault_source_refuses_a_stable_target_symlink' "exit $status, output: $out"
fi

# Identical targets keep their bytes and inodes. A different bundle conflicts.
home="$(new_home vault-keep)"
run_install "$cfg_a" "$home" --vault-source "$bundle" >/dev/null 2>&1
stable_shared="$(dirname "$home")/vault/shared.yml"
inode_before="$(inode_of "$stable_shared")"
out="$(run_install "$cfg_a" "$home" --vault-source "$bundle" 2>&1)"
status=$?
if [[ $status -eq 0 && "$out" == *'keep'* \
      && "$(inode_of "$stable_shared")" == "$inode_before" ]]; then
    ok 'vault_source_keeps_an_identical_target'
else
    bad 'vault_source_keeps_an_identical_target' "exit $status, output: $out"
fi
out="$(run_install "$cfg_a" "$home" --vault-source "$bundle_b" 2>&1)"
status=$?
if [[ $status -eq 1 && "$out" == *"$stable_shared"* \
      && "$(cat "$stable_shared")" == "$(cat "$bundle/shared.yml")" ]]; then
    ok 'vault_source_refuses_a_conflicting_target'
else
    bad 'vault_source_refuses_a_conflicting_target' "exit $status, output: $out"
fi

home="$(new_home vault-local-state)"
run_install "$cfg_a" "$home" --import-state "$state" >/dev/null 2>&1
out="$(run_install "$cfg_a" "$home" --vault-source "$bundle" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'--keep-secrets'* ]]; then
    ok 'vault_source_refuses_existing_plaintext'
else
    bad 'vault_source_refuses_existing_plaintext' "exit $status, output: $out"
fi

# -- The legacy tracked artifact ---------------------------------------

legacy_bytes="$work/legacy-shared.yml"
printf '$ANSIBLE_VAULT;1.2;AES256;shared\nlegacy-fixture\n' > "$legacy_bytes"
legacy_sha="$(sha256_of "$legacy_bytes")"
legacy_size="$(wc -c < "$legacy_bytes" | tr -d ' ')"

home="$(new_home node2-shape)"
mkdir -p "$home/ansible/vault"
cp "$legacy_bytes" "$home/ansible/vault/shared.yml"
printf 'installed-at: fixture\ncli: skipped\n' > "$home/.installed"
out="$(env XDG_CONFIG_HOME="$cfg_a" \
    NODE9_INSTALL_LEGACY_SHA256="$legacy_sha" \
    NODE9_INSTALL_LEGACY_SIZE="$legacy_size" \
    bash "$install" --skip-cli --prefix "$home" --vault-source "$bundle" 2>&1)"
status=$?
stable_shared="$(dirname "$home")/vault/shared.yml"
if [[ $status -eq 0 && -L "$home/ansible/vault/shared.yml" \
      && "$(cat "$stable_shared")" == "$(cat "$bundle/shared.yml")" \
      && "$(cat "$stable_shared")" != "$(cat "$legacy_bytes")" ]]; then
    ok 'node2_shape_takes_a_different_vault'
else
    bad 'node2_shape_takes_a_different_vault' "exit $status, output: $out"
fi

home="$(new_home legacy-keep)"
mkdir -p "$home/ansible/vault" "$home/secrets"
cp "$legacy_bytes" "$home/ansible/vault/shared.yml"
cp "$state/secrets.env" "$home/secrets.env"
cp "$state"/secrets/*.password "$home/secrets/"
printf 'installed-at: fixture\ncli: skipped\n' > "$home/.installed"
out="$(env XDG_CONFIG_HOME="$cfg_a" \
    NODE9_INSTALL_LEGACY_SHA256="$legacy_sha" \
    NODE9_INSTALL_LEGACY_SIZE="$legacy_size" \
    bash "$install" --skip-cli --prefix "$home" --keep-secrets 2>&1)"
status=$?
if [[ $status -eq 0 && ! -e "$home/ansible/vault/shared.yml" \
      && "$(cat "$home/secrets.env")" == "$(cat "$state/secrets.env")" ]]; then
    ok 'keep_secrets_drops_the_inert_legacy_file'
else
    bad 'keep_secrets_drops_the_inert_legacy_file' "exit $status, output: $out"
fi

home="$(new_home legacy-altered)"
mkdir -p "$home/ansible/vault"
printf 'altered\n' > "$home/ansible/vault/shared.yml"
printf 'installed-at: fixture\ncli: skipped\n' > "$home/.installed"
out="$(env XDG_CONFIG_HOME="$cfg_a" \
    NODE9_INSTALL_LEGACY_SHA256="$legacy_sha" \
    NODE9_INSTALL_LEGACY_SIZE="$legacy_size" \
    bash "$install" --skip-cli --prefix "$home" --vault-source "$bundle" 2>&1)"
status=$?
if [[ $status -eq 1 && "$out" == *"$home/ansible/vault/shared.yml"* ]]; then
    ok 'vault_source_refuses_an_altered_legacy_file'
else
    bad 'vault_source_refuses_an_altered_legacy_file' "exit $status, output: $out"
fi

home="$(new_home legacy-altered-keep)"
mkdir -p "$home/ansible/vault" "$home/secrets"
printf 'altered\n' > "$home/ansible/vault/shared.yml"
cp "$state/secrets.env" "$home/secrets.env"
cp "$state"/secrets/*.password "$home/secrets/"
printf 'installed-at: fixture\ncli: skipped\n' > "$home/.installed"
out="$(env XDG_CONFIG_HOME="$cfg_a" \
    NODE9_INSTALL_LEGACY_SHA256="$legacy_sha" \
    NODE9_INSTALL_LEGACY_SIZE="$legacy_size" \
    bash "$install" --skip-cli --prefix "$home" --keep-secrets 2>&1)"
status=$?
if [[ $status -eq 1 && "$out" == *"$home/ansible/vault/shared.yml"* ]]; then
    ok 'keep_secrets_refuses_an_altered_legacy_file'
else
    bad 'keep_secrets_refuses_an_altered_legacy_file' "exit $status, output: $out"
fi

home="$(new_home legacy-directory)"
mkdir -p "$home/ansible/vault/shared.yml"
printf 'installed-at: fixture\n' > "$home/.installed"
out="$(run_install "$cfg_a" "$home" --vault-source "$bundle" 2>&1)"
status=$?
if [[ $status -eq 1 && "$out" == *"$home/ansible/vault/shared.yml"* \
      && -d "$home/ansible/vault/shared.yml" ]]; then
    ok 'vault_source_refuses_a_legacy_directory'
else
    bad 'vault_source_refuses_a_legacy_directory' "exit $status, output: $out"
fi

# -- Import state -------------------------------------------------------

home="$(new_home import-ok)"
out="$(run_install "$cfg_a" "$home" --import-state "$state" 2>&1)"
status=$?
if [[ $status -eq 0 && -f "$home/secrets.env" \
      && "$(mode_of "$home/secrets.env")" == '600' \
      && "$(mode_of "$home/secrets/Theta.password")" == '600' \
      && "$(cat "$home/secrets/Theta.password")" \
         == "$(value_of CHAT_USER_THETA_PASSWORD "$home/secrets.env")" ]] \
   && has_line "$home/.installed" 'secrets: import-state' \
   && has_line "$home/.installed" 'plaintext: complete'; then
    ok 'import_state_copies_a_consistent_set'
else
    bad 'import_state_copies_a_consistent_set' "exit $status, output: $out"
fi

# A missing shared vault gets the source instruction even without Ansible.
no_ansible_bin="$work/no-ansible-bin"
mkdir -p "$no_ansible_bin"
ln -s "$(command -v dirname)" "$no_ansible_bin/dirname"
out="$(env PATH="$no_ansible_bin" XDG_CONFIG_HOME="$work/no-vault-config" \
    /bin/bash "$home/bin/setup-secrets" 2>&1)"
status=$?
if [[ $status -eq 1 && "$out" == *'--vault-source'* \
      && "$out" == *'ansible/vault/README.md'* ]]; then
    ok 'setup_secrets_names_missing_vault_without_ansible'
else
    bad 'setup_secrets_names_missing_vault_without_ansible' "exit $status, output: $out"
fi

partial="$work/partial-state"
mkdir -p "$partial/secrets"
grep -v CHAT_USER_MARIO_PASSWORD "$state/secrets.env" > "$partial/secrets.env"
cp "$state"/secrets/*.password "$partial/secrets/"
home="$(new_home import-missing-key)"
out="$(run_install "$cfg_a" "$home" --import-state "$partial" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'CHAT_USER_MARIO_PASSWORD'* && ! -e "$home" ]]; then
    ok 'import_state_requires_seven_keys'
else
    bad 'import_state_requires_seven_keys' "exit $status, output: $out"
fi

conflict="$work/conflict-state"
mkdir -p "$conflict/secrets"
cp "$state/secrets.env" "$conflict/secrets.env"
cp "$state"/secrets/*.password "$conflict/secrets/"
printf 'not-the-key-value\n' > "$conflict/secrets/Phi.password"
home="$(new_home import-agent-conflict)"
out="$(run_install "$cfg_a" "$home" --import-state "$conflict" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'Phi.password'* && ! -e "$home" ]]; then
    ok 'import_state_checks_agent_files_against_keys'
else
    bad 'import_state_checks_agent_files_against_keys' "exit $status, output: $out"
fi

home="$(new_home import-target-conflict)"
run_install "$cfg_a" "$home" --import-state "$state" >/dev/null 2>&1
printf 'changed\n' > "$home/secrets.env"
out="$(run_install "$cfg_a" "$home" --import-state "$state" 2>&1)"
status=$?
if [[ $status -eq 1 && "$out" == *"$home/secrets.env"* \
      && "$(cat "$home/secrets.env")" == 'changed' ]]; then
    ok 'import_state_refuses_a_target_mismatch'
else
    bad 'import_state_refuses_a_target_mismatch' "exit $status, output: $out"
fi

home="$(new_home import-target-partial)"
run_install "$cfg_a" "$home" --import-state "$state" >/dev/null 2>&1
rm -f "$home/secrets/Theta.password"
out="$(run_install "$cfg_a" "$home" --import-state "$state" 2>&1)"
status=$?
if [[ $status -eq 1 && "$out" == *'Theta.password'* ]]; then
    ok 'import_state_refuses_a_partial_target'
else
    bad 'import_state_refuses_a_partial_target' "exit $status, output: $out"
fi

home="$(new_home import-target-broken-link)"
mkdir -p "$home"
ln -s "$work/absent-import-target" "$home/secrets.env"
printf 'installed-at: fixture\n' > "$home/.installed"
out="$(run_install "$cfg_a" "$home" --import-state "$state" 2>&1)"
status=$?
if [[ $status -eq 1 && "$out" == *"$home/secrets.env"* \
      && -L "$home/secrets.env" && ! -e "$work/absent-import-target" ]]; then
    ok 'import_state_refuses_a_broken_target_link'
else
    bad 'import_state_refuses_a_broken_target_link' "exit $status, output: $out"
fi

# -- Keep secrets -------------------------------------------------------

home="$(new_home keep-fresh)"
out="$(run_install "$cfg_a" "$home" --keep-secrets 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'marker'* && ! -e "$home" ]]; then
    ok 'keep_secrets_refuses_a_fresh_home'
else
    bad 'keep_secrets_refuses_a_fresh_home' "exit $status, output: $out"
fi

home="$(new_home keep-ok)"
run_install "$cfg_a" "$home" --import-state "$state" >/dev/null 2>&1
before="$(sha256_of "$home/secrets.env")"
out="$(run_install "$cfg_a" "$home" --keep-secrets 2>&1)"
status=$?
if [[ $status -eq 0 && "$(sha256_of "$home/secrets.env")" == "$before" ]] \
   && has_line "$home/.installed" 'secrets: kept' \
   && has_line "$home/.installed" 'plaintext: complete'; then
    ok 'keep_secrets_preserves_complete_plaintext'
else
    bad 'keep_secrets_preserves_complete_plaintext' "exit $status, output: $out"
fi

home="$(new_home keep-partial)"
run_install "$cfg_a" "$home" --import-state "$state" >/dev/null 2>&1
rm -f "$home/secrets/Theta.password"
out="$(run_install "$cfg_a" "$home" --keep-secrets 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'Theta.password'* ]]; then
    ok 'keep_secrets_refuses_partial_state'
else
    bad 'keep_secrets_refuses_partial_state' "exit $status, output: $out"
fi

# -- Local secrets ------------------------------------------------------

home="$(new_home local-no-tty)"
out="$(run_install "$cfg_a" "$home" --local-secrets < /dev/null 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'terminal'* && ! -e "$home" ]]; then
    ok 'local_secrets_requires_a_terminal'
else
    bad 'local_secrets_requires_a_terminal' "exit $status, output: $out"
fi

home="$(new_home local-existing)"
run_install "$cfg_a" "$home" --import-state "$state" >/dev/null 2>&1
out="$(run_install "$cfg_a" "$home" --local-secrets < /dev/null 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'--keep-secrets'* ]]; then
    ok 'local_secrets_refuses_existing_plaintext'
else
    bad 'local_secrets_refuses_existing_plaintext' "exit $status, output: $out"
fi

# -- Dry run ------------------------------------------------------------

home="$(new_home dry-vault)"
out="$(run_install "$cfg_a" "$home" --dry-run --vault-source "$bundle" 2>&1)"
status=$?
if [[ $status -eq 0 && ! -e "$home" \
      && "$out" == *'choice: vault-source'* \
      && "$out" == *"$bundle/shared.yml"* \
      && "$out" == *"$(dirname "$home")/vault/shared.yml"* \
      && "$out" == *'plaintext: pending'* \
      && "$out" != *'fixture-shared'* && "$out" != *'fixture-owner'* \
      && "$out" != *'fixture-shared-vault-pass'* ]]; then
    ok 'dry_run_reports_paths_and_no_value'
else
    bad 'dry_run_reports_paths_and_no_value' "exit $status, output: $out"
fi

home="$(new_home dry-local)"
out="$(run_install "$cfg_a" "$home" --dry-run --local-secrets < /dev/null 2>&1)"
status=$?
if [[ $status -eq 0 && ! -e "$home" && "$out" == *'choice: local-secrets'* ]]; then
    ok 'dry_run_local_needs_no_terminal'
else
    bad 'dry_run_local_needs_no_terminal' "exit $status, output: $out"
fi

home="$(new_home dry-vault-conflict)"
mkdir -p "$(dirname "$home")/vault"
cp "$bundle_b/shared.yml" "$(dirname "$home")/vault/shared.yml"
out="$(run_install "$cfg_a" "$home" --dry-run --vault-source "$bundle" 2>&1)"
status=$?
if [[ $status -eq 1 && "$out" == *'shared.yml'* && ! -e "$home" ]] \
   && cmp -s "$bundle_b/shared.yml" "$(dirname "$home")/vault/shared.yml"; then
    ok 'dry_run_refuses_a_vault_conflict'
else
    bad 'dry_run_refuses_a_vault_conflict' "exit $status, output: $out"
fi

home="$(new_home dry-import-conflict)"
run_install "$cfg_a" "$home" --import-state "$state" >/dev/null 2>&1
printf 'changed\n' > "$home/secrets.env"
out="$(run_install "$cfg_a" "$home" --dry-run --import-state "$state" 2>&1)"
status=$?
if [[ $status -eq 1 && "$out" == *'secrets.env'* \
      && "$(cat "$home/secrets.env")" == 'changed' ]]; then
    ok 'dry_run_refuses_an_import_conflict'
else
    bad 'dry_run_refuses_an_import_conflict' "exit $status, output: $out"
fi

# -- Reinstall preservation --------------------------------------------

home="$(new_home reinstall-vault)"
run_install "$cfg_a" "$home" --vault-source "$bundle" >/dev/null 2>&1
stable_shared="$(dirname "$home")/vault/shared.yml"
stable_owner="$(dirname "$home")/vault/owner.yml"
shared_inode="$(inode_of "$stable_shared")"
owner_inode="$(inode_of "$stable_owner")"
run_install "$cfg_a" "$home" --vault-source "$bundle" >/dev/null 2>&1
if [[ "$(inode_of "$stable_shared")" == "$shared_inode" \
      && "$(inode_of "$stable_owner")" == "$owner_inode" \
      && -L "$home/ansible/vault/shared.yml" \
      && -L "$home/ansible/vault/owner.yml" ]]; then
    ok 'reinstall_preserves_stable_vaults'
else
    bad 'reinstall_preserves_stable_vaults' 'a stable vault changed'
fi

printf '\n%s checks, %s failures\n' "$checks" "$failures"
(( failures == 0 ))
