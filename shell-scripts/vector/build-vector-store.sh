#!/bin/bash
#
# build-vector-store.sh — build, verify, and install one vector store provider.
#
# This builds the smallest module set that produces a working VectorStore, and
# nothing else. No deployment, no image, and no composite service.
#
#   ./build-vector-store.sh simple
#   ./build-vector-store.sh embedded
#   ./build-vector-store.sh redis
#   ./build-vector-store.sh simple --skip-tests
#   ./build-vector-store.sh embedded --no-install
#
# "Deploy" means install to the local Maven repository. A vector store is a
# library that supplies one bean. It has no server of its own, so there is
# nothing else to deploy.
#
# Verification runs the provider's own configuration test. That test builds a
# real store, adds documents, and searches them. A build that compiles but
# cannot answer a search is not a viable asset.
#
# The provider argument becomes app.service.core.vector at launch. Pair it with
# an embedding selector. This repository supplies three: mock, which is test
# only and lives in the chat-core test jar, openai, and local. A production
# value also needs app.service.core.embedding.identity. See
# docs/EMBEDDING-PROVIDERS.md.

set -euo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT="$( cd "$DIR/../.." && pwd )"

RUN_TESTS=1
DO_INSTALL=1
PROVIDER=""

usage() {
    cat <<USAGE
usage: build-vector-store.sh <simple|embedded|redis> [--skip-tests] [--no-install]

  simple     Spring AI SimpleVectorStore. In process, no container.
  embedded   JDK Vector API store. Needs --add-modules jdk.incubator.vector,
             which the module pom already carries.
  redis      Redis Stack store, Jedis backed. Needs a Docker daemon, because
             the verification starts a Redis Stack container.

  --skip-tests   Build and install without verifying the store answers.
  --no-install   Build and verify without writing to the local repository.
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        simple|embedded|redis) PROVIDER="$1"; shift ;;
        --skip-tests) RUN_TESTS=0; shift ;;
        --no-install) DO_INSTALL=0; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "build-vector-store.sh: unknown argument '$1'" >&2; echo >&2; usage >&2; exit 2 ;;
    esac
done

if [[ -z "$PROVIDER" ]]; then
    echo "build-vector-store.sh: name one provider." >&2
    echo >&2
    usage >&2
    exit 2
fi

# One row per provider. Adding a provider means adding a row here and a module
# to the reactor, and nothing else in this script.
case "$PROVIDER" in
    simple)
        MODULE="chat-vector-simple"
        VERIFY_TEST="SimpleVectorStoreConfigurationTests"
        PROFILES=""
        NEEDS_DOCKER=0
        ;;
    embedded)
        MODULE="chat-vector-embedded"
        VERIFY_TEST="EmbeddedVectorStoreConfigurationTests,EmbeddedVectorStoreReplaceTests,VectorApiFlagTests"
        PROFILES=""
        NEEDS_DOCKER=0
        ;;
    redis)
        MODULE="chat-vector-redis"
        VERIFY_TEST="RedisVectorStoreConfigurationTests"
        # The redis test carries @Tag("integration"), and a plain build excludes
        # that group. The profile empties excluded.test.groups.
        PROFILES="-Pintegration"
        NEEDS_DOCKER=1
        ;;
esac

# chat-core supplies the selector validation and DummyEmbeddingModel. Every
# provider needs it, and the embedded module needs its test jar.
MODULES="chat-core,$MODULE"

# The build needs Java 25. An older JDK fails at release 25 rather than here,
# with a message that does not name the cause.
if [[ -z "${JAVA_HOME:-}" ]]; then
    echo "build-vector-store.sh: JAVA_HOME is unset. This build needs Java 25." >&2
    echo "  try: export JAVA_HOME=\$(ls -d ~/.sdkman/candidates/java/25* | head -1)" >&2
    exit 1
fi

JAVA_MAJOR="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
if [[ "$JAVA_MAJOR" -lt 25 ]]; then
    echo "build-vector-store.sh: JAVA_HOME is Java $JAVA_MAJOR. This build needs Java 25." >&2
    echo "  JAVA_HOME=$JAVA_HOME" >&2
    exit 1
fi

if [[ "$NEEDS_DOCKER" -eq 1 && "$RUN_TESTS" -eq 1 ]]; then
    if ! docker info > /dev/null 2>&1; then
        echo "build-vector-store.sh: the redis verification needs a Docker daemon." >&2
        echo "  Start Docker, or pass --skip-tests to build without verifying." >&2
        exit 1
    fi
fi

cd "$ROOT"

echo "provider : $PROVIDER"
echo "modules  : $MODULES"
echo "java     : $JAVA_MAJOR at $JAVA_HOME"
echo

if [[ "$RUN_TESTS" -eq 1 ]]; then
    echo "==> verifying the store answers a search"
    # shellcheck disable=SC2086
    mvn -o -B -pl "$MODULES" $PROFILES test \
        -Dtest="$VERIFY_TEST" \
        -Dsurefire.failIfNoSpecifiedTests=false
    echo
else
    echo "==> skipping verification, by request"
    echo
fi

if [[ "$DO_INSTALL" -eq 1 ]]; then
    echo "==> installing to the local repository"
    # shellcheck disable=SC2086
    mvn -o -B -pl "$MODULES" -DskipTests install -q
    echo
else
    echo "==> skipping install, by request"
    echo
fi

cat <<DONE
==> done

  Artifact : com.demo:$MODULE:0.0.1
  Selector : app.service.core.vector=$PROVIDER

  Launch a deployment that carries this provider with:

    app.service.core.vector=$PROVIDER
    app.service.core.embedding=mock

  mock is test only, and it reaches no launch outside a test classpath. A
  launch takes openai or local, and either one also takes
  app.service.core.embedding.identity. See docs/EMBEDDING-PROVIDERS.md.

  A deploy module can select only a provider it declares. chat-deploy-memory
  declares simple and embedded. chat-deploy-redis declares redis. See
  docs/VECTOR-BUILD-CONTROLS.md.
DONE
