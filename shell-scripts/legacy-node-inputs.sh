#!/usr/bin/env bash
# Temporary readers preserve legacy inputs during the configuration transition.

chat_legacy_pairs() {
    cat <<'EOF'
NODE9_PROJECT NODE_PROJECT
NODE9_VECTOR_PERSIST NODE_VECTOR_PERSIST
NODE9_RSOCKET_PORT NODE_RSOCKET_PORT
NODE9_MGMT_PORT NODE_MGMT_PORT
NODE9_REGISTRY_PORT NODE_REGISTRY_PORT
NODE9_IMAGE_REPO NODE_IMAGE_REPO
NODE9_HOME CHAT_DEPLOYMENT_HOME
NODE_9_BIN CHAT_DEPLOYMENT_BIN
NODE9_SECRETS_FILE CHAT_DEPLOYMENT_SECRETS_FILE
NODE9_SECRETS_DIR CHAT_DEPLOYMENT_SECRETS_DIR
NODE9_ANSIBLE_DIR CHAT_DEPLOYMENT_ANSIBLE_DIR
NODE9_JAVA CHAT_BUILD_JAVA
NODE9_MVN CHAT_BUILD_MVN
NODE9_JAVA_LIMIT CHAT_JAVA_PROBE_LIMIT_S
NODE9_ALLOW_SOURCE_TREE CHAT_ALLOW_SOURCE_TREE
NODE9_CHAT_BUILD CHAT_BUILD_COMMAND
NODE9_INSTALL_FAIL_LIB_MOVE CHAT_TEST_INSTALL_FAIL_LIB_MOVE
NODE9_INSTALL_LEGACY_SHA256 CHAT_TEST_INSTALL_LEGACY_SHA256
NODE9_INSTALL_LEGACY_SIZE CHAT_TEST_INSTALL_LEGACY_SIZE
NODE_9_REGISTER_TOPIC_NAME CHAT_REGISTER_TOPIC_NAME
NODE_9_REGISTER_TOPIC_ID CHAT_REGISTER_TOPIC_ID
EOF
}

