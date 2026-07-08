# mygh — a minimal gh-compatible CLI in Java

`mygh` covers a narrow but critical GitHub pull request workflow for environments
where the official `gh` cannot be installed: creating PRs, reading inline review
comments (threads with resolved status), replying to threads, and resolving/unresolving
threads.

It works against **GitHub.com Enterprise Cloud** (`api.github.com`) with SAML SSO, and
against **GitHub Enterprise Server** (configurable base URLs). It accounts for a corporate
proxy and TLS interception (Zscaler).

## Build

```bash
mvn -q clean package     # -> target/mygh.jar (executable fat-jar)
```

Requires Java 17+ and Maven. Run via the wrapper:

```bash
./mygh --help            # Linux/macOS
mygh.cmd --help          # Windows
```

## Commands

```
mygh pr create    --title <t> [--body <b> | --body-file <path>] --base <branch> --head <branch> [--draft] [--repo owner/name] [--json]
mygh pr list      [--state open|closed|all] [--limit N] [--repo owner/name] [--json]
mygh pr view      <number> [--repo owner/name] [--json]
mygh pr edit      <number> [--title <t>] [--body <b> | --body-file <path>] [--base <branch>] [--state open|closed] [--json] [--repo owner/name]
mygh pr threads   <number> [--all | --unresolved] [--json] [--repo owner/name]
mygh pr reply     <number> --thread <threadId> (--body <b> | --body-file <path>) [--resolve] [--json] [--repo owner/name]
mygh pr resolve   <number> --thread <threadId> [--json] [--repo owner/name]
mygh pr unresolve <number> --thread <threadId> [--json] [--repo owner/name]
mygh pr comment   <number> (--body <b> | --body-file <path>) [--json] [--repo owner/name]
```

`threadId` comes from the output of `mygh pr threads <n>` (a GraphQL node id).

A comment body can be passed as `--body "text"` **or** `--body-file <path>` (a deviation
from the original spec — convenient for multi-line replies to a reviewer); `--body-file -`
reads from stdin. Passing both is an error.

`pr edit` requires at least one of `--title`, `--body`/`--body-file`, `--base`, `--state`.
Use `--state closed` to close a PR (and `--state open` to reopen it).

## Configuration (env)

| Variable | Purpose | Default |
|---|---|---|
| `GH_TOKEN` / `GITHUB_TOKEN` | Personal access token (in this order) | — |
| `GH_HOST` | Host | `github.com` |
| `GH_REST_URL` | REST base | `https://api.github.com` (GHES: `https://<host>/api/v3`) |
| `GH_GRAPHQL_URL` | GraphQL endpoint | `https://api.github.com/graphql` (GHES: `https://<host>/api/graphql`) |
| `GH_REPO` | Default `owner/name` | from `git remote` |
| `HTTPS_PROXY` / `HTTP_PROXY` / `NO_PROXY` | Proxy (the JVM does not read these itself) | — |
| `GH_CA_BUNDLE` | PEM with a trusted root CA (Zscaler root) | — |

The repository is detected from `git remote get-url origin` (SSH and HTTPS formats);
`--repo` and `GH_REPO` override it.

## Proxy and TLS interception (Zscaler)

The JVM does **not** read `HTTPS_PROXY` automatically — `mygh` reads the environment
variables itself and configures `HttpClient`. For an intercepting proxy, provide the root CA:

```bash
export HTTPS_PROXY=http://127.0.0.1:9000
export GH_CA_BUNDLE=/path/to/zscaler-root.pem
```

Alternatively, import the root into the JDK truststore (once):

```bash
keytool -importcert -alias zscaler -file zscaler-root.pem \
  -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit
```

Export the Zscaler root from the Windows store (PowerShell):

```powershell
$cert = Get-ChildItem Cert:\LocalMachine\Root, Cert:\CurrentUser\Root |
        Where-Object { $_.Subject -match 'Zscaler' } | Select-Object -First 1
$pem = "-----BEGIN CERTIFICATE-----`n" +
       [Convert]::ToBase64String($cert.RawData,'InsertLineBreaks') + "`n-----END CERTIFICATE-----"
$pem | Out-File -Encoding ascii "$env:USERPROFILE\Downloads\zscaler-root.pem"
```

On a TLS error without a configured CA, `mygh` prints a hint about exporting the Zscaler certificate.

## Machine-readable output (for agents)

The CLI is designed to be driven by an agent/script, so:

- **`--json` is available on every command**, including the write commands
  (`create`/`edit`/`reply`/`resolve`/`unresolve`/`comment`) — a parseable result instead of
  a human-readable line.
- **Errors are categorized**: stderr prints `error [<category>]: <message>`, where `category`
  is stable and lets you branch without parsing text. Possible categories: `auth`, `sso`,
  `rate_limit`, `not_found`, `validation`, `forbidden`, `graphql`, `tls`, `network`, `config`,
  `usage`, `http_<code>`, `internal`.
- **stdout/stderr are always UTF-8** (including on Windows) — non-ASCII in comment bodies is not mangled.
- `pr list --limit N` with `N > 100` paginates properly instead of silently truncating to 100.

### ⚠️ Non-ASCII in command-line arguments on Windows

On Windows the JVM launcher decodes **command-line arguments** (`argv`) using the legacy
ANSI code page (`sun.jnu.encoding`, e.g. `Cp1252`), not UTF-8. Characters absent from it
(Cyrillic, etc.) become `?` **before** our code runs — they cannot be recovered in-process.
This affects `--title` and `--body` passed directly on the command line.

**The reliable path for non-ASCII avoids argv:**

```bash
# from a file
mygh pr reply 123 --thread <id> --body-file reply.txt
# from stdin
printf 'done, fixed' | mygh pr comment 123 --body-file -
```

`--body-file`/stdin are read as UTF-8 by the CLI and do not touch argv. If you pass non-ASCII
via `--body`/`--title` on an affected platform, the CLI prints a warning.

If you need UTF-8 argv specifically for `--title`, enable system UTF-8 (Windows:
*Region → Administrative → Change system locale → Beta: Use Unicode UTF-8 for worldwide
language support*) so `sun.jnu.encoding` becomes UTF-8. There is no issue on Linux/macOS.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Success |
| 1 | API / validation error |
| 2 | Invalid arguments |
| 4 | Authentication / SSO / TLS problem |

## Example

```bash
export GH_TOKEN=ghp_xxx
mygh pr threads 123 --unresolved
mygh pr reply 123 --thread PRRT_kwDO... --body "done, fixed" --resolve
```

## Out of scope

OAuth/device-flow, full `gh` parity, interactive prompts, caching and config files are
not supported (PAT from env only).
