#!/usr/bin/env bash
# Prove which launch input wins over a baked JVM system property.
# A baked agent property arrives as -Dapp.oauth2.* in JAVA_TOOL_OPTIONS.
# The script compares SPRING_APPLICATION_JSON against a plain variable.
# It also proves that a launch list replaces a baked list.
# It proves that an empty launch value changes nothing.
# It writes only inside one private root. It changes no repository file.
# See CHAT-vidnpywh.
set -euo pipefail
umask 077

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(git -C "$here" rev-parse --show-toplevel)"
proof_root="$(mktemp -d "${TMPDIR:-/tmp}/agent-mapping-precedence.XXXXXX")"
chmod 700 "$proof_root"
trap 'rm -rf -- "$proof_root"' EXIT

baked="from-baked-image"
launch="from-launch"
agent_json='{"app":{"oauth2":{"agent-scope":"from-launch","agents":[{"client-id":"agent-one","username":"One","jwk-set-uri":"https://example.test/one/jwks"}]}}}'

echo "[precedence] Resolving the chat-authorization-server classpath."
mvn -q -o -f "$root/pom.xml" -pl chat-authorization-server \
    dependency:build-classpath -Dmdep.outputFile="$proof_root/cp.txt" > "$proof_root/cp.log" 2>&1
classpath="$(cat "$proof_root/cp.txt")"

cat > "$proof_root/Probe.java" <<'JAVA'
import org.springframework.boot.support.SpringApplicationJsonEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;

public class Probe {
    public static void main(String[] args) {
        StandardEnvironment env = new StandardEnvironment();
        new SpringApplicationJsonEnvironmentPostProcessor().postProcessEnvironment(env, null);
        System.out.println("agent-scope=" + env.getProperty("app.oauth2.agent-scope"));
        System.out.println("agents[0].client-id=" + env.getProperty("app.oauth2.agents[0].client-id"));
        System.out.println("agents[0].jwk-set-uri=" + env.getProperty("app.oauth2.agents[0].jwk-set-uri"));
    }
}
JAVA

cat > "$proof_root/ListProbe.java" <<'JAVA'
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.support.SpringApplicationJsonEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;

import java.util.List;

public class ListProbe {

    /** The agent shape of AgentClientProperties.AgentClient. */
    public static class Agent {
        private String clientId;
        private String username;
        private String jwkSetUri;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getJwkSetUri() { return jwkSetUri; }
        public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
    }

    public static void main(String[] args) {
        StandardEnvironment env = new StandardEnvironment();
        new SpringApplicationJsonEnvironmentPostProcessor().postProcessEnvironment(env, null);
        List<Agent> agents = new Binder(ConfigurationPropertySources.get(env))
                .bind("app.oauth2.agents", Bindable.listOf(Agent.class))
                .orElse(List.of());
        System.out.println("count=" + agents.size());
        for (Agent agent : agents) {
            System.out.println("agent=" + agent.getClientId() + "|" + agent.getUsername() + "|" + agent.getJwkSetUri());
        }
    }
}
JAVA

echo "[precedence] Compiling the probes."
javac -cp "$classpath" -d "$proof_root" "$proof_root/Probe.java" "$proof_root/ListProbe.java"

probe() {
    java -cp "$classpath:$proof_root" "$@" Probe
}

list_probe() {
    java -cp "$classpath:$proof_root" "$@" ListProbe
}

baked_pair=(
    '-Dapp.oauth2.agents[0].client-id=baked-one'
    '-Dapp.oauth2.agents[0].username=BakedOne'
    '-Dapp.oauth2.agents[1].client-id=baked-two'
    '-Dapp.oauth2.agents[1].username=BakedTwo'
)
launch_single='{"app":{"oauth2":{"agents":[{"client-id":"launch-one","username":"LaunchOne","jwk-set-uri":"https://example.test/launch/jwks"}]}}}'

expect() {
    local label="$1" key="$2" want="$3" output="$4"
    local got
    # A key holds brackets, so the match must be literal and anchored.
    got="$(awk -v k="$key" 'index($0, k "=") == 1 { print substr($0, length(k) + 2); exit }' <<<"$output")"
    if [[ "$got" == "$want" ]]; then
        echo "[precedence] PASS $label: $key=$got"
        return 0
    fi
    echo "[precedence] FAIL $label: $key=$got, expected $want" >&2
    return 1
}

failures=0

echo "[precedence] Case 1: launch JSON against a baked system property."
out1="$(SPRING_APPLICATION_JSON="$agent_json" probe "-Dapp.oauth2.agent-scope=$baked")"
expect "case 1" "agent-scope" "$launch" "$out1" || failures=$((failures + 1))
expect "case 1" "agents[0].client-id" "agent-one" "$out1" || failures=$((failures + 1))
expect "case 1" "agents[0].jwk-set-uri" "https://example.test/one/jwks" "$out1" || failures=$((failures + 1))

echo "[precedence] Case 2: plain variable against a baked system property."
out2="$(APP_OAUTH2_AGENT_SCOPE=from-env probe "-Dapp.oauth2.agent-scope=$baked")"
expect "case 2" "agent-scope" "$baked" "$out2" || failures=$((failures + 1))

echo "[precedence] Case 3: plain variable with no baked system property."
out3="$(APP_OAUTH2_AGENT_SCOPE=from-env probe)"
expect "case 3" "agent-scope" "from-env" "$out3" || failures=$((failures + 1))

echo "[precedence] Case 4: a baked system property alone."
out4="$(probe "-Dapp.oauth2.agent-scope=$baked")"
expect "case 4" "agent-scope" "$baked" "$out4" || failures=$((failures + 1))
expect "case 4" "agents[0].client-id" "null" "$out4" || failures=$((failures + 1))

echo "[precedence] Case 5: one launch agent against two baked agents."
out5="$(SPRING_APPLICATION_JSON="$launch_single" list_probe "${baked_pair[@]}")"
expect "case 5" "count" "1" "$out5" || failures=$((failures + 1))
expect "case 5" "agent" "launch-one|LaunchOne|https://example.test/launch/jwks" "$out5" || failures=$((failures + 1))
if grep -q -- "baked-" <<<"$out5"; then
    echo "[precedence] FAIL case 5: a baked agent reached the bound list" >&2
    failures=$((failures + 1))
else
    echo "[precedence] PASS case 5: no baked agent reached the bound list"
fi

echo "[precedence] Case 6: two baked agents with no launch JSON."
out6="$(list_probe "${baked_pair[@]}")"
expect "case 6" "count" "2" "$out6" || failures=$((failures + 1))

echo "[precedence] Case 7: an empty launch value against two baked agents."
out7="$(SPRING_APPLICATION_JSON= list_probe "${baked_pair[@]}")"
expect "case 7" "count" "2" "$out7" || failures=$((failures + 1))
expect "case 7" "agent" "baked-one|BakedOne|null" "$out7" || failures=$((failures + 1))

echo "[precedence] Failures: $failures"
exit $((failures > 0))
