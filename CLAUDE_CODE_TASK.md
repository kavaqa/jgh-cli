# Задача для Claude Code: `mygh` — минимальный gh-совместимый CLI на Java

## 1. Контекст и цель

В закрытом окружении нельзя поставить оригинальный GitHub CLI (`gh`). Нужен свой лёгкий
CLI на Java, который покрывает узкий, но критичный сценарий работы с pull request'ами:

1. **Создавать PR**
2. **Читать review-комментарии** (inline-комментарии ревьюеров, сгруппированные в треды, со статусом resolved)
3. **Отвечать на комментарии** (reply в тот же тред)
4. **Резолвить / анрезолвить комментарии** (треды)

Инструмент работает против **GitHub.com Enterprise Cloud** (обычный `api.github.com`,
но внутри enterprise-организации с включённым SSO). Base URL должен быть настраиваемым,
чтобы при необходимости переключиться на GitHub Enterprise Server (`https://<host>/api/v3`
и `https://<host>/api/graphql`).

Итог — исполняемый артефакт: fat-jar + шелл-враппер `mygh`, вызываемый как `gh`.

## 2. Ключевое техническое решение (прочитать до старта)

`gh` — это обёртка над GitHub API. Всё требуемое доступно через публичный API, приватных
механизмов нет. Но **транспорта нужно два**:

- **REST API** (`https://api.github.com`) — создание PR, список PR, список ревью и inline-комментариев.
- **GraphQL API** (`https://api.github.com/graphql`) — обязателен для:
  - получения review-**тредов** со статусом `isResolved` (в REST нет понятия «тред» и флага resolved);
  - **резолва/анрезолва** тредов (`resolveReviewThread` / `unresolveReviewThread` есть **только** в GraphQL);
  - ответа в тред (`addPullRequestReviewThreadReply`) — удобнее, чем REST-replies, т.к. принимает `threadId` напрямую.

Поэтому «читать комментарии», «отвечать» и «резолвить» удобно строить целиком на GraphQL
(единая модель threadId), а REST использовать для создания/списка PR. Reply также
возможен через REST (`POST .../pulls/{n}/comments/{comment_id}/replies`) — оставить как
запасной путь, но основной — GraphQL.

### SSO-нюанс (важно для этого окружения)
PAT (classic или fine-grained) должен быть **явно авторизован под SAML SSO** enterprise-организации.
Иначе запросы возвращают `403` с заголовком/телом, указывающим на необходимость SSO-авторизации.
CLI обязан ловить этот случай и печатать понятную подсказку: «токен не авторизован для SSO
организации <org>, авторизуйте PAT в настройках токена → Configure SSO».

### Корпоративный прокси + перехват TLS (проверено на реальном окружении)
Целевое окружение сидит за корпоративным прокси **Zscaler**, который:
1. Слушает локально (в нашем случае `http://127.0.0.1:9000`) — все исходящие запросы идут через него.
   Без прокси консоль даже не резолвит `github.com` («cannot resolve host»), хотя браузер работает.
2. **Перехватывает TLS** (MITM) и переподписывает сертификаты своим корневым CA. curl/JDK по умолчанию
   этот корень не доверяют → ошибка `SSL certificate problem: self signed certificate in certificate chain`
   / `PKIX path building failed`. Браузер работает только потому, что Zscaler-корень стоит в системном хранилище.

Что из этого следует для CLI (обязательно):
- Уважать стандартные прокси-настройки. **JVM НЕ читает `HTTP(S)_PROXY`** — нужно поддержать оба пути:
  системные свойства `-Dhttps.proxyHost/-Dhttps.proxyPort` и/или самому прочитать `HTTPS_PROXY`/`HTTP_PROXY`/`NO_PROXY`
  и построить `HttpClient.newBuilder().proxy(ProxySelector.of(...))`. Запуск с `-Djava.net.useSystemProxies=true`
  как дополнительный вариант.
- Позволить указать **кастомный CA-truststore** (корень Zscaler), иначе TLS-handshake падает. Пути:
  а) документировать импорт корня в JDK `cacests` через `keytool` (см. раздел 9);
  б) поддержать переменную окружения (напр. `GH_CA_BUNDLE` → путь к PEM) и грузить кастомный `SSLContext`
     с этим сертификатом в `HttpClient`.
- При TLS-ошибке печатать понятную подсказку: «похоже, вы за перехватывающим прокси (Zscaler);
  экспортируйте его корневой сертификат и укажите через GH_CA_BUNDLE / импортируйте в cacerts».

