#!/usr/bin/env bash
# General register validation and explicit deployment inputs.

chat_register_initialize() {
    chat_register_tools_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
    . "$chat_register_tools_dir/node-inputs.sh"
    chat_node_inputs || return $?
    if [[ ! "${CHAT_DEPLOYMENT_HOME+x}" ]]; then
        CHAT_DEPLOYMENT_HOME=$(cd "$chat_register_tools_dir/.." && pwd)
    fi
    [[ "$CHAT_DEPLOYMENT_HOME" == /* && -d "$CHAT_DEPLOYMENT_HOME" ]] \
        || { printf 'register: select an absolute CHAT_DEPLOYMENT_HOME directory.\n' >&2; return 2; }
    export CHAT_DEPLOYMENT_HOME
}

chat_deployment_installed() {
    [[ -f "$CHAT_DEPLOYMENT_HOME/.installed" ]]
}

chat_deployment_require_installed() {
    if chat_deployment_installed || [[ "${CHAT_ALLOW_SOURCE_TREE:-}" == 1 ]]; then
        return 0
    fi
    printf 'register: the source tree requires an explicit CHAT_ALLOW_SOURCE_TREE=1 input.\n' >&2
    return 2
}

chat_register_load_profile() {
    chat_node_inputs
}

chat_register_connection() {
    local register_key="${CHAT_SHELL_KEY_TYPE:-${NODE_KEY_TYPE:-}}"
    local register_node="${CHAT_SHELL_NODE_ID:-${NODE_ID:-}}"
    local register_port="${CORE_PORT:-${NODE_RSOCKET_PORT:-}}"
    local register_management="${CORE_MGMT_PORT:-${NODE_MGMT_PORT:-}}"
    chat_profile_value NODE_KEY_TYPE "$register_key" \
        && chat_profile_value NODE_ID "$register_node" \
        || { printf 'register: supply a profile or explicit identity and connection values.\n' >&2; return 2; }
    if [[ -z "${CORE_MGMT_PORT:-}" && -n "${NODE_RSOCKET_PORT:-}" && -n "${NODE_MGMT_PORT:-}" ]] \
        && chat_profile_value NODE_RSOCKET_PORT "$register_port" \
        && chat_profile_value NODE_RSOCKET_PORT "$NODE_RSOCKET_PORT" \
        && chat_profile_value NODE_MGMT_PORT "$NODE_MGMT_PORT"; then
        register_management=$((10#$NODE_MGMT_PORT + 10#$register_port - 10#$NODE_RSOCKET_PORT))
        chat_profile_value NODE_MGMT_PORT "$register_management" \
            || { printf 'register: the profile port offset is outside the valid range.\n' >&2; return 2; }
    fi
    export CHAT_SHELL_KEY_TYPE="$register_key" CHAT_SHELL_NODE_ID="$register_node"
    export CORE_PORT="$register_port" CORE_MGMT_PORT="$register_management"
}

# chat_register_key_error KEY

# Print one reason and return 1 for a key that cannot stand in a pointer
# line. Return 0 for a safe key. A key is opaque text. This project passes an
# fp issue ID, and the rule knows nothing about that source. A key holds no |
# delimiter, no backslash, and no control character. A backslash doubles in
# the tab-separated read form, so a later read cannot match it. A key holds no
# space at its start or its end, and at most 64 characters. The key - is
# reserved for archive pointers. The register command and the importer share
# this rule. See CHAT-jmnjvupr.
chat_register_key_error() {
    local key="${1-}" trimmed
    if [[ -z "$key" ]]; then
        printf 'the key is empty.\n'
        return 1
    fi
    if (( ${#key} > 64 )); then
        printf 'the key passes 64 characters.\n'
        return 1
    fi
    if [[ "$key" == *'|'* ]]; then
        printf 'the key holds the | delimiter.\n'
        return 1
    fi
    if [[ "$key" == *[\\]* ]]; then
        printf 'the key holds a backslash.\n'
        return 1
    fi
    if [[ "$key" == *[[:cntrl:]]* ]]; then
        printf 'the key holds a control character.\n'
        return 1
    fi
    trimmed="${key#"${key%%[![:space:]]*}"}"
    trimmed="${trimmed%"${trimmed##*[![:space:]]}"}"
    if [[ "$key" != "$trimmed" ]]; then
        printf 'the key holds a space at its start or its end.\n'
        return 1
    fi
    if [[ "$key" == '-' ]]; then
        printf 'the key - is reserved for archive pointers.\n'
        return 1
    fi
    return 0
}

# chat_register_title_error TITLE
# Print one reason and return 1 for a title that cannot stand in a pointer
# line. Return 0 for a safe title. A title holds text, no | delimiter, and no
# control character. The register command and the importer share this rule.
# See CHAT-jmnjvupr.
chat_register_title_error() {
    local title="${1-}"
    if [[ -z "$title" ]]; then
        printf 'the title is empty.\n'
        return 1
    fi
    if [[ "$title" == *'|'* ]]; then
        printf 'the title holds the | delimiter.\n'
        return 1
    fi
    if [[ "$title" == *[[:cntrl:]]* ]]; then
        printf 'the title holds a control character.\n'
        return 1
    fi
    return 0
}

# chat_register_value
# Print the register value and return 0. The name wins over the id. Print one
# line and return 2 when neither variable is set. A read uses this function.
# See CHAT-jmnjvupr.
chat_register_value() {
    local name="${CHAT_REGISTER_TOPIC_NAME:-}" id="${CHAT_REGISTER_TOPIC_ID:-}"
    if [[ -n "$name" ]]; then printf '%s\n' "$name"; return 0; fi
    if [[ -n "$id" ]]; then printf '%s\n' "$id"; return 0; fi
    printf 'register: set CHAT_REGISTER_TOPIC_NAME or CHAT_REGISTER_TOPIC_ID.\n' >&2
    return 2
}

# chat_register_name
# Print the register name and return 0. A write uses this function, because it
# mints topic names and receipt paths. Return 2 when the name is absent or
# invalid. See CHAT-jmnjvupr.
chat_register_name() {
    local name="${CHAT_REGISTER_TOPIC_NAME:-}" reason
    if [[ -z "$name" ]]; then
        printf 'register: a write needs CHAT_REGISTER_TOPIC_NAME. An id gives no topic prefix.\n' >&2
        return 2
    fi
    reason="$(chat_register_name_error "$name")" \
        || { printf 'register: %s\n' "$reason" >&2; return 2; }
    printf '%s\n' "$name"
}

# chat_register_name_error NAME
# Print one reason and return 1 for a name that cannot form a topic. Return 0
# for a safe name. A name holds lowercase letters, digits, and dashes, starts
# with a letter or a digit, and holds at most 37 characters. The bound leaves
# room for -fr-snapshot- and twelve hex characters inside 64. See
# CHAT-jmnjvupr.
chat_register_name_error() {
    local name="${1-}"
    if [[ -z "$name" ]]; then
        printf 'the register name is empty.\n'
        return 1
    fi
    if (( ${#name} > 37 )); then
        printf 'the register name passes 37 characters.\n'
        return 1
    fi
    if [[ ! "$name" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
        printf 'the register name is not lowercase letters, digits, and dashes.\n'
        return 1
    fi
    return 0
}

# chat_register_id_error ID
# Print one reason and return 1 for an id that cannot select a register.
# Return 0 for a safe id. An id holds letters, digits, and dashes, starts with
# a letter or a digit, and holds at most 64 characters. The adapter applies
# this rule to both selector keys. See CHAT-lmwuxiiq.
chat_register_id_error() {
    local id="${1-}"
    if [[ -z "$id" ]]; then
        printf 'the register id is empty.\n'
        return 1
    fi
    if (( ${#id} > 64 )); then
        printf 'the register id passes 64 characters.\n'
        return 1
    fi
    if [[ ! "$id" =~ ^[A-Za-z0-9][A-Za-z0-9-]*$ ]]; then
        printf 'the register id is not letters, digits, and dashes.\n'
        return 1
    fi
    return 0
}

