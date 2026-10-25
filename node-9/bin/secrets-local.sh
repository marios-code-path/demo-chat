# Shared local secret generation for node 9. Source this file.
# See CHAT-cnwkjsrp.
#
# setup-secrets and node-9/install both call these functions, so the local
# path has one implementation. The functions take explicit paths.

# node9_refuse_symlink PATH  Stop when PATH is a symbolic link.
node9_refuse_symlink() {
    if [[ -L "$1" ]]; then
        printf 'node-9: %s is a symbolic link. Secret paths must be plain files.\n' "$1" >&2
        exit 1
    fi
}

# node9_write_local TARGET SECRETS_DIR  Generate every missing value without
# Ansible. The owner password needs a terminal.
node9_write_local() {
    local target="$1" secrets_dir="$2" tmp key name upper file mario
    node9_refuse_symlink "$target"
    node9_refuse_symlink "$secrets_dir"
    tmp="$(mktemp "${target}.XXXXXX")" || return 1
    # The trap runs in the caller's shell, after this function returns. Put
    # the path in the trap text, or the unset local name reaches the trap.
    trap "rm -f $(printf '%q' "$tmp")" EXIT
    if [[ -f "$target" ]]; then cp "$target" "$tmp"; fi
    chmod 0600 "$tmp"

    for key in CHAT_SERVICE_PASSWORD CHAT_ADMIN_PASSWORD \
               CHAT_EMBEDDING_OPENAI_API_KEY CHAT_USER_SIGMA_PASSWORD \
               CHAT_USER_THETA_PASSWORD CHAT_USER_PHI_PASSWORD; do
        if ! grep -q "^$key=" "$tmp" 2>/dev/null; then
            printf '%s=%s\n' "$key" "$(openssl rand -hex 16)" >> "$tmp"
        fi
    done

    if ! grep -q '^CHAT_USER_MARIO_PASSWORD=' "$tmp" 2>/dev/null; then
        if [[ ! -t 0 ]]; then
            printf 'node-9: %s needs CHAT_USER_MARIO_PASSWORD. Run --local from a terminal, or ask the owner.\n' "$target" >&2
            return 1
        fi
        printf 'Mario password: ' >&2
        IFS= read -rs mario
        printf '\n' >&2
        if [[ -z "$mario" ]]; then
            printf 'node-9: an empty owner password is not allowed.\n' >&2
            return 1
        fi
        printf 'CHAT_USER_MARIO_PASSWORD=%s\n' "$mario" >> "$tmp"
    fi

    mkdir -p "$secrets_dir" && chmod 0700 "$secrets_dir"
    for name in Sigma Theta Phi; do
        upper="$(printf '%s' "$name" | tr '[:lower:]' '[:upper:]')"
        file="$secrets_dir/$name.password"
        node9_refuse_symlink "$file"
        if [[ ! -e "$file" ]]; then
            sed -n "s/^CHAT_USER_${upper}_PASSWORD=//p" "$tmp" > "$file"
            chmod 0600 "$file"
        fi
    done

    mv "$tmp" "$target"
    trap - EXIT
    chmod 0600 "$target"
    printf 'node-9: wrote %s and %s/ without Ansible.\n' "$target" "$secrets_dir"
}
