#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTPUT_DIR="${REPO_ROOT}/deps-update"
BOUNDARY_FILE="${OUTPUT_DIR}/deps-boundary.env"
PREFLIGHT_FILE="${OUTPUT_DIR}/preflight.json"

require_file() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    echo "Missing required file: ${file}" >&2
    exit 1
  fi
}

require_file "${PREFLIGHT_FILE}"
require_file "${BOUNDARY_FILE}"

preflight_status="$(jq -r '.status' "${PREFLIGHT_FILE}")"
if [[ "${preflight_status}" == "fail" ]]; then
  echo "Preflight status is 'fail' — resolve issues in ${PREFLIGHT_FILE} before running report" >&2
  exit 1
fi

SPRING_BOOT_VERSION="$(sed -n 's/^SPRING_BOOT_VERSION=//p' "${BOUNDARY_FILE}" | tail -n 1)"
SPRING_CLOUD_BOM_VERSION="$(sed -n 's/^SPRING_CLOUD_BOM_VERSION=//p' "${BOUNDARY_FILE}" | tail -n 1)"

if [[ -z "${SPRING_BOOT_VERSION}" || -z "${SPRING_CLOUD_BOM_VERSION}" ]]; then
  echo "SPRING_BOOT_VERSION or SPRING_CLOUD_BOM_VERSION not set in ${BOUNDARY_FILE}" >&2
  exit 1
fi

echo "Boundary: spring-boot=${SPRING_BOOT_VERSION}  spring-cloud-bom=${SPRING_CLOUD_BOM_VERSION}"
echo ""

mkdir -p "${OUTPUT_DIR}"

run_display() {
  local goal="$1"
  local outfile="${OUTPUT_DIR}/$2"
  echo "  ${goal} ..."
  mvn -f "${REPO_ROOT}/pom.xml" \
    "${goal}" \
    -Dspring-boot.version="${SPRING_BOOT_VERSION}" \
    -Dspring-cloud-bom.version="${SPRING_CLOUD_BOM_VERSION}" \
    -DallowSnapshots=false \
    --no-transfer-progress \
    -U \
    > "${outfile}" 2>&1
  echo "    → ${outfile}"
}

run_display "versions:display-parent-updates"     "report-parent.txt"
run_display "versions:display-property-updates"   "report-properties.txt"
run_display "versions:display-dependency-updates" "report-deps.txt"
run_display "versions:display-plugin-updates"     "report-plugins.txt"

echo ""
echo "Wrote:"
echo "  ${OUTPUT_DIR}/report-parent.txt"
echo "  ${OUTPUT_DIR}/report-properties.txt"
echo "  ${OUTPUT_DIR}/report-deps.txt"
echo "  ${OUTPUT_DIR}/report-plugins.txt"
