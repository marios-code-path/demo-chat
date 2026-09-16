#!/bin/zsh

  set -euo pipefail

  TASK_ROOT="${TASK_ROOT:-/Users/darkbit1001/workspace/demo-chat/.worktrees/vector-reindex}"
  BASE_REV="${BASE_REV:-c7dac773}"
  TARGET_REV="${TARGET_REV:-28c0d009}"
  TASK_JAVA_HOME="${TASK_JAVA_HOME:-/Users/darkbit1001/.sdkman/candidates/java/25.0.4-tem}"
  FULL="${FULL:-0}"

  fail() {
    print -u2 -- "FAIL: $*"
    exit 1
  }

  pass() {
    print -- "PASS: $*"
  }

  [[ -d "$TASK_ROOT/.git" || -f "$TASK_ROOT/.git" ]] ||
    fail "No Git worktree exists at $TASK_ROOT"
  [[ -x "$TASK_JAVA_HOME/bin/java" ]] ||
    fail "Java 25 is missing at $TASK_JAVA_HOME"

  command -v mvn >/dev/null || fail "Maven is not available"
  command -v ruby >/dev/null || fail "Ruby is not available"
  command -v drift >/dev/null || fail "drift is not available"
  command -v fp >/dev/null || fail "fp is not available"

  cd "$TASK_ROOT"

  actual_root="$(git rev-parse --show-toplevel)"
  [[ "$actual_root" == "$TASK_ROOT" ]] ||
    fail "Expected root $TASK_ROOT, but Git reports $actual_root"

  actual_head="$(git rev-parse HEAD)"
  expected_head="$(git rev-parse "$TARGET_REV^{commit}")"
  [[ "$actual_head" == "$expected_head" ]] ||
    fail "HEAD is not $TARGET_REV"

  base_head="$(git rev-parse "$BASE_REV^{commit}")"
  target_parent="$(git rev-parse "$TARGET_REV^")"
  [[ "$target_parent" == "$base_head" ]] ||
    fail "$TARGET_REV is not directly after $BASE_REV"

  tracked_status="$(git status --porcelain --untracked-files=no)"
  [[ -z "$tracked_status" ]] || fail "Tracked files are dirty"
  pass "commit and tracked-worktree checks"

  git diff --check "$BASE_REV..$TARGET_REV"
  pass "Git whitespace check"

  drift check
  pass "drift check"

  ruby <<'RUBY'
  plan_path = "docs/superpowers/plans/2026-09-11-vector-index-job-record.md"

  source_paths = [
    "chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorCoveragePolicy.kt",
    "chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorCoveragePolicyImpl.kt",
    "chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/VectorCoveragePolicyImplTests.kt",
  ]

  plan = File.binread(plan_path)

  source_paths.each do |path|
    source = File.binread(path).sub(/\n+\z/, "")
    fenced_source = "```kotlin\n#{source}\n```"

    unless plan.include?(fenced_source)
      abort "FAIL: Task 8 plan block differs from #{path}"
    end
  end

  fences = plan.lines.filter { |line| line.start_with?("```") }

  unless fences.length == 132
    abort "FAIL: expected 132 fences, found #{fences.length}"
  end

  fences.each_with_index do |line, index|
    text = line.strip

    if index.even?
      if text == "```"
        abort "FAIL: fence #{index + 1} is not an opening fence"
      end
    elsif text != "```"
      abort "FAIL: fence #{index + 1} is not a closing fence"
    end
  end

  test_path = source_paths.fetch(2)
  test_count = File.read(test_path).scan(/^\s*@Test\s*$/).length

  unless test_count == 14
    abort "FAIL: expected 14 Task 8 tests, found #{test_count}"
  end

  policy = File.read(source_paths.fetch(1))

  code = policy.lines.reject do |line|
    stripped = line.lstrip
    stripped.start_with?("//", "*", "/**", "*/")
  end.join

  operators = [
    "jobStore.listJobTopics()",
    ".filter { topic -> JobTopicNames.matches(topic.data, nodeId, keyType) }",
    ".flatMap { topic -> jobStore.readJob(topic.key) }",
    "if (job.nodeId == nodeId && job.keyType == keyType)",
    ".filter { job -> job.outcome == JobOutcome.SUCCEEDED }",
    ".filter { job -> trust == VectorTrust.STORED || job.incarnationId == incarnationId }",
    ".sort(",
    ".next()",
    ".filter { job -> job.covers }",
    ".onErrorResume",
  ]

  positions = operators.map do |operator|
    position = code.index(operator)
    abort "FAIL: missing policy step: #{operator}" unless position
    position
  end

  ordered = positions.each_cons(2).all? { |first, second| first < second }
  abort "FAIL: policy steps are out of order" unless ordered

  required_tests = [
    "another node's job is never read",
    "a record that disagrees with its topic name reports no covering job",
    "a running repair keeps the coverage of the older successful job",
    "a failed repair keeps the coverage of the older successful job",
    "an equal instant orders root keys as numbers, not as text",
  ]

  tests = File.read(test_path)

  required_tests.each do |name|
    unless tests.include?("fun `#{name}`()")
      abort "FAIL: missing test: #{name}"
    end
  end

  puts "PASS: Task 8 plan, fence, source, order, and test checks"
