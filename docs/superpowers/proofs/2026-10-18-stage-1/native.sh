#!/usr/bin/env bash
# The native one-shot proof of stage 1, task 7. A bounded attempt: it records
# what happens and does not claim a native delivery. See docs/NATIVE-IMAGE.md.
#
#   native.sh build <scratch-dir>
#   native.sh run <scratch-dir> [command words...]
#
# build   Builds the chat-deploy native image with the shell composition fixed
#         at AOT time. It writes the image path to <scratch-dir>/native-image
#         and the log to <scratch-dir>/native-build.log. Run just build-shell-cli
#         first. The build runs from the chat-deploy pom alone, with no -pl and
#         no -am. With the shell profile, chat-deploy depends on chat-shell, and
#         chat-shell depends on chat-deploy. Any reactor that holds both
#         refuses that cycle, and so does a reactor with only -pl. The build
#         loads the shell jars from ~/.m2.
# run     Starts the image inside the stage 0 sandbox. Every argument is a
#         command word, because one-shot mode joins the program arguments into
#         the command line. The runtime settings are environment variables in
#         the relaxed form of Spring Boot. A variable that the caller sets wins.
set -uo pipefail
action="${1:?usage: native.sh build|run <scratch-dir> [command words...]}"
S="${2:?usage: native.sh build|run <scratch-dir> [command words...]}"
shift 2
root="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"

case "$action" in
build)
    # AOT fixes the profiles and the properties, so the build names the composition of the shell launch.
    options="$(CHAT_SHELL_CLI_PRINT_OPTIONS=1 CHAT_SHELL_KEY_TYPE=uuid CORE_PORT="${CORE_PORT:-6990}" \
        CORE_MGMT_PORT="${CORE_MGMT_PORT:-6991}" "$root/shell-scripts/chat-shell-cli" | tr '\n' ' ')"
    printf '%s\n' "$options" > "$S/native-aot-options"
    start="$SECONDS"
    (cd "$root" && mvn -B -ntp -f chat-deploy/pom.xml -Pnative,deploy,shell,shell-host,client-backend package \
        -Dmaven.test.skip=true -DappMainClass=com.demo.chat.ShellCliKt \
        "-Dspring-boot.aot.jvmArguments=$options") > "$S/native-build.log" 2>&1
    status=$?
    echo "build: exit $status in $((SECONDS - start)) s, log $S/native-build.log"
    if [[ $status -eq 0 && -x "$root/chat-deploy/target/chat-deploy" ]]; then
        echo "$root/chat-deploy/target/chat-deploy" > "$S/native-image"
    fi
    exit "$status"
    ;;
run)
    image="$(cat "$S/native-image")"
    exec sandbox-exec -f "$S/isolation.sb" env \
        CORE_PORT="${CORE_PORT:-6990}" CORE_MGMT_PORT="${CORE_MGMT_PORT:-6991}" \
        SPRING_MAIN_BANNERMODE="${SPRING_MAIN_BANNERMODE:-off}" \
        LOGGING_THRESHOLD_CONSOLE="${LOGGING_THRESHOLD_CONSOLE:-OFF}" \
        SPRING_MAIN_WEBAPPLICATIONTYPE="${SPRING_MAIN_WEBAPPLICATIONTYPE:-none}" \
        "$image" "$@"
    ;;
*) echo "usage: native.sh build|run <scratch-dir> [command words...]" >&2; exit 2 ;;
esac
