#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTPUT_DIR="${REPO_ROOT}/deps-update"
SCOPE_SCRIPT="${SCRIPT_DIR}/deps-scope.sh"
BOUNDARY_FILE="${OUTPUT_DIR}/deps-boundary.env"
PREFLIGHT_FILE="${OUTPUT_DIR}/preflight.json"

TOOLS=(mvn xmllint jq rg git)

require_file() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    echo "Missing required file: ${file}" >&2
    exit 1
  fi
}

require_file "${SCOPE_SCRIPT}"

"${SCOPE_SCRIPT}" >/dev/null

mkdir -p "${OUTPUT_DIR}"

tool_checks_json() {
  printf '%s\n' "${TOOLS[@]}" | while read -r tool; do
    if command -v "${tool}" >/dev/null 2>&1; then
      jq -nc --arg tool "${tool}" --arg path "$(command -v "${tool}")" \
        '{name: $tool, available: true, path: $path}'
    else
      jq -nc --arg tool "${tool}" \
        '{name: $tool, available: false, path: null}'
    fi
  done | jq -cs 'map({key: .name, value: {available: .available, path: .path}}) | from_entries'
}

git_status_json() {
  local status_file
  status_file="$(mktemp)"
  git -C "${REPO_ROOT}" status --short > "${status_file}"

  jq -Rn --rawfile status "${status_file}" '
    ($status | split("\n") | map(select(length > 0))) as $lines
    | {
        dirty: ($lines | length) > 0,
        entries: $lines
      }
  '

  rm -f "${status_file}"
}

snapshot_refs_json() {
  local snapshot_file
  snapshot_file="$(mktemp)"

  {
    rg -n 'SNAPSHOT' "${REPO_ROOT}/pom.xml" || true

    while read -r module; do
      [[ -z "${module}" ]] && continue
      rg -n 'SNAPSHOT' "${REPO_ROOT}/${module}/pom.xml" || true
    done < "${OUTPUT_DIR}/reactor-modules.txt"
  } > "${snapshot_file}"

  jq -Rn --rawfile refs "${snapshot_file}" '
    ($refs | split("\n") | map(select(length > 0))) as $lines
    | {
        count: ($lines | length),
        refs: $lines
      }
  '

  rm -f "${snapshot_file}"
}

scope_json() {
  jq -n \
    --rawfile reactor "${OUTPUT_DIR}/reactor-modules.txt" \
    --rawfile excluded "${OUTPUT_DIR}/excluded-modules.txt" '
    ($reactor | split("\n") | map(select(length > 0))) as $reactor_modules
    | ($excluded | split("\n") | map(select(length > 0))) as $excluded_modules
    | {
        reactor_module_count: ($reactor_modules | length),
        reactor_modules: $reactor_modules,
        excluded_modules: $excluded_modules,
        excluded_modules_in_reactor: ($excluded_modules - ($excluded_modules - $reactor_modules))
      }
  '
}

boundary_json() {
  if [[ ! -f "${BOUNDARY_FILE}" ]]; then
    jq -nc '{exists: false, spring_boot_version: null, spring_cloud_bom_version: null}'
    return
  fi

  local spring_boot_version
  local spring_cloud_bom_version

  spring_boot_version="$(
    sed -n 's/^SPRING_BOOT_VERSION=//p' "${BOUNDARY_FILE}" | tail -n 1
  )"
  spring_cloud_bom_version="$(
    sed -n 's/^SPRING_CLOUD_BOM_VERSION=//p' "${BOUNDARY_FILE}" | tail -n 1
  )"

  jq -nc \
    --arg spring_boot_version "${spring_boot_version}" \
    --arg spring_cloud_bom_version "${spring_cloud_bom_version}" \
    '{
      exists: true,
      spring_boot_version: ($spring_boot_version | select(length > 0)),
      spring_cloud_bom_version: ($spring_cloud_bom_version | select(length > 0))
    }'
}

tool_checks="$(tool_checks_json)"
git_status="$(git_status_json)"
snapshot_refs="$(snapshot_refs_json)"
scope="$(scope_json)"
boundary="$(boundary_json)"

jq -n \
  --arg generated_at "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
  --arg repo_root "${REPO_ROOT}" \
  --arg root_pom "${REPO_ROOT}/pom.xml" \
  --argjson tools "${tool_checks}" \
  --argjson git "${git_status}" \
  --argjson snapshots "${snapshot_refs}" \
  --argjson scope "${scope}" \
  --argjson boundary "${boundary}" '
  {
    generated_at: $generated_at,
    repo_root: $repo_root,
    root_pom: $root_pom,
    tools: $tools,
    git: $git,
    snapshots: $snapshots,
    scope: $scope,
    boundary: $boundary
  }
  | .status =
      (if ([.tools[] | select(.available == false)] | length) > 0 then "fail"
       elif (.scope.excluded_modules_in_reactor | length) > 0 then "fail"
       elif .boundary.exists == false then "warn"
       elif .git.dirty or (.snapshots.count > 0) then "warn"
       else "ok"
       end)
' > "${PREFLIGHT_FILE}"

echo "Wrote:"
echo "  ${PREFLIGHT_FILE}"
