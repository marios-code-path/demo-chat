#!/usr/bin/env bash
# Offline checks for the register bundle tools. No network call, and no
# container start. See CHAT-jmnjvupr and the plan at
# docs/superpowers/plans/2026-10-21-portable-register-import.md.
#
# register-range cuts exact bytes from a source. validate_mapping refuses a
# mapping that cannot describe one safe import.
set -uo pipefail

# The suite runs the tools from a source tree on purpose.
export NODE9_ALLOW_SOURCE_TREE=1

cd "$(dirname "$0")/.."
repo_root="$PWD"
ranger="$repo_root/node-9/bin/register-range"
importer="$repo_root/node-9/bin/register-import"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

failures=0
ok() { printf 'ok   %s\n' "$1"; }
bad() { printf 'FAIL %s: %s\n' "$1" "$2"; failures=$((failures + 1)); }

sha_of() { shasum -a 256 "$1" | cut -d ' ' -f 1; }

# -- register-range ------------------------------------------------------

printf 'one\r\ntwo\r\nthree\r\n' > "$work/crlf"
printf 'two\r\n' > "$work/crlf-want"
out="$("$ranger" "$work/crlf" 2 3 "$work/crlf-got" 2>&1)"
status=$?
crlf_case='ok'
if [[ $status -ne 0 ]]; then
    crlf_case="exit $status, output: $out"
elif ! cmp -s "$work/crlf-want" "$work/crlf-got"; then
    crlf_case='the bytes differ from line 2'
else
    for pair in 'offset 5' 'bytes 5'; do
        field="${pair% *}"
        want="${pair#* }"
        got="$(jq -r ".$field" <<< "$out" 2> /dev/null)"
        [[ "$got" == "$want" ]] || crlf_case="$field is $got, not $want"
    done
    got="$(jq -r '.sha256' <<< "$out" 2> /dev/null)"
    [[ "$got" == "$(sha_of "$work/crlf-want")" ]] \
        || crlf_case='the sha256 field is wrong'
fi
if [[ "$crlf_case" == 'ok' ]]; then
    ok 'range_keeps_crlf_bytes'
else
    bad 'range_keeps_crlf_bytes' "$crlf_case"
fi

printf 'one\ntwo' > "$work/no-final-lf"
printf 'two' > "$work/no-final-lf-want"
out="$("$ranger" "$work/no-final-lf" 2 3 "$work/no-final-lf-got" 2>&1)"
status=$?
if [[ $status -eq 0 ]] \
   && cmp -s "$work/no-final-lf-want" "$work/no-final-lf-got" \
   && [[ "$(jq -r '.bytes' <<< "$out" 2> /dev/null)" == '3' ]]; then
    ok 'range_without_final_newline'
else
    bad 'range_without_final_newline' "exit $status, output: $out"
fi

# One 3-byte character, 5462 times, gives 16386 bytes and one LF. That range
# passes the 16384-byte limit. 5461 times gives 16383 bytes and one LF, which
# is exactly the limit.
perl -e 'binmode(STDOUT, ":utf8"); print "\x{2026}" x 5462, "\n"' \
    > "$work/over-limit"
perl -e 'binmode(STDOUT, ":utf8"); print "\x{2026}" x 5461, "\n"' \
    > "$work/at-limit"
over_out="$("$ranger" "$work/over-limit" 1 2 "$work/over-got" 2>&1)"
over_status=$?
at_out="$("$ranger" "$work/at-limit" 1 2 "$work/at-got" 2>&1)"
at_status=$?
byte_case='ok'
if [[ $over_status -ne 2 ]]; then
    byte_case="the 16387-byte range gave exit $over_status, output: $over_out"
elif [[ $(printf '%s\n' "$over_out" | grep -c .) -ne 1 ]]; then
    byte_case='the refusal is not one line'
elif [[ $at_status -ne 0 ]]; then
    byte_case="the 16384-byte range gave exit $at_status, output: $at_out"
elif [[ "$(jq -r '.bytes' <<< "$at_out" 2> /dev/null)" != '16384' ]]; then
    byte_case='the byte count of the limit range is wrong'
