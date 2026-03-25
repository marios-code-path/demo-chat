#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ROOT_POM="${REPO_ROOT}/pom.xml"
OUTPUT_DIR="${REPO_ROOT}/deps-update"

EXCLUDED_MODULES=(
  "chat-streams"
  "chat-messaging-pulsar"
  "chat-deploy-stream-rabbit"
)

require_tool() {
  local tool="$1"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Missing required tool: $tool" >&2
    exit 1
  fi
}

require_tool xmllint
require_tool jq

if [[ ! -f "${ROOT_POM}" ]]; then
  echo "Root pom.xml not found at ${ROOT_POM}" >&2
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"

extract_modules() {
  xmllint --xpath \
    "/*[local-name()='project']/*[local-name()='modules']/*[local-name()='module']/text()" \
    "${ROOT_POM}" 2>/dev/null \
    | tr ' ' '\n' \
    | sed '/^$/d'
}

extract_properties_json() {
  local property_xml
  property_xml="$(xmllint --xpath "/*[local-name()='project']/*[local-name()='properties']/*" "${ROOT_POM}" 2>/dev/null)"

  PROPERTY_XML="${property_xml}" jq -Rn '
    (env.PROPERTY_XML | split("\n") | map(select(length > 0)))
    | map(capture("^<(?<key>[^>]+)>(?<value>.*)</[^>]+>$"))
    | from_entries
  '
}

extract_modules > "${OUTPUT_DIR}/reactor-modules.txt"
extract_properties_json > "${OUTPUT_DIR}/current-properties.json"
printf '%s\n' "${EXCLUDED_MODULES[@]}" > "${OUTPUT_DIR}/excluded-modules.txt"

echo "Wrote:"
echo "  ${OUTPUT_DIR}/reactor-modules.txt"
echo "  ${OUTPUT_DIR}/current-properties.json"
echo "  ${OUTPUT_DIR}/excluded-modules.txt"
