set shell := ["zsh", "-eu", "-c"]

# Show the command menu.
default:
	@just --list

# Check the default build-health state.
check:
	./shell-scripts/build-health.sh

# Check the install build-health state.
check-install:
	./shell-scripts/build-health.sh --install

# Check the integration build-health state.
check-integration:
	./shell-scripts/build-health.sh --integration

# Build the container image, then check the build-health state. Resolves online.
check-ci:
	./shell-scripts/build-health.sh --ci

# Check chat-build flag output.
check-flags:
	./shell-scripts/test-flags.sh

# Fail when a module pom declares a third-party dependency version.
check-deps:
	./shell-scripts/check-dependency-versions.sh

# Fail when a test artifact can reach a production classpath.
check-production-classpath:
	./shell-scripts/check-production-classpath.sh

# Run the same Maven command as the CI build job.
ci-local:
	mvn -B clean test

# Run the same Maven command as the CI integration job.
ci-integration-local:
	mvn -B clean verify -Ptest-build,integration

# Build selected modules with the manual CI script.
build-partial $modules $mode="unit":
	./shell-scripts/build-partial.sh --modules "$modules" --mode "$mode"

# Print the partial build command without starting Maven.
dry-run-partial $modules $mode="unit":
	./shell-scripts/build-partial.sh --modules "$modules" --mode "$mode" --dry-run

# Check partial build inputs and Maven execution without a build.
check-partial:
	./shell-scripts/test-build-partial.sh

# Start the core service with memory storage.
launch-memory node_id="0":
	./shell-scripts/chat-build core --memory --run --notls --node-id {{node_id}} --init users,rootkeys

# Print the memory launch command and properties.
dry-run-memory node_id="0":
	./shell-scripts/chat-build core --memory --run --notls --node-id {{node_id}} --init users,rootkeys --dry-run

# Start the interactive shell client. Start a core service first.
launch-shell node_id="1":
	./shell-scripts/chat-build shell --run --notls --node-id {{node_id}}

# Print the shell client launch command and properties.
dry-run-shell node_id="1":
	./shell-scripts/chat-build shell --run --notls --node-id {{node_id}} --dry-run

# Build a Cassandra core service image.
build-cassandra node_id:
	./shell-scripts/chat-build core --cassandra --build --notls --node-id {{node_id}}

# Deploy or stop core with Cassandra, Lucene, Redis pub/sub, and a memory command bus.
deploy-core-cassandra-lucene-redis node_id="7" action="":
	#!/usr/bin/env bash
	set -euo pipefail
	node_id={{quote(node_id)}}
	action={{quote(action)}}
	if [[ "$node_id" == --stop || "$action" == --stop ]]; then
	    ./shell-scripts/chat-deploy core --stop
	elif [[ -n "$action" ]]; then
	    echo "Unknown deployment flag: $action" >&2
	    exit 2
	else
	    ./shell-scripts/chat-deploy core --cassandra --index lucene --pubsub redis-pubsub --notls --node-id "${node_id#node_id=}"
	fi

# Start the authorization server after checking its dependencies.
deploy-authserv jwk:
	#!/usr/bin/env bash
	set -euo pipefail
	CHAT_SERVICE_PASSWORD="${CHAT_SERVICE_PASSWORD:-}"
	SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-}"
	SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-}"
	jwk={{quote(jwk)}}
	jwk="${jwk#jwk=}"
	for variable in CHAT_SERVICE_PASSWORD SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD; do
	    if [[ -z "${!variable}" ]]; then
	        echo "deploy-authserv: set $variable before starting" >&2
	        exit 2
	    fi
	done
	if [[ ! -f "$jwk" ]]; then
	    echo "deploy-authserv: JWK file does not exist: $jwk" >&2
	    exit 2
	fi
	jwk="$(cd "$(dirname "$jwk")" && pwd)/$(basename "$jwk")"
	core_health_url="http://127.0.0.1:${CORE_MGMT_PORT:-6791}/actuator/health"
	if ! curl --connect-timeout 2 --max-time 3 --fail --silent "$core_health_url" >/dev/null; then
	    echo "deploy-authserv: core is not healthy at $core_health_url" >&2
	    exit 1
	fi
	auth_health_url="http://127.0.0.1:${AUTHSERV_MGMT_PORT:-9001}/actuator/health"
	auth_status="$(curl --connect-timeout 2 --max-time 3 --silent --output /dev/null --write-out '%{http_code}' "$auth_health_url" || true)"
	if [[ "$auth_status" == 200 ]]; then
	    echo "Authorization server is already healthy at $auth_health_url"
	    exit 0
	fi
	if [[ "$auth_status" != 000 ]]; then
	    echo "deploy-authserv: authorization server responded with HTTP $auth_status" >&2
	    exit 1
	fi
	docker compose -f chat-authorization-server/docker-compose.yaml up -d postgres
	for ((attempt = 1; attempt <= 30; attempt++)); do
	    if docker compose -f chat-authorization-server/docker-compose.yaml exec -T \
	        -e PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" postgres \
	        psql -U "$SPRING_DATASOURCE_USERNAME" -d authserver -Atqc 'SELECT 1' \
	        >/dev/null 2>&1; then
	        break
	    fi
	    if ((attempt == 30)); then
	        echo "deploy-authserv: Postgres did not become ready" >&2
	        exit 1
	    fi
	    sleep 1
	done
	./shell-scripts/chat-build authserv --run --notls --node-id 8 --jwk "$jwk"

# Generate a private ES256 signing key for the authorization server.
generate-authserv-jwk:
	#!/usr/bin/env bash
	set -euo pipefail
	set +x
	repo_root="$(git rev-parse --show-toplevel)"
	output="$repo_root/encrypt-keys/authserv.jwk"
	if [[ -e "$output" ]]; then
	    echo "Authorization signing key already exists: $output"
	    exit 0
	fi
	mkdir -p "$repo_root/encrypt-keys"
	tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/authserv-jwk.XXXXXX")"
	trap 'rm -rf -- "$tmp_dir"' EXIT
	classpath_file="$tmp_dir/classpath"
	mvn -q -f "$repo_root/chat-authorization-server/pom.xml" -Pjdbc \
	    dependency:build-classpath -Dmdep.includeScope=runtime \
	    -Dmdep.outputFile="$classpath_file"
	classpath="$(cat "$classpath_file")"
	if [[ -z "$classpath" ]]; then
	    echo "generate-authserv-jwk: Maven returned an empty classpath" >&2
	    exit 1
	fi
	umask 077
	chmod 700 "$repo_root/encrypt-keys"
	export AUTHSERV_JWK_PATH="$output"
	jshell_script="$tmp_dir/generate.jsh"
	printf '%s\n' \
	    'var jwk = new com.nimbusds.jose.jwk.gen.ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256).keyID(java.util.UUID.randomUUID().toString()).generate();' \
	    'java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv("AUTHSERV_JWK_PATH")), jwk.toJSONString(), java.nio.file.StandardOpenOption.CREATE_NEW);' \
	    '/exit' > "$jshell_script"
	jshell --feedback silent --class-path "$classpath" "$jshell_script"
	chmod 600 "$output"
	if [[ ! -s "$output" ]]; then
	    echo "generate-authserv-jwk: no key was written to $output" >&2
	    exit 1
	fi
	echo "Created authorization signing key: $output"