Рабочий корневой сертификат Zscaler уже экспортирован пользователем из хранилища Windows и подтверждён
(`curl --cacert zscaler-root.pem` проходит). Тот же PEM переиспользовать для JDK-truststore.

## 3. Требования к окружению
- Java 17+ (использовать `java.net.http.HttpClient`, records, switch-expressions).
- Maven. Сборка через `maven-shade-plugin` в исполняемый fat-jar с `Main-Class`.
- Разрешены внешние зависимости из Maven Central. Рекомендуемый минимум:
  - **picocli** — парсинг аргументов и генерация help (даёт gh-подобный UX).
  - **Jackson** (`jackson-databind`) — сериализация/десериализация JSON.
  - (тесты) **JUnit 5**; **WireMock** или простой встроенный `HttpServer` для мока API.
  - Если позже понадобится минимизировать зависимости — код транспорта не должен зависеть
    от Jackson жёстко; изолировать разбор JSON за небольшим интерфейсом.

## 4. Интерфейс командной строки (mimic `gh`)

Имя бинарника: `mygh`. Общая форма: `mygh <group> <command> [flags]`.

```
mygh pr create   --title <t> --body <b> --base <branch> --head <branch> [--draft] [--repo owner/name]
mygh pr list     [--state open|closed|all] [--limit N] [--repo owner/name]
mygh pr view     <number> [--repo owner/name]
mygh pr threads  <number> [--all | --unresolved] [--json] [--repo owner/name]
                 # список review-тредов: threadId, resolved/outdated, файл:строка, автор, тело каждого комментария
mygh pr reply    <number> --thread <threadId> --body <b> [--resolve] [--repo owner/name]
                 # добавить ответ в тред; с --resolve сразу зарезолвить тред
mygh pr resolve  <number> --thread <threadId> [--repo owner/name]
mygh pr unresolve <number> --thread <threadId> [--repo owner/name]
mygh pr comment  <number> --body <b> [--repo owner/name]
                 # обычный (не inline) комментарий к PR = issue comment
```

Общие флаги/поведение:
- `--repo owner/name` переопределяет автоопределение; иначе репозиторий берётся из `git remote`.
- `--json` — машинный вывод (сырой JSON или нормализованный). Человекочитаемый вывод по умолчанию.
- Глобально: `--help`/`-h` на каждом уровне (picocli), `--version`.
- Коды возврата: `0` успех; `1` ошибка API/валидации; `2` неверные аргументы; `4` проблема аутентификации/SSO.

## 5. Конфигурация и аутентификация (из env)

Порядок разрешения токена: `GH_TOKEN` → `GITHUB_TOKEN`.
Хост/URL:
- `GH_HOST` (default `github.com`).
- `GH_REST_URL` (default: для github.com → `https://api.github.com`; для GHES → `https://<GH_HOST>/api/v3`).
- `GH_GRAPHQL_URL` (default: github.com → `https://api.github.com/graphql`; GHES → `https://<GH_HOST>/api/graphql`).
- `GH_REPO` — дефолтный `owner/name`, если не удалось/не нужно определять из git.

Прокси и TLS:
- `HTTPS_PROXY` / `HTTP_PROXY` / `NO_PROXY` — стандартные; CLI сам читает их и настраивает `HttpClient`
  (в целевом окружении это `http://127.0.0.1:9000`, Zscaler).
- `GH_CA_BUNDLE` — путь к PEM с доверенным корневым сертификатом (Zscaler root). Если задан — грузить
  кастомный `SSLContext` с ним. Альтернатива — импортировать корень в JDK `cacerts` (раздел 9).

Заголовки запросов:
- `Authorization: Bearer <token>`
- `Accept: application/vnd.github+json`
- `X-GitHub-Api-Version: 2022-11-28`
- `User-Agent: mygh/<version>`

Определение репозитория из git:
- Выполнить `git remote get-url origin` (и fallback на первый доступный remote).
- Распарсить оба формата:
  - `git@github.com:owner/repo.git`
  - `https://github.com/owner/repo.git`
- Убрать суффикс `.git`. При неудаче — просить `--repo`/`GH_REPO`.

## 6. Справочник API (точные вызовы)

### REST
- **Создать PR**: `POST /repos/{owner}/{repo}/pulls`
  тело: `{ "title", "head", "base", "body", "draft" }` → ответ содержит `number`, `html_url`.
