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

# -- register-uuid5 ------------------------------------------------------

uuid5="$repo_root/node-9/bin/register-uuid5"
out="$("$uuid5" 6ba7b810-9dad-11d1-80b4-00c04fd430c8 python.org 2>&1)"
status=$?
if [[ $status -eq 0 && "$out" == '886313e1-3b8a-5372-9b90-0c9aee199e5d' ]]; then
    ok 'uuid5_known_vector'
else
    bad 'uuid5_known_vector' "exit $status, output: $out"
fi

# -- pack ----------------------------------------------------------------

mode_of() {
    case "$(uname -s)" in
        Darwin) stat -f '%Lp' "$1" ;;
        *) stat -c '%a' "$1" ;;
    esac
}

pack() { "$importer" pack --source "$1" --mapping "$2" --bundle "$3"; }

pack_out="$(pack "$work/source.md" "$work/mapping.json" "$work/bundle-a" 2>&1)"
pack_status=$?
if [[ $pack_status -ne 0 ]]; then
    bad 'pack_builds_a_bundle' "exit $pack_status, output: $pack_out"
else
    ok 'pack_builds_a_bundle'
fi

pack "$work/source.md" "$work/mapping.json" "$work/bundle-b" > /dev/null 2>&1
if cmp -s "$work/bundle-a/bundle-manifest.json" \
          "$work/bundle-b/bundle-manifest.json"; then
    ok 'pack_is_deterministic'
else
    bad 'pack_is_deterministic' 'the two bundle manifests differ'
fi

# One changed source byte changes the bundle identity and every request ID.
sed 's/body one/body ONE/' "$work/source.md" > "$work/source-2.md"
jq --arg sha "$(sha_of "$work/source-2.md")" '.source_sha256 = $sha' \
    "$work/mapping.json" > "$work/mapping-2.json"
pack "$work/source-2.md" "$work/mapping-2.json" "$work/bundle-c" > /dev/null 2>&1
hash_a="$(jq -r '.content_sha256' "$work/bundle-a/bundle-manifest.json")"
hash_c="$(jq -r '.content_sha256' "$work/bundle-c/bundle-manifest.json")"
ids_a="$(jq -r '[.requests[].request_id] | sort | join(",")' \
    "$work/bundle-a/bundle-manifest.json")"
ids_c="$(jq -r '[.requests[].request_id] | sort | join(",")' \
    "$work/bundle-c/bundle-manifest.json")"
if [[ -n "$hash_a" && "$hash_a" != "$hash_c" && "$ids_a" != "$ids_c" ]]; then
    ok 'pack_changes_identity'
else
    bad 'pack_changes_identity' "hash_a=$hash_a hash_c=$hash_c"
fi

mode_case='ok'
if [[ "$(mode_of "$work/bundle-a")" != '700' ]]; then
    mode_case='the bundle directory is not 0700'
else
    while IFS= read -r path; do
        [[ "$(mode_of "$path")" == '600' ]] || mode_case="$path is not 0600"
    done < <(find "$work/bundle-a" -type f)
fi
if [[ "$mode_case" == 'ok' ]]; then
    ok 'pack_modes'
else
    bad 'pack_modes' "$mode_case"
fi

out="$(pack "$work/source.md" "$work/mapping.json" "$work/bundle-a" 2>&1)"
status=$?
if [[ $status -eq 2 ]]; then
    ok 'pack_refuses_existing_dir'
else
    bad 'pack_refuses_existing_dir' "exit $status, output: $out"
fi

printf '## No preamble\nbody\n' > "$work/no-preamble.md"
jq --arg sha "$(sha_of "$work/no-preamble.md")" \
   '.source_sha256 = $sha
    | .session = {"first_line": 1, "after_line": 2}
    | .entries = [.entries[0] | .first_line = 1 | .after_line = 2]' \
   "$work/mapping.json" > "$work/no-preamble-mapping.json"
out="$(pack "$work/no-preamble.md" "$work/no-preamble-mapping.json" \
    "$work/bundle-no-preamble" 2>&1)"
status=$?
if [[ $status -eq 2 && ! -e "$work/bundle-no-preamble" ]]; then
    ok 'pack_refuses_missing_preamble'
else
    bad 'pack_refuses_missing_preamble' "exit $status, output: $out"
fi

nul_case='ok'
printf 'Preamble\n\000\n## First\nbody\n' > "$work/nul.md"
perl -e 'binmode(STDOUT, ":raw"); print "Preamble\n\xff\xfe\n## First\nbody\n"' \
    > "$work/bad-utf8.md"
for name in nul bad-utf8; do
    jq --arg sha "$(sha_of "$work/$name.md")" \
       '.source_sha256 = $sha
        | .session = {"first_line": 1, "after_line": 2}
        | .entries = [.entries[0] | .first_line = 1 | .after_line = 2]' \
       "$work/mapping.json" > "$work/$name-mapping.json"
    out="$(pack "$work/$name.md" "$work/$name-mapping.json" \
        "$work/bundle-$name" 2>&1)"
    status=$?
    if [[ $status -ne 2 ]]; then
        nul_case="$name gave exit $status, output: $out"
    elif [[ -e "$work/bundle-$name" ]]; then
        nul_case="$name left a bundle directory"
    fi
done
if [[ "$nul_case" == 'ok' ]]; then
    ok 'pack_refuses_nul_and_bad_utf8'
else
    bad 'pack_refuses_nul_and_bad_utf8' "$nul_case"
