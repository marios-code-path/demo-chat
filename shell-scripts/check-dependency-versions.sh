#!/bin/bash
#
# Fails when a module pom declares a version for a third-party dependency.
#
# The rule: no module declares a third-party version. If a BOM manages the
# artifact, declare it with no version. If no BOM manages it, put the version
# in the parent dependencyManagement and still declare it without a version in
# the module.
#
# The rule exists because three separate cleanups found the same defect: a
# stale local override that the build accepts in silence. CHAT-onzqrqox removed
# five downward properties. CHAT-ixazwico removed a jvmTarget pin that made one
# module emit Java 8 bytecode in a Java 25 build. CHAT-hcgmcuxp removed inline
# versions that beat the managed ones, including jackson at 2.9.5 against a
# managed 2.21.4.
#
#   ./shell-scripts/check-dependency-versions.sh
#
# Exit status: 0 when no module declares a third-party version, 1 when one does.

set -uo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT="$( cd "$DIR/.." && pwd )"
cd "$ROOT" || exit 1

python3 - <<'PY'
import re, glob, sys

# A BOM import needs its own version. So does the parent pom, which is where
# every unmanaged version belongs.
ALLOWED_IMPORT_SCOPE = True

violations = []
for path in sorted(glob.glob('**/pom.xml', recursive=True)):
    if '/target/' in path:
        continue
    if path == 'pom.xml':
        continue
    text = re.sub(r'<!--.*?-->', '', open(path).read(), flags=re.S)
    # A plugin dependency lives inside <build> and must carry its own version.
    # pluginManagement does not cover it. Drop those sections before scanning.
    text = re.sub(r'<build>.*?</build>', '', text, flags=re.S)
    for block in re.finditer(r'<dependency>(.*?)</dependency>', text, re.S):
        body = block.group(1)
        version = re.search(r'<version>([^<]*)</version>', body)
        if not version:
            continue
        group = re.search(r'<groupId>([^<]*)</groupId>', body)
        artifact = re.search(r'<artifactId>([^<]*)</artifactId>', body)
        group = group.group(1) if group else '?'
        artifact = artifact.group(1) if artifact else '?'
        # Reactor modules carry their own version.
        if group.startswith('com.demo'):
            continue
        # A BOM import must name a version.
        if ALLOWED_IMPORT_SCOPE and '<scope>import</scope>' in body:
            continue
        violations.append((path, group, artifact, version.group(1)))

if violations:
    print('Third-party versions declared in a module pom:')
    print()
    for path, group, artifact, version in violations:
        print(f'  {path}')
        print(f'    {group}:{artifact} at {version}')
    print()
    print('Move the version to the parent dependencyManagement, or remove it if')
    print('an imported BOM already manages the artifact.')
    sys.exit(1)

print('dependency versions: no module declares a third-party version')
sys.exit(0)
PY
