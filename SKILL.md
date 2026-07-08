---
name: mygh-pr-review
description: Create, inspect and act on GitHub pull requests and inline review threads from the command line via the bundled `mygh` CLI — list/view/create/edit PRs, read review threads, reply, resolve/unresolve, and comment. Works with github.com and GitHub Enterprise Server, behind a corporate proxy / TLS interception. Use whenever a task involves reviewing or responding to a GitHub PR without the official `gh`.
---

# mygh — GitHub PR review from the CLI

`mygh` is a small `gh`-compatible CLI focused on the pull-request review loop.
This folder is self-contained: the CLI (`mygh.jar`) and its wrappers ship next to this file.

## Setup

- Requires **Java 17+** on PATH.
- Auth: set `GH_TOKEN` (or `GITHUB_TOKEN`) to a GitHub personal access token.
- Optional (corporate network): `HTTPS_PROXY`, and `GH_CA_BUNDLE=/path/to/root.pem` for
  TLS-intercepting proxies (Zscaler). For GHES set `GH_HOST=your.host`.

## How to run

```sh
./mygh <args>            # Linux/macOS wrapper
mygh.cmd <args>          # Windows wrapper
java -jar mygh.jar <args># portable
```

The repo is auto-detected from `git remote`; override with `--repo owner/name` or `GH_REPO`.

## Rules for agents (important)

1. **Always pass `--json`** — every command supports it and returns stable, parseable output.
2. **Non-ASCII bodies must NOT go through `--body`** (they get corrupted to `?` on Windows).
   Use `--body-file <path>` or pipe via `--body-file -` (stdin). Both are read as UTF-8.
3. **Branch on the error category, not the message.** Errors print to stderr as
   `error [<category>]: <message>`. Categories: `auth`, `sso`, `rate_limit`, `not_found`,
   `validation`, `forbidden`, `graphql`, `tls`, `network`, `config`, `usage`, `http_<code>`.
4. **Exit codes**: `0` ok · `1` API/validation · `2` bad args · `4` auth/SSO/TLS.
5. `threadId` values come from `pr threads` (GraphQL node ids) — pass them verbatim.

## Commands

```
mygh pr list      [--state open|closed|all] [--limit N] [--json]
mygh pr view      <number> [--json]
mygh pr create    --title <t> [--body-file <path>] --base <branch> --head <branch> [--draft] [--json]
mygh pr edit      <number> [--title <t>] [--body-file <path>] [--base <b>] [--state open|closed] [--json]
mygh pr threads   <number> [--unresolved] [--json]
mygh pr reply     <number> --thread <threadId> --body-file <path> [--resolve] [--json]
mygh pr resolve   <number> --thread <threadId> [--json]
mygh pr unresolve <number> --thread <threadId> [--json]
mygh pr comment   <number> --body-file <path> [--json]
```

All commands also accept `--repo owner/name`. `pr edit --state closed` closes a PR.

## Typical workflow: address review comments

```sh
# 1. read the open (unresolved) threads as JSON
./mygh pr threads 123 --unresolved --json

# 2. for each thread, reply (body from a file to stay UTF-8-safe) and resolve it
printf 'Fixed in the latest commit, thanks.' | ./mygh pr reply 123 --thread <threadId> --body-file - --resolve --json

# 3. confirm nothing is left
./mygh pr threads 123 --unresolved --json    # -> []
```

## JSON shapes (fields you will read)

- `pr threads`: `[{ id, resolved, outdated, location, comments:[{ id, databaseId, author, body, path, line, url }] }]`
- `pr create` / `pr view` / `pr edit`: `{ number, title, state, url, ... }`
- `pr reply`: `{ threadId, replyUrl, resolved }`
- `pr resolve` / `pr unresolve`: `{ threadId, resolved }`
- `pr comment`: `{ number, url }`
