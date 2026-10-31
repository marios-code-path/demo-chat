#!/usr/bin/env bash
# Prepare neutral inputs through the temporary compatibility entry point.
chat_node_inputs() {
    local ci_here
    ci_here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
    . "$ci_here/node-profile.sh"
    if [[ -f "$ci_here/legacy-node-inputs.sh" ]]; then
        . "$ci_here/legacy-node-inputs.sh"
        chat_legacy_node_inputs || return $?
        if printenv CHAT_NODE_PROFILE > /dev/null; then
            chat_legacy_node_profile_load "$@" || return $?
        fi
    elif printenv CHAT_NODE_PROFILE > /dev/null; then
        chat_node_profile_load "$@" || return $?
    fi
}