- **Список PR**: `GET /repos/{owner}/{repo}/pulls?state={state}&per_page={N}`
- **Просмотр PR**: `GET /repos/{owner}/{repo}/pulls/{number}`
- **Ответ в тред (fallback)**: `POST /repos/{owner}/{repo}/pulls/{number}/comments/{comment_id}/replies`
  тело: `{ "body" }` (нужен `databaseId`/`id` первого комментария треда).
- **Issue-комментарий к PR**: `POST /repos/{owner}/{repo}/issues/{number}/comments` тело `{ "body" }`.

### GraphQL

Получить треды со статусом resolved:
```graphql
query($owner:String!,$repo:String!,$number:Int!,$cursor:String){
  repository(owner:$owner,name:$repo){
    pullRequest(number:$number){
      reviewThreads(first:50, after:$cursor){
        pageInfo{ hasNextPage endCursor }
        nodes{
          id
          isResolved
          isOutdated
          comments(first:100){
            nodes{ id databaseId author{login} body path line originalLine createdAt url }
          }
        }
      }
    }
  }
}
```
(реализовать пагинацию по `pageInfo.hasNextPage` / `endCursor`.)

Ответ в тред:
```graphql
mutation($threadId:ID!,$body:String!){
  addPullRequestReviewThreadReply(input:{pullRequestReviewThreadId:$threadId, body:$body}){
    comment{ id url }
  }
}
```

Резолв / анрезолв:
```graphql
mutation($threadId:ID!){ resolveReviewThread(input:{threadId:$threadId}){ thread{ id isResolved } } }
mutation($threadId:ID!){ unresolveReviewThread(input:{threadId:$threadId}){ thread{ id isResolved } } }
```

> `threadId` — это GraphQL node id (строка), его пользователь получает из `mygh pr threads`.
> В человекочитаемом выводе `threads` показывать этот id (и/или короткий индекс #1, #2, который
> CLI мапит на id внутри — но node id всё равно печатать, т.к. процессы могут быть stateless).

## 7. Структура проекта

```
pom.xml
mygh                      # шелл-враппер: exec java -jar $DIR/mygh.jar "$@"
src/main/java/dev/mygh/
  Main.java               # picocli @Command root, регистрация подкоманд, exit-коды
  cli/
    PrCreateCommand.java
    PrListCommand.java
    PrViewCommand.java
    PrThreadsCommand.java
    PrReplyCommand.java
    PrResolveCommand.java
    PrUnresolveCommand.java
    PrCommentCommand.java
  core/
    GitHubClient.java     # REST + GraphQL транспорт поверх java.net.http.HttpClient
    HttpClientFactory.java # сборка HttpClient: прокси (HTTPS_PROXY) + кастомный SSLContext (GH_CA_BUNDLE)
    Config.java           # чтение env, разрешение URL/токена/прокси/CA
    RepoResolver.java     # парсинг git remote → owner/repo
    GraphQL.java          # хелпер: query + variables → response, обработка errors[]
    ApiException.java     # + распознавание 401/403/SSO/rate-limit
    Json.java             # обёртка над Jackson (изолирует зависимость)
  model/                  # records: PullRequest, ReviewThread, ReviewComment, ...
src/test/java/dev/mygh/
  RepoResolverTest.java
  GitHubClientTest.java   # против мок-сервера (WireMock/HttpServer)
  cli/...Test.java
```

## 8. Обработка ошибок (обязательно)
- **401** → «неверный или отсутствует токен» (exit 4).
- **403 + признак SSO** → подсказка про SSO-авторизацию PAT (exit 4).
- **403 + rate limit** (заголовок `X-RateLimit-Remaining: 0`) → сообщить время сброса (`X-RateLimit-Reset`).
- **404** → «репозиторий/PR не найден или нет прав» (exit 1).
- **422** (валидация, напр. PR уже существует / нет коммитов между base и head) → показать `message` и `errors[]` из тела.
- **GraphQL errors[]** → печатать `message` каждого; ненулевой exit.
- **TLS-ошибка** (`SSLHandshakeException` / `PKIX path building failed`) → подсказка про перехватывающий
  прокси (Zscaler): экспортируйте корневой CA и укажите `GH_CA_BUNDLE` или импортируйте в `cacerts` (exit 4).
- **«cannot resolve host» / отказ соединения** → напомнить, что окружение за прокси, проверьте `HTTPS_PROXY` (exit 1).
- Сетевые/таймауты → внятное сообщение, exit 1. Разумные таймауты на соединение и ответ.

