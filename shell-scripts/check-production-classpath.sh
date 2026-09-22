#!/bin/bash
#
# Fails when a test artifact can reach a production classpath.
#
# The rule exists because a test jar carried the only EmbeddingModel in this
# repository onto a launched deployment. chat-deploy-memory declared the
# chat-core test jar with no scope element, so it resolved at compile. The
# feature ran in every test and would have failed at launch. No test could
# detect it, because a Spring Boot test puts test jars on its classpath by
# construction. See CHAT-etfnihnu.
#
# Rule one reads the poms. Every dependency with <type>test-jar</type> must
# declare <scope>test</scope>.
#
# Rule two reads the resolved classpath. A known test library must not appear
# on a compile or runtime classpath. A transitive leak never appears in a pom,
# which is why rule two resolves rather than reads. Rule two closes
# CHAT-incuynpc, where two testcontainers artifacts resolve at compile scope in
# chat-shell. Those are ordinary jars and not test jars, so rule one cannot see
# them.
#
#   ./shell-scripts/check-production-classpath.sh
#
# Exit status: 0 when no module breaches either rule, 1 when one does.

set -uo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT="$( cd "$DIR/.." && pwd )"
cd "$ROOT" || exit 1

# CLAUDE.md requires miniforge for Python. Both rules read their input with
# Python, so the activation happens once, here, and it must succeed.
# shellcheck disable=SC1090
if [ -f "$HOME/miniforge3/etc/profile.d/conda.sh" ]; then
    source "$HOME/miniforge3/etc/profile.d/conda.sh"
    conda activate base || { echo "conda activate base failed"; exit 1; }
else
    echo "miniforge is not installed at ~/miniforge3."
    echo "CLAUDE.md requires miniforge for Python. Ask the owner for guidance."
    exit 1
fi
# conda activate base sets CONDA_PREFIX, and on this machine it does not put
# the miniforge bin directory first. A bare python3 then resolves to the
# homebrew interpreter. Measured on 2026-09-15. So every call below names the
# interpreter of the active environment.
PYTHON="$CONDA_PREFIX/bin/python3"
if [ ! -x "$PYTHON" ]; then
    echo "no python3 at $PYTHON after conda activate base"
    exit 1
fi
case "$PYTHON" in
    "$HOME/miniforge3"/*) ;;
    *)
        echo "python3 resolves to $PYTHON, which is outside miniforge."
        echo "CLAUDE.md requires miniforge for Python."
        exit 1
        ;;
esac

FAILED=0

echo "Rule one: every test-jar dependency declares test scope."
"$PYTHON" - <<'PY'
import re, glob, sys

violations = []
for path in sorted(glob.glob('**/pom.xml', recursive=True)):
    if '/target/' in path:
        continue
    text = re.sub(r'<!--.*?-->', '', open(path).read(), flags=re.S)
    text = re.sub(r'<build>.*?</build>', '', text, flags=re.S)
    for block in re.finditer(r'<dependency>(.*?)</dependency>', text, re.S):
        body = block.group(1)
        if '<type>test-jar</type>' not in body:
            continue
        if re.search(r'<scope>\s*test\s*</scope>', body):
            continue
        artifact = re.search(r'<artifactId>([^<]*)</artifactId>', body)
        artifact = artifact.group(1) if artifact else '?'
        violations.append((path, artifact))

if violations:
    print()
    print('A test-jar dependency declares no test scope:')
    print()
    for path, artifact in violations:
        print(f'  {path}')
        print(f'    {artifact}, type test-jar')
    print()
    print('Add <scope>test</scope>. A test jar on a production classpath ships')
    print('test code to a launch, and no test can detect it.')
    sys.exit(1)
print('  ok')
PY
[ $? -ne 0 ] && FAILED=1

echo
echo "Rule two: no known test library on a runtime classpath."

MODULES=$("$PYTHON" -c "
import re
text = open('pom.xml').read()
for m in re.findall(r'<module>([^<]*)</module>', text):
    print(m)
")

CPDIR=$(mktemp -d)
trap 'rm -rf "$CPDIR"' EXIT

# A -pl run resolves every com.demo dependency from ~/.m2. So the reactor must
# be installed before this rule reads a single module. An uninstalled or stale
# local repository makes this rule read the wrong classpath, or none at all.
#
# Use -DskipTests, never -Dmaven.test.skip=true. The second one also skips
# test jar creation, and several modules depend on a test jar of another
# module. chat-index-cassandra needs chat-persistence-cassandra:jar:tests, so
# the install then fails to resolve an artifact its own reactor should have
# produced. That failure hid for as long as a stale test jar sat in ~/.m2.
echo "  installing the reactor, so each standalone resolution is correct"
if ! mvn -o -B -DskipTests install > "$CPDIR/install.log" 2>&1; then
    tail -40 "$CPDIR/install.log"
    echo
    echo "The install did not finish. Rule two cannot resolve a classpath."
    exit 1
fi

for module in $MODULES; do
    out="$CPDIR/$module.txt"
    log="$CPDIR/$module.log"
    # Never hide the Maven output. A silent resolution failure writes no file,
    # and a rule that reads no file reports no violation. That is a false pass.
    if ! mvn -o -pl "$module" \
        -DincludeScope=runtime \
        -Dmdep.outputFile="$out" \
        dependency:build-classpath > "$log" 2>&1; then
        tail -30 "$log"
        echo
        echo "Resolution failed for $module. Rule two cannot run."
        exit 1
    fi
    # A module with no runtime dependency writes no file. That is a legal
    # outcome, and an empty file makes the reader treat it as one.
    [ -f "$out" ] || : > "$out"
done

"$PYTHON" - "$CPDIR" <<'PY'
import os, sys

# Each entry matches a path segment of a resolved artifact. A group id maps to
# a directory path under the local repository. An artifact id matches the jar
# name.
FORBIDDEN = [
    'org/testcontainers/',
    'com/redis/testcontainers-redis/',
    'org/junit/jupiter/',
    'org/junit/platform/',
    'org/mockito/',
    'org/assertj/',
    'io/projectreactor/reactor-test/',
    'org/springframework/boot/spring-boot-starter-test/',
    'org/springframework/security/spring-security-test/',
]

directory = sys.argv[1]
violations = []
# Only the classpath outputs. The same directory holds install.log and one
# .log per module, and a Maven log names every artifact it downloads. Reading
# those would report a violation for a test library that no classpath carries.
for name in sorted(n for n in os.listdir(directory) if n.endswith('.txt')):
    module = name[:-4]
    entries = open(os.path.join(directory, name)).read().split(os.pathsep)
    for entry in entries:
        normal = entry.replace(os.sep, '/')
        for forbidden in FORBIDDEN:
            if forbidden in normal:
                violations.append((module, forbidden, os.path.basename(entry)))

if violations:
    print()
    print('A test library resolves on a runtime classpath:')
    print()
    for module, forbidden, jar in violations:
        print(f'  {module}')
        print(f'    {jar}, matched {forbidden}')
    print()
    print('Add <scope>test</scope> to the declaration, or remove the')
    print('dependency that drags it in.')
    sys.exit(1)
print('  ok')
PY
[ $? -ne 0 ] && FAILED=1

echo
if [ "$FAILED" -eq 0 ]; then
    echo "No module breaches either rule."
else
    echo "At least one module breaches a rule. See above."
fi
exit "$FAILED"