RUBY

  export JAVA_HOME="$TASK_JAVA_HOME"
  mvn -o -B -pl chat-core,chat-service-composite test \
   -Dtest=VectorCoveragePolicyImplTests \
   -Dsurefire.failIfNoSpecifiedTests=false

  pass "focused Task 8 test gate"

  mvn -o -B -pl chat-core,chat-service-composite clean test

  ruby -r rexml/document <<'RUBY'
  expected = {
    "chat-core" => {
      tests: 163,
      failures: 0,
      errors: 0,
      skipped: 11,
    },
    "chat-service-composite" => {
      tests: 83,
      failures: 0,
      errors: 0,
      skipped: 0,
    },
  }

  expected.each do |module_name, wanted|
    files = Dir[
      File.join(module_name, "target/surefire-reports/TEST-*.xml")
    ]

    abort "FAIL: no Surefire reports for #{module_name}" if files.empty?

    actual = {
      tests: 0,
      failures: 0,
      errors: 0,
      skipped: 0,
    }

    files.each do |path|
      root = REXML::Document.new(File.read(path)).root

      actual.each_key do |key|
        actual[key] += root.attributes[key.to_s].to_i
      end
    end

    unless actual == wanted
      abort(
        "FAIL: #{module_name} totals #{actual.inspect}, " \
        "expected #{wanted.inspect}"
      )
    end

    puts "PASS: #{module_name} totals #{actual.inspect}"
  end
RUBY

  if [[ "$FULL" == "1" ]]; then
    mvn -o -B clean test

    ruby -r rexml/document <<'RUBY'
  files = Dir["*/target/surefire-reports/TEST-*.xml"]
  abort "FAIL: no full-reactor Surefire reports" if files.empty?

  actual = {
    tests: 0,
    failures: 0,
    errors: 0,
    skipped: 0,
  }

  files.each do |path|
    root = REXML::Document.new(File.read(path)).root

    actual.each_key do |key|
      actual[key] += root.attributes[key.to_s].to_i
    end
  end

  wanted = {
    tests: 653,
    failures: 0,
    errors: 0,
    skipped: 30,
  }

  unless actual == wanted
    abort(
      "FAIL: full-reactor totals #{actual.inspect}, " \
      "expected #{wanted.inspect}"
    )
  end

  puts "PASS: full-reactor totals #{actual.inspect}"
RUBY
  else
    print -- "SKIP: full reactor. Run with FULL=1 to include it."
  fi

  issue_text="$(FP_AGENT_NAME=sigma fp issue show CHAT-fpwpfrfj)"

  [[ "$issue_text" == *"28c0d009"* ]] ||
    fail "CHAT-fpwpfrfj does not name 28c0d009"

  [[ "$issue_text" != *"Blocked: The revised specification needs owner review."* ]] ||
    fail "CHAT-fpwpfrfj still contains the stale blocker"

  pass "FP revision and stale-blocker checks"

  final_status="$(git status --porcelain --untracked-files=no)"
  [[ -z "$final_status" ]] || fail "Tests changed tracked files"

  print -- "PASS: Task 8 review gate"