fi
if [[ "$byte_case" == 'ok' ]]; then
    ok 'range_counts_bytes_not_characters'
else
    bad 'range_counts_bytes_not_characters' "$byte_case"
fi

printf 'one\ntwo\nthree\n' > "$work/lf"
bounds_case='ok'
# Each pair is first_line and after_line: an empty range, a reversed range,
# a zero start, and an end above the last line plus one.
for pair in '2 2' '3 2' '0 2' '1 5'; do
    # shellcheck disable=SC2086
    out="$("$ranger" "$work/lf" $pair "$work/bounds-got" 2>&1)"
    status=$?
    if [[ $status -ne 2 ]]; then
        bounds_case="range $pair gave exit $status, output: $out"
    elif [[ $(printf '%s\n' "$out" | grep -c .) -ne 1 ]]; then
        bounds_case="range $pair printed more than one line"
    fi
    rm -f "$work/bounds-got"
done
if [[ "$bounds_case" == 'ok' ]]; then
    ok 'range_rejects_bad_bounds'
else
    bad 'range_rejects_bad_bounds' "$bounds_case"
fi

# -- validate_mapping ----------------------------------------------------

printf 'Preamble\ntext\n## First\nbody one\n## Second\nbody two\n' \
    > "$work/source.md"

cat > "$work/mapping.json" <<EOF
{
  "version": 1,
  "source_sha256": "$(sha_of "$work/source.md")",
  "source_date": "2026-10-21",
  "source_basis": "Owner-reviewed snapshot for the suite",
  "session": {"first_line": 1, "after_line": 3},
  "entries": [
    {"key": "CHAT-aaaaaaaa", "topic": "forward-register-first",
     "title": "First", "first_line": 3, "after_line": 5},
    {"key": "CHAT-bbbbbbbb", "topic": "forward-register-second",
     "title": "Second", "first_line": 5, "after_line": 7}
  ]
}
EOF

# run_validate MAPPING  Call validate_mapping in a subshell. The library mode
# of register-import defines the function and runs no command.
run_validate() {
    ( REGISTER_IMPORT_LIB=1
      export REGISTER_IMPORT_LIB
      # shellcheck source=/dev/null
      source "$importer" \
          || { printf 'register-import: the library did not load\n'; exit 2; }
      validate_mapping "$1" "$work/source.md" )
}

out="$(run_validate "$work/mapping.json" 2>&1)"
status=$?
if [[ $status -eq 0 && -z "$out" ]]; then
    ok 'mapping_accepts_a_reviewed_file'
else
    bad 'mapping_accepts_a_reviewed_file' "exit $status, output: $out"
fi

zero_hash='0000000000000000000000000000000000000000000000000000000000000000'
mapping_case='ok'
# Each name holds one jq program that breaks the reviewed mapping.
make_bad() {
    local name="$1" program="$2"
    jq "$program" "$work/mapping.json" > "$work/bad-$name.json"
    out="$(run_validate "$work/bad-$name.json" 2>&1)"
    status=$?
    if [[ $status -ne 2 ]]; then
        mapping_case="$name gave exit $status, output: $out"
    elif [[ $(printf '%s\n' "$out" | grep -c .) -ne 1 ]]; then
        mapping_case="$name printed more than one line"
    fi
}

make_bad wrong_hash ".source_sha256 = \"$zero_hash\""
make_bad repeated_key '.entries[1].key = .entries[0].key'
make_bad repeated_topic '.entries[1].topic = .entries[0].topic'
make_bad unsafe_topic '.entries[0].topic = "Bad Topic"'
make_bad key_with_delimiter '.entries[0].key = "a|b"'
make_bad title_with_delimiter '.entries[0].title = "x|y"'
make_bad title_with_newline '.entries[0].title = "x\ny"'
make_bad key_with_leading_space '.entries[0].key = " CHAT-aaaaaaaa"'
if [[ "$mapping_case" == 'ok' ]]; then
    ok 'mapping_rejects_bad_input'
else
    bad 'mapping_rejects_bad_input' "$mapping_case"
fi

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all register bundle cases passed'
