package dev.mygh.core;

import com.fasterxml.jackson.databind.JsonNode;
import dev.mygh.model.PullRequest;
import dev.mygh.model.ReviewComment;
import dev.mygh.model.ReviewThread;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.mygh.core.RepoResolver.Repo;

/**
 * REST + GraphQL transport over java.net.http.HttpClient.
 * Kept independent of the CLI layer so it can be tested against a mock server.
 */
public final class GitHubClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final Config config;
    private final HttpClient http;

    public GitHubClient(Config config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public static GitHubClient fromEnv(Config config) {
        return new GitHubClient(config, HttpClientFactory.create(config));
    }

    // ================= REST =================

    /** POST /repos/{owner}/{repo}/pulls */
    public PullRequest createPullRequest(Repo repo, String title, String head,
                                         String base, String body, boolean draft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("head", head);
        payload.put("base", base);
        if (body != null) {
            payload.put("body", body);
        }
        payload.put("draft", draft);

        String json = restPost(restPath("/repos/%s/%s/pulls", repo), Json.write(payload));
        return parsePullRequest(Json.parse(json));
    }

    /** GET /repos/{owner}/{repo}/pulls?state=&per_page= */
    public List<PullRequest> listPullRequests(Repo repo, String state, int limit) {
        int perPage = Math.min(Math.max(limit, 1), 100);
        String path = String.format("/repos/%s/%s/pulls?state=%s&per_page=%d",
                repo.owner(), repo.name(), state, perPage);
        String json = restGet(restUri(path));
        JsonNode arr = Json.parse(json);
        List<PullRequest> out = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode node : arr) {
                out.add(parsePullRequest(node));
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    /** GET /repos/{owner}/{repo}/pulls/{number} */
    public PullRequest viewPullRequest(Repo repo, int number) {
        String json = restGet(restUri(String.format("/repos/%s/%s/pulls/%d",
                repo.owner(), repo.name(), number)));
        return parsePullRequest(Json.parse(json));
    }

    /** POST /repos/{owner}/{repo}/issues/{number}/comments */
    public String addIssueComment(Repo repo, int number, String body) {
        String json = restPost(
                restPath("/repos/%s/%s/issues/" + number + "/comments", repo),
                Json.write(Map.of("body", body)));
        JsonNode node = Json.parse(json);
        return textOrNull(node, "html_url");
    }

    /** POST /repos/{owner}/{repo}/pulls/{number}/comments/{comment_id}/replies (REST fallback). */
    public String replyToReviewCommentRest(Repo repo, int number, long commentDatabaseId, String body) {
        String path = String.format("/repos/%s/%s/pulls/%d/comments/%d/replies",
                repo.owner(), repo.name(), number, commentDatabaseId);
        String json = restPost(restUri(path), Json.write(Map.of("body", body)));
        JsonNode node = Json.parse(json);
        return textOrNull(node, "html_url");
    }

    // ================= GraphQL =================

    /** Fetch all review threads for a PR, following pagination. */
    public List<ReviewThread> listReviewThreads(Repo repo, int number) {
        List<ReviewThread> threads = new ArrayList<>();
        String cursor = null;
        do {
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("owner", repo.owner());
            vars.put("repo", repo.name());
            vars.put("number", number);
            vars.put("cursor", cursor);

            JsonNode data = graphql(THREADS_QUERY, vars);
            JsonNode pr = data.path("repository").path("pullRequest");
            if (pr.isMissingNode() || pr.isNull()) {
                throw ApiException.api("pull request #" + number + " not found in " + repo);
            }
            JsonNode reviewThreads = pr.path("reviewThreads");
            for (JsonNode node : reviewThreads.path("nodes")) {
                threads.add(parseThread(node));
            }
            JsonNode pageInfo = reviewThreads.path("pageInfo");
            boolean hasNext = pageInfo.path("hasNextPage").asBoolean(false);
            cursor = hasNext ? pageInfo.path("endCursor").asText(null) : null;
        } while (cursor != null);
        return threads;
    }

    /** Add a reply to a review thread; returns the new comment URL. */
    public String addReviewThreadReply(String threadId, String body) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("threadId", threadId);
        vars.put("body", body);
        JsonNode data = graphql(ADD_REPLY_MUTATION, vars);
        return data.path("addPullRequestReviewThreadReply").path("comment").path("url").asText(null);
    }

    /** Resolve a review thread; returns the resulting isResolved flag. */
    public boolean resolveThread(String threadId) {
        JsonNode data = graphql(RESOLVE_MUTATION, Map.of("threadId", threadId));
        return data.path("resolveReviewThread").path("thread").path("isResolved").asBoolean(false);
    }

    /** Unresolve a review thread; returns the resulting isResolved flag. */
    public boolean unresolveThread(String threadId) {
        JsonNode data = graphql(UNRESOLVE_MUTATION, Map.of("threadId", threadId));
        return data.path("unresolveReviewThread").path("thread").path("isResolved").asBoolean(true);
    }

    private JsonNode graphql(String query, Map<String, Object> variables) {
        String responseBody = graphqlPost(GraphQL.body(query, variables));
        return GraphQL.dataOrThrow(responseBody);
    }

    // ================= HTTP plumbing =================

    private String restGet(URI uri) {
        return sendExpectingBody(baseRequest(uri).GET().build());
    }

    private String restPost(URI uri, String body) {
        return sendExpectingBody(baseRequest(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private String graphqlPost(String body) {
        return sendExpectingBody(baseRequest(URI.create(config.graphqlUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private HttpRequest.Builder baseRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + config.requireToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "mygh/" + Config.VERSION);
    }

    private String sendExpectingBody(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw NetworkErrors.translate(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.api("request interrupted");
        }
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response.body();
        }
        throw errorFor(response);
    }

    private ApiException errorFor(HttpResponse<String> response) {
        int status = response.statusCode();
        String body = response.body() != null ? response.body() : "";
        String apiMessage = extractMessage(body);

        switch (status) {
            case 401:
                return ApiException.auth("401 unauthorized: invalid or missing token"
                        + (apiMessage != null ? " (" + apiMessage + ")" : ""));
            case 403:
                if (isSsoError(response, body)) {
                    String org = ssoOrg(response);
                    return ApiException.auth("403 forbidden: token is not authorized for SSO"
                            + (org != null ? " organization '" + org + "'" : "")
                            + ". Authorize your PAT under the token settings → Configure SSO.");
                }
                if (isRateLimited(response)) {
                    return ApiException.api("403 rate limit exceeded" + rateLimitResetHint(response));
                }
                return ApiException.api("403 forbidden"
                        + (apiMessage != null ? ": " + apiMessage : ""));
            case 404:
                return ApiException.api("404 not found: repository or PR does not exist, or no access");
            case 422:
                return ApiException.api("422 unprocessable: " + describe422(body, apiMessage));
            default:
                return ApiException.api(status + " error"
                        + (apiMessage != null ? ": " + apiMessage : ": " + truncate(body)));
        }
    }

    private static boolean isSsoError(HttpResponse<String> response, String body) {
        if (response.headers().firstValue("x-github-sso").isPresent()) {
            return true;
        }
        String lower = body.toLowerCase();
        return lower.contains("saml") || lower.contains("single sign-on")
                || lower.contains("sso") || lower.contains("resource protected by organization");
    }

    private static String ssoOrg(HttpResponse<String> response) {
        // Header form: "required; url=https://github.com/orgs/<org>/sso?..."
        String sso = response.headers().firstValue("x-github-sso").orElse(null);
        if (sso == null) {
            return null;
        }
        int idx = sso.indexOf("/orgs/");
        if (idx >= 0) {
            String rest = sso.substring(idx + "/orgs/".length());
            int slash = rest.indexOf('/');
            return slash >= 0 ? rest.substring(0, slash) : rest;
        }
        return null;
    }

    private static boolean isRateLimited(HttpResponse<String> response) {
        return response.headers().firstValue("x-ratelimit-remaining")
                .map(v -> v.trim().equals("0")).orElse(false);
    }

    private static String rateLimitResetHint(HttpResponse<String> response) {
        return response.headers().firstValue("x-ratelimit-reset").map(v -> {
            try {
                Instant reset = Instant.ofEpochSecond(Long.parseLong(v.trim()));
                String when = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault()).format(reset);
                return "; resets at " + when;
            } catch (RuntimeException e) {
                return "";
            }
        }).orElse("");
    }

    private static String describe422(String body, String apiMessage) {
        StringBuilder sb = new StringBuilder(apiMessage != null ? apiMessage : "validation failed");
        JsonNode root;
        try {
            root = Json.parse(body);
        } catch (RuntimeException e) {
            return sb.toString();
        }
        JsonNode errors = root.get("errors");
        if (errors != null && errors.isArray()) {
            for (JsonNode err : errors) {
                String message = textOrNull(err, "message");
                String field = textOrNull(err, "field");
                String code = textOrNull(err, "code");
                sb.append("\n  - ");
                if (message != null) {
                    sb.append(message);
                } else {
                    sb.append(field != null ? field : "?").append(": ").append(code != null ? code : "invalid");
                }
            }
        }
        return sb.toString();
    }

    private static String extractMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = Json.parse(body);
            return textOrNull(node, "message");
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ================= parsing =================

    private static PullRequest parsePullRequest(JsonNode node) {
        return new PullRequest(
                node.path("number").asInt(),
                textOrNull(node, "title"),
                textOrNull(node, "state"),
                node.path("draft").asBoolean(false),
                node.path("head").path("ref").asText(null),
                node.path("base").path("ref").asText(null),
                node.path("user").path("login").asText(null),
                textOrNull(node, "html_url"),
                textOrNull(node, "body"));
    }

    private static ReviewThread parseThread(JsonNode node) {
        List<ReviewComment> comments = new ArrayList<>();
        for (JsonNode c : node.path("comments").path("nodes")) {
            comments.add(new ReviewComment(
                    textOrNull(c, "id"),
                    c.hasNonNull("databaseId") ? c.get("databaseId").asLong() : null,
                    c.path("author").path("login").asText(null),
                    textOrNull(c, "body"),
                    textOrNull(c, "path"),
                    c.hasNonNull("line") ? c.get("line").asInt() : null,
                    c.hasNonNull("originalLine") ? c.get("originalLine").asInt() : null,
                    textOrNull(c, "createdAt"),
                    textOrNull(c, "url")));
        }
        return new ReviewThread(
                textOrNull(node, "id"),
                node.path("isResolved").asBoolean(false),
                node.path("isOutdated").asBoolean(false),
                comments);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 300 ? body.substring(0, 300) + "…" : body;
    }

    private URI restUri(String path) {
        return URI.create(config.restUrl() + path);
    }

    private URI restPath(String format, Repo repo) {
        return restUri(String.format(format, repo.owner(), repo.name()));
    }

    // ================= GraphQL documents =================

    private static final String THREADS_QUERY = """
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
            """;

    private static final String ADD_REPLY_MUTATION = """
            mutation($threadId:ID!,$body:String!){
              addPullRequestReviewThreadReply(input:{pullRequestReviewThreadId:$threadId, body:$body}){
                comment{ id url }
              }
            }
            """;

    private static final String RESOLVE_MUTATION = """
            mutation($threadId:ID!){ resolveReviewThread(input:{threadId:$threadId}){ thread{ id isResolved } } }
            """;

    private static final String UNRESOLVE_MUTATION = """
            mutation($threadId:ID!){ unresolveReviewThread(input:{threadId:$threadId}){ thread{ id isResolved } } }
            """;
}
