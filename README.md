# mygh — минимальный gh-совместимый CLI на Java

`mygh` покрывает узкий, но критичный сценарий работы с pull request'ами GitHub там,
где нельзя поставить оригинальный `gh`: создание PR, чтение inline-комментариев ревью
(треды со статусом resolved), ответы в треды, резолв/анрезолв тредов.

Работает против **GitHub.com Enterprise Cloud** (`api.github.com`) с SAML SSO,
а также против **GitHub Enterprise Server** (настраиваемые base URL). Учитывает
корпоративный прокси и перехват TLS (Zscaler).

## Сборка

```bash
mvn -q clean package     # -> target/mygh.jar (исполняемый fat-jar)
```

Требуется Java 17+ и Maven. Запуск через враппер:

```bash
./mygh --help            # Linux/macOS
mygh.cmd --help          # Windows
```

## Команды

```
mygh pr create    --title <t> [--body <b> | --body-file <path>] --base <branch> --head <branch> [--draft] [--repo owner/name] [--json]
mygh pr list      [--state open|closed|all] [--limit N] [--repo owner/name] [--json]
mygh pr view      <number> [--repo owner/name] [--json]
mygh pr threads   <number> [--all | --unresolved] [--json] [--repo owner/name]
mygh pr reply     <number> --thread <threadId> (--body <b> | --body-file <path>) [--resolve] [--repo owner/name]
mygh pr resolve   <number> --thread <threadId> [--repo owner/name]
mygh pr unresolve <number> --thread <threadId> [--repo owner/name]
mygh pr comment   <number> (--body <b> | --body-file <path>) [--repo owner/name]
```

`threadId` берётся из вывода `mygh pr threads <n>` (GraphQL node id).

Тело комментария можно передать как `--body "текст"` **или** `--body-file <path>`
(отклонение от исходного ТЗ — удобно для многострочных ответов ревьюеру);
`--body-file -` читает из stdin. Указывать оба нельзя.

## Конфигурация (env)

| Переменная | Назначение | Default |
|---|---|---|
| `GH_TOKEN` / `GITHUB_TOKEN` | Personal access token (в таком порядке) | — |
| `GH_HOST` | Хост | `github.com` |
| `GH_REST_URL` | REST base | `https://api.github.com` (GHES: `https://<host>/api/v3`) |
| `GH_GRAPHQL_URL` | GraphQL endpoint | `https://api.github.com/graphql` (GHES: `https://<host>/api/graphql`) |
| `GH_REPO` | Дефолтный `owner/name` | из `git remote` |
| `HTTPS_PROXY` / `HTTP_PROXY` / `NO_PROXY` | Прокси (JVM их не читает сама) | — |
| `GH_CA_BUNDLE` | PEM с доверенным корневым CA (Zscaler root) | — |

Репозиторий определяется из `git remote get-url origin` (SSH и HTTPS форматы);
`--repo` и `GH_REPO` переопределяют.

## Прокси и перехват TLS (Zscaler)

JVM **не читает** `HTTPS_PROXY` автоматически — `mygh` читает переменные окружения
сам и настраивает `HttpClient`. Для перехватывающего прокси укажите корневой CA:

```bash
export HTTPS_PROXY=http://127.0.0.1:9000
export GH_CA_BUNDLE=/path/to/zscaler-root.pem
```

Альтернатива — импорт корня в JDK-truststore (один раз):

```bash
keytool -importcert -alias zscaler -file zscaler-root.pem \
  -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit
```

Экспорт корня Zscaler из хранилища Windows (PowerShell):

```powershell
$cert = Get-ChildItem Cert:\LocalMachine\Root, Cert:\CurrentUser\Root |
        Where-Object { $_.Subject -match 'Zscaler' } | Select-Object -First 1
$pem = "-----BEGIN CERTIFICATE-----`n" +
       [Convert]::ToBase64String($cert.RawData,'InsertLineBreaks') + "`n-----END CERTIFICATE-----"
$pem | Out-File -Encoding ascii "$env:USERPROFILE\Downloads\zscaler-root.pem"
```

При TLS-ошибке без настроенного CA `mygh` печатает подсказку про экспорт Zscaler-сертификата.

## Коды возврата

| Код | Значение |
|---|---|
| 0 | Успех |
| 1 | Ошибка API / валидации |
| 2 | Неверные аргументы |
| 4 | Проблема аутентификации / SSO / TLS |

## Пример

```bash
export GH_TOKEN=ghp_xxx
mygh pr threads 123 --unresolved
mygh pr reply 123 --thread PRRT_kwDO... --body "Готово, поправил" --resolve
```

## Вне области

OAuth/device-flow, полный паритет с `gh`, интерактивные промпты, кэширование и
конфиг-файлы — не поддерживаются (только PAT из env).
