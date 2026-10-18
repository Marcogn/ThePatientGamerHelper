#!/bin/bash
# PreToolUse hook: a branch Claude creates must have a name that says what it is for.
# Generic: copy it unchanged into any project (docs/claude.md).
#
# Checks `git checkout -b|-B|--orphan`, `git switch -c|-C|--create`, `git branch <name>`,
# `git worktree add -b` (Bash; only real commands, not quoted text) and mcp__github__create_branch. Accepted: <type>/<what-and-where>, lowercase, hyphens, at least
# three words in all, e.g. fix/crash-on-rotation, feat/export-pdf-share, deps/gradle-minor-bumps.
# Branches named `claude/...` or `ccr-...` are the cloud environment's own and always pass. Fails open.

set -u
INPUT=$(cat)

if command -v jq >/dev/null 2>&1; then
  TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty')
  CMD=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
  MCP=$(echo "$INPUT" | jq -r '.tool_input.branch // empty')
else
  exit 0
fi

# The name a git command in one shell segment creates, if any: `git [-C dir] checkout -b|-B|--orphan
# <name>`, `switch -c|-C|--create|--force-create <name>`, `branch <name>`, `worktree add ... -b|-B <name>`.
branch_in_segment() {
  local -a w
  read -ra w <<< "$1"
  local i=1
  [ "${w[0]:-}" = git ] || return 0
  while [ "$i" -lt "${#w[@]}" ] && [[ "${w[$i]}" == -C || "${w[$i]}" == -c ]]; do i=$((i + 2)); done
  local sub="${w[$i]:-}" j
  i=$((i + 1))
  case "$sub" in
    checkout|switch|worktree)
      for ((j = i; j < ${#w[@]}; j++)); do
        case "$sub:${w[$j]}" in
          checkout:-b|checkout:-B|checkout:--orphan|switch:-c|switch:-C|switch:--create|switch:--force-create|worktree:-b|worktree:-B)
            echo "${w[$((j + 1))]:-}"; return 0 ;;
          # the same options with the value attached: --create=name, -cname, -bname
          switch:--create=*|switch:--force-create=*|checkout:--orphan=*)
            echo "${w[$j]#*=}"; return 0 ;;
          checkout:-b?*|checkout:-B?*|switch:-c?*|switch:-C?*|worktree:-b?*|worktree:-B?*)
            echo "${w[$j]:2}"; return 0 ;;
        esac
      done ;;
    branch)
      [[ -n "${w[$i]:-}" && "${w[$i]}" != -* ]] && echo "${w[$i]}" ;;
  esac
  return 0
}

NAME=""
if [ "$TOOL" = "Bash" ]; then
  # Quoted text and heredoc bodies (commit messages, echo) are not commands: drop them, even
  # across lines (a quoted single word stays, it may be the branch name), then look at each segment between ; & | ( ) and newlines, so `git branch | head`
  # or a commit message that mentions `git branch x` pass. POSIX awk: works with macOS awk too.
  while IFS= read -r seg; do
    seg="${seg#"${seg%%[![:space:]]*}"}"
    NAME=$(branch_in_segment "$seg")
    [ -n "$NAME" ] && break
  done < <(printf '%s\n' "$CMD" | awk '
    { text = text $0 "\n" }
    END {
      n = length(text); q = ""; hd = ""; i = 1
      while (i <= n) {
        c = substr(text, i, 1)
        if (q != "" && c == q) {
          # a quoted single word may be a branch name; longer text is one opaque argument
          if (qb ~ /^[^ \t\n]+$/) printf "%s", qb; else printf "_"
          q = ""; i++; continue
        }
        if (q == "\"" && c == "\\") { qb = qb substr(text, i + 1, 1); i += 2; continue }
        if (q != "") { qb = qb c; i++; continue }
        if (c == "\047" || c == "\"") { q = c; qb = ""; i++; continue }
        if (c == "\\") { i += 2; continue }
        if (substr(text, i, 2) == "<<" && substr(text, i, 3) != "<<<") {
          j = i + 2; tabs = 0
          if (substr(text, j, 1) == "-") { tabs = 1; j++ }
          while (substr(text, j, 1) == " ") j++
          hd = ""
          while (j <= n) {
            ch = substr(text, j, 1)
            if (ch ~ /[ \t\n;&|<>()]/) break
            if (ch != "\047" && ch != "\"" && ch != "\\") hd = hd ch
            j++
          }
          i = j; continue
        }
        if (c == "\n") {
          printf "\n"; i++
          if (hd != "") {
            while (i <= n) {
              e = index(substr(text, i), "\n"); line = substr(text, i, e - 1); i += e
              if (tabs) sub(/^\t+/, "", line)
              if (line == hd) break
            }
            hd = ""
          }
          continue
        }
        if (c ~ /[;&|()]/) printf "\n"; else printf "%s", c
        i++
      }
    }')
else
  NAME="$MCP"
fi
[ -z "$NAME" ] && exit 0
case "$NAME" in claude/*|ccr-*) exit 0 ;; esac

WORDS=$(echo "$NAME" | tr '/_.-' '\n\n\n\n' | grep -Ec '^[a-z]{3,}$')
if echo "$NAME" | grep -Eq '^[a-z0-9]+([/_.-][a-z0-9]+)*$' && [ "$WORDS" -ge 3 ] \
   && ! echo "$NAME" | grep -Eq '^(tmp|temp|test|wip|new|patch|branch)([/_.-]|$)'; then
  exit 0
fi

MSG="Branch name '$NAME' doesn't say what the branch is for. Use <type>/<what-and-where> in lowercase with hyphens, at least three words, e.g. fix/crash-on-rotation, feat/export-pdf-share, deps/gradle-minor-bumps, docs/ci-release-guide (types: feat, fix, refactor, docs, chore, deps, plan, phase). Rename and retry."
jq -n --arg r "$MSG" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
exit 0