chat_legacy_node_inputs() {
    local li_old li_new li_old_value li_new_value li_index li_root
    local li_keys=() li_values=()
    if printenv NODE9_DIR > /dev/null; then
        li_root="$NODE9_DIR"
        if [[ $# == 0 || "$li_root" != "$1" ]]; then
            printf 'legacy-node-inputs: the deployment root cannot be overridden.\n' >&2
            return 2
        fi
    fi
    while read -r li_old li_new; do
        if printenv "$li_old" > /dev/null; then
            li_old_value="${!li_old}"
            if printenv "$li_new" > /dev/null; then
                li_new_value="${!li_new}"
                if [[ "$li_old_value" != "$li_new_value" ]]; then
                    printf 'legacy-node-inputs: %s conflicts with %s.\n' "$li_old" "$li_new" >&2
                    return 2
                fi
            else
                li_keys+=("$li_new")
                li_values+=("$li_old_value")
            fi
        fi
    done < <(chat_legacy_pairs)
    for ((li_index=0; li_index < ${#li_keys[@]}; li_index++)); do
        export "${li_keys[$li_index]}=${li_values[$li_index]}"
    done
}

chat_legacy_profile_translate() {
    local li_path="$1" li_line li_key li_value li_old li_new li_index li_found li_original
    local li_keys=() li_values=() li_originals=() li_version=false li_legacy=false
    [[ "$li_path" == /* && -f "$li_path" && -r "$li_path" ]] \
        || { chat_profile_error 'select a readable absolute profile file.'; return 2; }
    LC_ALL=C tr -d '\000' < "$li_path" | cmp -s "$li_path" - \
        || { chat_profile_error 'the legacy profile contains a NUL byte or cannot be read.'; return 2; }
    while IFS= read -r li_line || [[ -n "$li_line" ]]; do
        case "$li_line" in ''|\#*) continue ;; esac
        [[ "$li_line" == *=* ]] \
            || { chat_profile_error 'a legacy profile record is not KEY=value.'; return 2; }
        li_original="${li_line%%=*}"
        li_key="$li_original"
        li_value="${li_line#*=}"
        while read -r li_old li_new; do
            if [[ "$li_key" == "$li_old" ]]; then li_key="$li_new"; li_legacy=true; break; fi
        done < <(chat_legacy_pairs)
        chat_profile_key "$li_key" \
            || { chat_profile_error 'the legacy profile contains an unknown key.'; return 2; }
        chat_profile_value "$li_key" "$li_value" \
            || { chat_profile_error "the legacy profile has an invalid $li_key value."; return 2; }
        li_found=false
        for ((li_index=0; li_index < ${#li_keys[@]}; li_index++)); do
            if [[ "${li_keys[$li_index]}" == "$li_key" ]]; then
                if [[ "${li_originals[$li_index]}" == "$li_original" || "${li_values[$li_index]}" != "$li_value" ]]; then
                    chat_profile_error "the legacy profile repeats or conflicts on $li_key."
                    return 2
                fi
                li_found=true
            fi
        done
        if [[ "$li_found" == false ]]; then
            li_keys+=("$li_key")
            li_values+=("$li_value")
            li_originals+=("$li_original")
        fi
        [[ "$li_key" != NODE_PROFILE_VERSION ]] || li_version=true
    done < "$li_path"
    if [[ "$li_version" != true ]]; then
        [[ "$li_legacy" == true ]] \
            || { chat_profile_error 'a neutral profile needs NODE_PROFILE_VERSION=1.'; return 2; }
        printf 'NODE_PROFILE_VERSION=1\n'
    fi
    for ((li_index=0; li_index < ${#li_keys[@]}; li_index++)); do
        printf '%s=%s\n' "${li_keys[$li_index]}" "${li_values[$li_index]}"
    done
}

chat_legacy_node_profile_load() {
    local li_original_profile="${CHAT_NODE_PROFILE:-}" li_stage li_status=0 li_here
    li_here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
    . "$li_here/node-profile.sh"
    li_stage=$(mktemp "${TMPDIR:-/tmp}/chat-profile.XXXXXX") || return 2
    if chat_legacy_profile_translate "$li_original_profile" > "$li_stage"; then
        export CHAT_NODE_PROFILE="$li_stage"
        chat_node_profile_load "$@" || li_status=$?
        export CHAT_NODE_PROFILE="$li_original_profile"
    else
        li_status=$?
    fi
    rm -f "$li_stage"
    return "$li_status"
}

# Keep old entry-point defaults here until deployment adapters replace them.
chat_legacy_stage_inputs() {
    if ! printenv CHAT_NODE_PROFILE > /dev/null; then
        export NODE_KEY_TYPE="${NODE_KEY_TYPE-uuid}"
        export NODE_ID="${NODE_ID-9}"
        export CORE_PORT="${CORE_PORT-6890}"
        export CORE_MGMT_PORT="${CORE_MGMT_PORT-6891}"
    fi
}

chat_legacy_shell_runtime() {
    local li_options="$1"
    if ! grep -q '^# core-port ' "$li_options" \
        && grep -qx '# node-id 9' "$li_options" \
        && grep -qx '# key-type uuid' "$li_options"; then
        export CORE_PORT="${CORE_PORT-${NODE_RSOCKET_PORT-6890}}"
        export CORE_MGMT_PORT="${CORE_MGMT_PORT-${NODE_MGMT_PORT-6891}}"
    fi
}

chat_legacy_portal_inputs() {
    if [[ "${NODE_9_BIN+x}" && ! "${CHAT_NODE_PROFILE+x}" ]]; then
        export NODE_RSOCKET_PORT="${NODE_RSOCKET_PORT-6890}"
        export NODE_MGMT_PORT="${NODE_MGMT_PORT-6891}"
        export NODE_REGISTRY_PORT="${NODE_REGISTRY_PORT-6892}"
        export NODE_ID="${NODE_ID-9}" NODE_KEY_TYPE="${NODE_KEY_TYPE-uuid}"
        export CHAT_SHELL_USER="${CHAT_SHELL_USER-Sigma}"
    fi
    if [[ ! "${CHAT_DEPLOYMENT_HOME+x}" && -n "${CHAT_DEPLOYMENT_BIN:-}" ]]; then
        export CHAT_DEPLOYMENT_HOME="$(dirname "$CHAT_DEPLOYMENT_BIN")"
    fi
}

chat_legacy_mcp_config() {
    local li_register="$1"
    shift
    NODE9_ALLOW_SOURCE_TREE=1 CHAT_ALLOW_SOURCE_TREE=1 "$li_register" config \
        --agent "$1" --project "$2" --register "$3"
}
