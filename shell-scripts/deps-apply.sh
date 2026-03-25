#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTPUT_DIR="${REPO_ROOT}/deps-update"
BOUNDARY_FILE="${OUTPUT_DIR}/deps-boundary.env"
ROOT_POM="${REPO_ROOT}/pom.xml"

EXCLUDED_PROPERTIES=(
  "kotlin.version"
  "spring-boot.version"
  "spring-cloud-bom.version"
  "spring-cloud.version"
  "spring-cloud-stream.version"
  "spring-cloud-gateway.version"
  "datastax-java-driver.version"
  "spring-shell.version"
  "spring-security.version"
)

TARGET_TESTCONTAINERS=(
  "org.testcontainers:junit-jupiter"
  "org.testcontainers:testcontainers"
  "org.testcontainers:consul"
  "org.testcontainers:elasticsearch"
  "org.testcontainers:cassandra"
)

require_tool() {
  local tool="$1"
  if ! command -v "${tool}" >/dev/null 2>&1; then
    echo "Missing required tool: ${tool}" >&2
    exit 1
  fi
}

require_file() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    echo "Missing required file: ${file}" >&2
    exit 1
  fi
}

require_tool mvn
require_tool git
require_tool perl
require_file "${BOUNDARY_FILE}"
require_file "${ROOT_POM}"

# shellcheck disable=SC1090
source "${BOUNDARY_FILE}"

if [[ -z "${SPRING_BOOT_VERSION:-}" || -z "${SPRING_CLOUD_BOM_VERSION:-}" ]]; then
  echo "Boundary file must set SPRING_BOOT_VERSION and SPRING_CLOUD_BOM_VERSION" >&2
  exit 1
fi

sync_boundary_versions() {
  SPRING_BOOT_VERSION="${SPRING_BOOT_VERSION}" \
  SPRING_CLOUD_BOM_VERSION="${SPRING_CLOUD_BOM_VERSION}" \
  perl -0pi -e '
    s#<spring-boot.version>.*?</spring-boot.version>#<spring-boot.version>$ENV{SPRING_BOOT_VERSION}</spring-boot.version>#s;
    s#<spring-cloud-bom.version>.*?</spring-cloud-bom.version>#<spring-cloud-bom.version>$ENV{SPRING_CLOUD_BOM_VERSION}</spring-cloud-bom.version>#s;
  ' "${ROOT_POM}"
}

sync_boundary_versions

EXCLUDED_PROPERTIES_CSV="$(IFS=,; echo "${EXCLUDED_PROPERTIES[*]}")"
TESTCONTAINERS_COORDS_CSV="$(IFS=,; echo "${TARGET_TESTCONTAINERS[*]}")"

(
  cd "${REPO_ROOT}"

  mvn versions:update-parent \
    -DgenerateBackupPoms=true \
    -DallowSnapshots=false \
    -DparentVersion="[${SPRING_BOOT_VERSION}]"
)

(
  cd "${REPO_ROOT}"

  mvn versions:update-properties \
    -DgenerateBackupPoms=true \
    -DallowSnapshots=false \
    -DexcludeProperties="${EXCLUDED_PROPERTIES_CSV}"

  mvn versions:use-latest-releases \
    -DgenerateBackupPoms=true \
    -DallowSnapshots=false \
    -Dincludes="${TESTCONTAINERS_COORDS_CSV}" \
    -DprocessDependencyManagement=false \
    -DprocessProperties=false

  git diff -- '*.xml' > "${OUTPUT_DIR}/pom-changes.diff"
  git status --short > "${OUTPUT_DIR}/post-apply-status.txt"
)

echo "Wrote:"
echo "  ${OUTPUT_DIR}/pom-changes.diff"
echo "  ${OUTPUT_DIR}/post-apply-status.txt"