fi

# One section above 16384 bytes stops pack. The error names the heading, the
# index, and the byte count.
{
    printf 'Preamble\n## Big\n'
    perl -e 'binmode(STDOUT, ":utf8"); print "\x{2026}" x 6000, "\n"'
} > "$work/oversize.md"
jq --arg sha "$(sha_of "$work/oversize.md")" \
   '.source_sha256 = $sha
    | .session = {"first_line": 1, "after_line": 2}
    | .entries = [.entries[0] | .first_line = 1 | .after_line = 2]' \
   "$work/mapping.json" > "$work/oversize-mapping.json"
# The preamble holds 9 bytes. Section 1 holds every other byte.
oversize_bytes=$(( $(wc -c < "$work/oversize.md" | tr -d ' ') - 9 ))
out="$(pack "$work/oversize.md" "$work/oversize-mapping.json" \
    "$work/bundle-oversize" 2>&1)"
status=$?
oversize_case='ok'
if [[ $status -ne 2 ]]; then
    oversize_case="exit $status, output: $out"
else
    for pattern in '## Big' 'index 1' "$oversize_bytes"; do
        [[ "$out" == *"$pattern"* ]] || oversize_case="missing: $pattern"
    done
fi
if [[ "$oversize_case" == 'ok' ]]; then
    ok 'pack_names_oversize_section'
else
    bad 'pack_names_oversize_section' "$oversize_case"
fi

review_case='ok'
for pattern in "$(sha_of "$work/source.md")" "$hash_a" \
    'forward-register-first' 'forward-register-second' 'bytes'; do
    [[ "$pack_out" == *"$pattern"* ]] || review_case="missing: $pattern"
done
if [[ "$pack_out" == *'body one'* ]]; then
    review_case='the review output holds source text'
fi
if [[ "$review_case" == 'ok' ]]; then
    ok 'pack_prints_review_lines'
else
    bad 'pack_prints_review_lines' "$review_case"
fi

# One mapping range above the last line of the source stops pack. The error
# names the entry, and pack leaves no bundle directory.
jq '.entries[0].after_line = 99' "$work/mapping.json" \
    > "$work/mapping-over.json"
out="$(pack "$work/source.md" "$work/mapping-over.json" \
    "$work/bundle-over" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'entry 0'* && ! -e "$work/bundle-over" ]]; then
    ok 'pack_refuses_a_range_above_the_source'
else
    bad 'pack_refuses_a_range_above_the_source' "exit $status, output: $out"
fi

# The bundle carries no password, no receipt, and no destination message ID.
# The old prototype suite held this case for its prepared state.
secret_case='ok'
printf 'CHAT_USER_MARIO_PASSWORD=bundle-secret-sentinel\n' > "$work/secrets.env"
if grep -rq 'bundle-secret-sentinel' "$work/bundle-a" 2> /dev/null; then
    secret_case='the bundle holds the sentinel'
elif grep -rqi 'password' "$work/bundle-a" 2> /dev/null; then
    secret_case='the bundle names a password'
elif [[ -e "$work/bundle-a/receipts.jsonl" ]]; then
    secret_case='the bundle holds a receipt file'
fi
if [[ "$secret_case" == 'ok' ]]; then
    ok 'pack_holds_no_secret'
else
    bad 'pack_holds_no_secret' "$secret_case"
fi

# -- check_bundle --------------------------------------------------------

run_check_bundle() {
    ( REGISTER_IMPORT_LIB=1
      export REGISTER_IMPORT_LIB
      # shellcheck source=/dev/null
      source "$importer" \
          || { printf 'register-import: the library did not load\n'; exit 2; }
      check_bundle "$1" )
}

out="$(run_check_bundle "$work/bundle-a" 2>&1)"
status=$?
if [[ $status -eq 0 ]]; then
    ok 'check_bundle_accepts_a_packed_bundle'
else
    bad 'check_bundle_accepts_a_packed_bundle' "exit $status, output: $out"
fi

tamper_case='ok'
# One tampered bundle for each case. Each copy starts from a clean pack.
for name in edited-part missing-part loose-mode edited-manifest; do
    rm -rf "$work/t-$name"
    cp -R "$work/bundle-a" "$work/t-$name"
    target=''
    case "$name" in
        edited-part)
            target="$(find "$work/t-$name/parts" -type f | sort | head -1)"
            printf 'x' >> "$target"
            ;;
        missing-part)
            target="$(find "$work/t-$name/parts" -type f | sort | head -1)"
            rm -f "$target"
            ;;
        loose-mode)
            target="$work/t-$name/content-manifest.json"
            chmod 644 "$target"
            ;;
        edited-manifest)
            target="$work/t-$name/bundle-manifest.json"
            jq '.requests[0].request_id = "00000000-0000-5000-8000-000000000000"' \
                "$target" > "$target.new" && mv "$target.new" "$target"
            chmod 600 "$target"
            ;;
    esac
    out="$(run_check_bundle "$work/t-$name" 2>&1)"
    status=$?
    if [[ $status -ne 2 ]]; then
        tamper_case="$name gave exit $status, output: $out"
    elif [[ "$out" != *"$(basename "$target")"* ]]; then
        tamper_case="$name does not name $(basename "$target"): $out"
    fi
done
if [[ "$tamper_case" == 'ok' ]]; then
    ok 'check_bundle_refuses_tampering'
else
    bad 'check_bundle_refuses_tampering' "$tamper_case"
fi

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all register bundle cases passed'
