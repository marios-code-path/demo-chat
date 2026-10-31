#!/usr/bin/env bash
# Share profile validation, local port calculation, and availability selection.
chat_tunnel_error() {
    printf 'chat-tunnel: %s\n' "$1" >&2
    return 2
}

chat_tunnel_profile() {
    local tp_here tp_key
    tp_here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
    . "$tp_here/node-inputs.sh"
    chat_node_inputs NODE_RSOCKET_PORT NODE_MGMT_PORT NODE_REGISTRY_PORT || return $?
    for tp_key in NODE_RSOCKET_PORT NODE_MGMT_PORT NODE_REGISTRY_PORT; do
        [[ "${!tp_key+x}" ]] && chat_profile_value "$tp_key" "${!tp_key}" \
            || { chat_tunnel_error "supply a valid $tp_key through a profile or explicit input."; return 2; }
    done
}

chat_tunnel_compute() {
    local tp_offset="$1" tp_auth="${2:-0}" tp_port
    [[ "$tp_offset" =~ ^[0-9]+$ ]] \
        || { chat_tunnel_error "the offset $tp_offset is not a number."; return 2; }
    tp_offset="${tp_offset#"${tp_offset%%[!0]*}"}"
    tp_offset="${tp_offset:-0}"
    (( ${#tp_offset} <= 5 && tp_offset <= 32768 )) \
        || { chat_tunnel_error "the offset $tp_offset passes 32768."; return 2; }
    (( tp_offset % 100 == 0 )) \
        || { chat_tunnel_error "the offset $tp_offset is not a multiple of 100."; return 2; }
    CHAT_TUNNEL_OFFSET="$tp_offset"
    CHAT_TUNNEL_LOCAL_RSOCKET=$((10#$NODE_RSOCKET_PORT + tp_offset))
    CHAT_TUNNEL_LOCAL_MGMT=$((10#$NODE_MGMT_PORT + tp_offset))
    CHAT_TUNNEL_LOCAL_REGISTRY=$((10#$NODE_REGISTRY_PORT + tp_offset))
    CHAT_TUNNEL_REQUIRED_PORTS=("$CHAT_TUNNEL_LOCAL_RSOCKET" "$CHAT_TUNNEL_LOCAL_MGMT" "$CHAT_TUNNEL_LOCAL_REGISTRY")
    if (( tp_auth )); then
        chat_profile_value NODE_MGMT_PORT "${CHAT_TUNNEL_AUTHSERV_PORT:-}" \
            || { chat_tunnel_error 'set CHAT_TUNNEL_AUTHSERV_PORT for the optional forward.'; return 2; }
        CHAT_TUNNEL_LOCAL_AUTHSERV=$((10#$CHAT_TUNNEL_AUTHSERV_PORT + tp_offset))
        CHAT_TUNNEL_REQUIRED_PORTS+=("$CHAT_TUNNEL_LOCAL_AUTHSERV")
    fi
    for tp_port in "${CHAT_TUNNEL_REQUIRED_PORTS[@]}"; do
        (( tp_port >= 1 && tp_port <= 65535 )) \
            || { chat_tunnel_error 'the profile and offset produce a port outside 1 through 65535.'; return 2; }
    done
}

chat_tunnel_find_offset() {
    local tp_check="$1" tp_auth="${2:-0}" tp_offset=0 tp_status
    while (( tp_offset <= 32700 )); do
        chat_tunnel_compute "$tp_offset" "$tp_auth" > /dev/null 2>&1 || return 1
        "$tp_check" "${CHAT_TUNNEL_REQUIRED_PORTS[@]}" > /dev/null 2>&1
        tp_status=$?
        case "$tp_status" in
            0) printf '%s\n' "$tp_offset"; return 0 ;;
            1) tp_offset=$((tp_offset + 100)) ;;
            *) return 2 ;;
        esac
    done
    return 1
}
