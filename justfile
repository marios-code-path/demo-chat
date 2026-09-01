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

# Check chat-build flag output.
check-flags:
	./shell-scripts/test-flags.sh

# Run the same Maven command as the CI build job.
ci-local:
	mvn -B clean test

# Run the same Maven command as the CI integration job.
ci-integration-local:
	mvn -B clean verify -Ptest-build,integration

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
