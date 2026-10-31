#!/usr/bin/env bash
# Read profile records as data. This library executes no profile content.

chat_profile_error() {
    printf 'node-profile: %s\n' "$1" >&2
    return 2
}

chat_profile_key() {
    case "$1" in
        NODE_PROFILE_VERSION|NODE_PROJECT|NODE_NETWORK|NODE_ID|NODE_KEY_TYPE|\
        NODE_RSOCKET_PORT|NODE_MGMT_PORT|NODE_REGISTRY_PORT|NODE_IMAGE_REPO|\
        CORE_IMAGE|NODE_VECTOR_PERSIST|CASSANDRA_VERSION|REDIS_VERSION|CONSUL_VERSION|\
        CHAT_EMBEDDING_OPENAI_BASE_URL|CHAT_EMBEDDING_OPENAI_MODEL|\
        CHAT_EMBEDDING_IDENTITY|VECTOR_OWNER) return 0 ;;
        *) return 2 ;;
    esac
}

chat_profile_value() {
    local cp_key="$1" cp_value="$2" cp_number
    local LC_ALL=C
    [[ "$cp_value" != *[[:cntrl:]]* ]] || return 2
    case "$cp_key" in
        NODE_PROFILE_VERSION) [[ "$cp_value" == 1 ]] ;;
        NODE_ID|NODE_RSOCKET_PORT|NODE_MGMT_PORT|NODE_REGISTRY_PORT)
            [[ "$cp_value" =~ ^[0-9]+$ ]] || return 2
            cp_number="${cp_value#"${cp_value%%[!0]*}"}"
            cp_number="${cp_number:-0}"
            if [[ "$cp_key" == NODE_ID ]]; then
                (( ${#cp_number} <= 4 )) && (( cp_number <= 1023 ))
            else
                (( ${#cp_number} <= 5 )) && (( cp_number >= 1 && cp_number <= 65535 ))
            fi ;;
        NODE_KEY_TYPE) [[ "$cp_value" == uuid || "$cp_value" == long ]] ;;
        NODE_VECTOR_PERSIST) [[ "$cp_value" == on || "$cp_value" == off ]] ;;
        NODE_PROJECT) [[ "$cp_value" =~ ^[a-z0-9][a-z0-9_-]*$ ]] ;;
        NODE_NETWORK) [[ "$cp_value" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] ;;
        NODE_IMAGE_REPO|CORE_IMAGE|CASSANDRA_VERSION|REDIS_VERSION|CONSUL_VERSION)
            [[ -n "$cp_value" && "$cp_value" != *[[:space:]]* ]] ;;
        VECTOR_OWNER) [[ "$cp_value" =~ ^[0-9]+:[0-9]+$ ]] ;;
        *) return 0 ;;
    esac
}

chat_node_profile_load() {
    local cp_path="${CHAT_NODE_PROFILE:-}" cp_line cp_key cp_value cp_index cp_other cp_found cp_version=false
    local cp_keys=() cp_values=() cp_effective_keys=() cp_effective_values=()
    local cp_all='NODE_PROFILE_VERSION NODE_PROJECT NODE_NETWORK NODE_ID NODE_KEY_TYPE NODE_RSOCKET_PORT NODE_MGMT_PORT NODE_REGISTRY_PORT NODE_IMAGE_REPO CORE_IMAGE NODE_VECTOR_PERSIST CASSANDRA_VERSION REDIS_VERSION CONSUL_VERSION CHAT_EMBEDDING_OPENAI_BASE_URL CHAT_EMBEDDING_OPENAI_MODEL CHAT_EMBEDDING_IDENTITY VECTOR_OWNER'
    [[ "$cp_path" == /* && -f "$cp_path" && -r "$cp_path" ]] \
        || { chat_profile_error 'select a readable absolute file with CHAT_NODE_PROFILE.'; return 2; }
    LC_ALL=C tr -d '\000' < "$cp_path" | cmp -s "$cp_path" - \
        || { chat_profile_error 'the profile contains a NUL byte or cannot be read.'; return 2; }
    while IFS= read -r cp_line || [[ -n "$cp_line" ]]; do
        case "$cp_line" in ''|\#*) continue ;; esac
        [[ "$cp_line" == *=* ]] \
            || { chat_profile_error 'a profile record is not KEY=value.'; return 2; }
        cp_key="${cp_line%%=*}"
        cp_value="${cp_line#*=}"
        chat_profile_key "$cp_key" \
            || { chat_profile_error 'the profile contains an unknown key.'; return 2; }
        for cp_other in "${cp_keys[@]+"${cp_keys[@]}"}"; do
            [[ "$cp_other" != "$cp_key" ]] \
                || { chat_profile_error "the profile repeats $cp_key."; return 2; }
        done
        chat_profile_value "$cp_key" "$cp_value" \
            || { chat_profile_error "the profile has an invalid $cp_key value."; return 2; }
        [[ "$cp_key" != NODE_PROFILE_VERSION ]] || cp_version=true
        cp_keys+=("$cp_key")
        cp_values+=("$cp_value")
    done < "$cp_path"
    [[ "$cp_version" == true ]] \
        || { chat_profile_error 'the profile needs NODE_PROFILE_VERSION=1.'; return 2; }
    for cp_key in $cp_all; do
        cp_found=false
        if cp_value=$(printenv "$cp_key"); then
            cp_found=true
        else
            for ((cp_index=0; cp_index < ${#cp_keys[@]}; cp_index++)); do
                if [[ "${cp_keys[$cp_index]}" == "$cp_key" ]]; then
                    cp_value="${cp_values[$cp_index]}"
                    cp_found=true
                    break
                fi
            done
        fi
        [[ "$cp_found" == true ]] || continue
        chat_profile_value "$cp_key" "$cp_value" \
            || { chat_profile_error "the effective $cp_key value is invalid."; return 2; }
        cp_effective_keys+=("$cp_key")
        cp_effective_values+=("$cp_value")
    done
    for cp_key in "$@"; do
        chat_profile_key "$cp_key" \
            || { chat_profile_error 'an operation requires an unknown profile key.'; return 2; }
        cp_found=false
        for ((cp_index=0; cp_index < ${#cp_effective_keys[@]}; cp_index++)); do
            if [[ "${cp_effective_keys[$cp_index]}" == "$cp_key" && -n "${cp_effective_values[$cp_index]}" ]]; then
                cp_found=true
            fi
        done
        [[ "$cp_found" == true ]] \
            || { chat_profile_error "the operation requires a nonempty $cp_key."; return 2; }
    done
    local cp_ports=() cp_port
    for ((cp_index=0; cp_index < ${#cp_effective_keys[@]}; cp_index++)); do
        case "${cp_effective_keys[$cp_index]}" in
            NODE_RSOCKET_PORT|NODE_MGMT_PORT|NODE_REGISTRY_PORT)
                cp_value="${cp_effective_values[$cp_index]}"
                cp_port="${cp_value#"${cp_value%%[!0]*}"}"
                for cp_other in "${cp_ports[@]+"${cp_ports[@]}"}"; do
                    [[ "$cp_other" != "$cp_port" ]] \
                        || { chat_profile_error 'profile service ports must be distinct.'; return 2; }
                done
                cp_ports+=("$cp_port") ;;
        esac
    done
    for ((cp_index=0; cp_index < ${#cp_effective_keys[@]}; cp_index++)); do
        export "${cp_effective_keys[$cp_index]}=${cp_effective_values[$cp_index]}"
    done
}
