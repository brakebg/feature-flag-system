#!/usr/bin/env python3
"""PreToolUse hook: refuse edits to owner-only paths listed in scripts/locked-paths.txt.

Exit 0 = allow, exit 2 = block (stderr is shown to the agent).
This is an early warning, not the hard gate: the shell can get around it.
The hard gate is scripts/owner-review.sh, run by the owner from main.
Owner sessions may edit locked files: start Claude Code with FF_OWNER_SESSION=1.
"""
import json
import os
import re
import sys

LIST_FILE = "scripts/locked-paths.txt"
EDIT_TOOLS = {"Edit", "Write", "MultiEdit", "NotebookEdit"}
# Shell operations that change files. Checked only when a locked path appears in the command.
SHELL_WRITE_OPS = re.compile(
    r"\bsed\s+(-[a-zA-Z]*\s+)*-i|\bperl\s+-[a-zA-Z]*i|\btee\b|\bmv\b|\brm\b|\bcp\b|"
    r"\btruncate\b|\bdd\b|\bchmod\b|\bln\b|\bgit\s+(rm|mv|restore|checkout\s+--)"
)


def block(message: str) -> None:
    print(message, file=sys.stderr)
    sys.exit(2)


def load_rules(root: str) -> list[tuple[str, bool]]:
    path = os.path.join(root, LIST_FILE)
    try:
        with open(path, encoding="utf-8") as fh:
            lines = fh.read().splitlines()
    except OSError:
        block(f"BLOCKED: {LIST_FILE} is missing, so locked files cannot be checked. Ask the owner.")
    rules = []
    for raw in lines:
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        rules.append((parts[0], len(parts) > 1 and parts[1] == "create-once"))
    return rules


def matches(rel: str, pattern: str) -> bool:
    if pattern.endswith("/**"):
        return rel.startswith(pattern[:-2])
    return rel == pattern


def blocked_message(rel: str) -> str:
    return (
        f"BLOCKED: '{rel}' is owner-only (listed in {LIST_FILE}). Do not work around this.\n"
        "If a change is really needed, raise a Level 3 escalation (spec 12.5, trigger 2) "
        "and continue with other work."
    )


def check_edit(root: str, rules, file_path: str, tool: str) -> None:
    if not file_path:
        return
    abs_path = os.path.realpath(os.path.join(root, file_path))
    rel = os.path.relpath(abs_path, os.path.realpath(root))
    if rel.startswith(".."):
        return  # outside the repo
    for pattern, create_once in rules:
        if not matches(rel, pattern):
            continue
        if create_once and tool == "Write" and not os.path.exists(abs_path):
            return  # first creation is allowed
        block(blocked_message(rel))


def check_bash(rules, command: str) -> None:
    if not SHELL_WRITE_OPS.search(command) and ">" not in command:
        return
    for pattern, _ in rules:
        base = pattern[:-3] if pattern.endswith("/**") else pattern
        if base not in command:
            continue
        redirect_to_locked = re.search(r">>?\s*[\"']?(\./)?" + re.escape(base), command)
        if redirect_to_locked or SHELL_WRITE_OPS.search(command):
            block(blocked_message(base))


def main() -> None:
    if os.environ.get("FF_OWNER_SESSION") == "1":
        sys.exit(0)
    data = json.load(sys.stdin)
    root = os.environ.get("CLAUDE_PROJECT_DIR") or data.get("cwd") or os.getcwd()
    tool = data.get("tool_name", "")
    tool_input = data.get("tool_input") or {}
    rules = load_rules(root)
    if tool in EDIT_TOOLS:
        check_edit(root, rules, tool_input.get("file_path") or tool_input.get("notebook_path"), tool)
    elif tool == "Bash":
        check_bash(rules, tool_input.get("command", ""))
    sys.exit(0)


if __name__ == "__main__":
    main()