## 9. Сборка и запуск
- `mvn -q clean package` → `target/mygh.jar` (fat-jar, Main-Class указан в манифесте shade-плагином).
- Враппер `mygh`:
  ```sh
  #!/usr/bin/env sh
  DIR="$(cd "$(dirname "$0")" && pwd)"
  exec java -jar "$DIR/mygh.jar" "$@"
  ```
- **Работа за прокси/TLS Zscaler.** Прокси и CA CLI читает из env (`HTTPS_PROXY`, `GH_CA_BUNDLE`) — см. раздел 5.
  Как альтернатива `GH_CA_BUNDLE`, документировать импорт корня Zscaler в JDK-truststore (делается один раз):
  ```bash
  keytool -importcert -alias zscaler -file zscaler-root.pem \
    -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit
  ```
  Экспорт корня из хранилища Windows (PowerShell) для получения `zscaler-root.pem`:
  ```powershell
  $cert = Get-ChildItem Cert:\LocalMachine\Root, Cert:\CurrentUser\Root |
          Where-Object { $_.Subject -match 'Zscaler' } | Select-Object -First 1
  $pem = "-----BEGIN CERTIFICATE-----`n" +
         [Convert]::ToBase64String($cert.RawData,'InsertLineBreaks') + "`n-----END CERTIFICATE-----"
  $pem | Out-File -Encoding ascii "$env:USERPROFILE\Downloads\zscaler-root.pem"
  ```
- (Опционально, стретч) профиль GraalVM `native-image` для настоящего нативного бинарника без JRE.

## 10. Критерии приёмки
- [ ] `mygh pr create --title ... --body ... --base main --head feature` создаёт PR и печатает его URL.
- [ ] `mygh pr threads <n>` печатает все треды: threadId, resolved/outdated, `path:line`, автор и тело комментариев; работает пагинация >50 тредов.
- [ ] `mygh pr reply <n> --thread <id> --body "..."` добавляет ответ в нужный тред; `--resolve` дополнительно резолвит.
- [ ] `mygh pr resolve/unresolve <n> --thread <id>` меняет `isResolved` (проверяется повторным `threads`).
- [ ] `mygh pr comment <n> --body "..."` добавляет обычный комментарий к PR.
- [ ] Репозиторий определяется из `git remote`; `--repo` и `GH_REPO` переопределяют.
- [ ] Токен/URL берутся из env; при 403-SSO печатается корректная подсказка.
- [ ] Все запросы идут через прокси из `HTTPS_PROXY`; TLS проходит с корнем из `GH_CA_BUNDLE` (или из cacerts).
- [ ] При TLS-ошибке без настроенного CA печатается подсказка про Zscaler-сертификат.
- [ ] `--json` даёт машинный вывод; коды возврата соответствуют разделу 4/8.
- [ ] `mvn clean package` собирает исполняемый fat-jar; `./mygh --help` и `./mygh pr --help` работают.
- [ ] Юнит-тесты на `RepoResolver` (оба формата remote) и на `GitHubClient` против мок-сервера (успех + 403 SSO + 422).

## 11. Порядок реализации (фазы)
1. **Каркас**: pom.xml (picocli, jackson, junit, shade), `Main` с root-командой и заглушками подкоманд, враппер, сборка fat-jar.
2. **Core**: `Config` (env/URL), `RepoResolver` (+тесты), `GitHubClient` (REST GET/POST, заголовки, обработка ошибок из раздела 8), `GraphQL` хелпер.
3. **Чтение**: `pr list`, `pr view`, `pr threads` (GraphQL + пагинация + человекочитаемый и `--json` вывод).
4. **Запись**: `pr create`, `pr comment`, `pr reply` (GraphQL, +REST fallback), `pr resolve`/`unresolve`.
5. **Полировка**: exit-коды, SSO-подсказка, help-тексты, README с примерами, тесты против мок-сервера.

## 12. Вне области (non-goals)
- Аутентификация через OAuth/device-flow (только PAT из env).
- Полный паритет с `gh` (issues, releases, gists, actions, checks и т.д.).
- Интерактивные промпты/TUI — всё через флаги.
- Кэширование, конфиг-файлы (пока только env).

## Примечание для реализатора
Начни с раздела 2 и 6 — там вся суть (два транспорта, GraphQL для тредов/резолва, SSO-403).
Держи транспорт (`GitHubClient`) отделённым от команд, чтобы легко покрыть тестами против мок-сервера.
