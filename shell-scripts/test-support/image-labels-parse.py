#!/usr/bin/env python3
"""Parse BP_IMAGE_LABELS as the Paketo image-labels buildpack does.

This is a test helper. It copies ReadToNext, ReadKey, ReadValue, and
ParseLabels of paketo-buildpacks/image-labels v4.14.4 (commit 2ae3978e,
labels/build.go) line for line. Keep the Go control flow, also where it looks
odd. docs/IMAGE-VERIFICATION.md records the check against the Go functions.

    image-labels-parse.py VALUE   prints {"labels": {...}} or {"error": true}

Python 3.10+ stdlib only.
"""

import json
import sys

QUOTES = ("\"", "'")


class ParseError(Exception):
    pass


def read_to_next(buf, chars):
    found = [buf.find(c) for c in chars if c in buf]
    i = min(found) if found else -1
    if 0 < i < len(buf):
        return buf[0:i], buf[i + 1:], buf[i]
    if i > 0 and i == len(buf):
        return buf[0:i - 1], "", buf[i]
    if i == 0 and i < len(buf):
        return "", buf[i + 1:], buf[i]
    return buf, "", "\0"


def trim_suffix(text, suffix):
    return text[:-len(suffix)] if text.endswith(suffix) else text


def read_key(buf):
    item = ""
    need_closing_quote = False
    key, rest, ch = read_to_next(buf, "\"'=")
    while True:
        if ch in QUOTES and key.endswith("\\"):
            key = trim_suffix(key, "\\") + ch
            ch = "\\"
        item += key
        if ch in QUOTES and need_closing_quote:
            invalid, rest, _ = read_to_next(rest, "=")
            if invalid.strip():
                raise ParseError("unable to have characters after a trailing quote")
            return item, rest
        if ch in QUOTES and not need_closing_quote:
            need_closing_quote = True
        if (ch == "=" or rest == "") and need_closing_quote:
            raise ParseError("unable to find a closing quote")
        elif ch == "=" or rest == "":
            return item, rest
        key, rest, ch = read_to_next(rest, "\"'=")


def read_value(buf):
    item = ""
    need_closing_quote = False
    value, rest, ch = read_to_next(buf, "\"' ")
    while True:
        if ch in QUOTES and value.endswith("\\"):
            value = trim_suffix(value, "\\") + ch
            ch = "\\"
        item += value
        if ch in QUOTES and need_closing_quote:
            invalid, rest, _ = read_to_next(rest, " ")
            if invalid.strip():
                raise ParseError("unable to have characters after a trailing quote")
            return item, rest
        if ch in QUOTES and not need_closing_quote:
            need_closing_quote = True
        if ch == " " and not need_closing_quote:
            return item, rest
        if ch == " " and need_closing_quote:
            item += " "
        if rest == "" and need_closing_quote:
            raise ParseError("unable to find a closing quote")
        elif rest == "":
            return item, rest
        value, rest, ch = read_to_next(rest, "\"' ")


def parse_labels(rest):
    labels = {}
    while True:
        key, rest = read_key(rest)
        if len(key) == 0:
            raise ParseError("unable to have empty key")
        value, rest = read_value(rest)
        labels[key] = value
        if rest == "":
            return labels


def main():
    try:
        print(json.dumps({"labels": parse_labels(sys.argv[1])}, sort_keys=True, ensure_ascii=False))
    except ParseError:
        print(json.dumps({"error": True}))


if __name__ == "__main__":
    main()
