package dev.mygh.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.mygh.core.RepoResolver.Repo;
import dev.mygh.model.PullRequest;
import dev.mygh.model.ReviewThread;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubClientTest {

    private HttpServer server;
    private String baseUrl;

    // Registered responders keyed by "METHOD path".
    private final Map<String, Responder> routes = new HashMap<>();

    @FunctionalInterface
    interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new Dispatcher());
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private GitHubClient client() {
        Config config = Config.fromEnv(name -> switch (name) {
            case "GH_TOKEN" -> "test-token";
            case "GH_REST_URL" -> baseUrl;
            case "GH_GRAPHQL_URL" -> baseUrl + "/graphql";
            default -> null;
        });
        return new GitHubClient(config, HttpClient.newHttpClient());
    }

    private void route(String key, Responder r) {
        routes.put(key, r);
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private final class Dispatcher implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            Responder r = routes.get(key);
            if (r == null) {
                send(exchange, 500, "{\"message\":\"no route for " + key + "\"}");
                return;
            }
            r.respond(exchange);
        }
    }

    // ---- tests ----

    @Test
    void createPullRequestSuccess() throws IOException {
        route("POST /repos/octocat/hello/pulls", ex -> {
            // consume request body
            ex.getRequestBody().readAllBytes();
            assertEquals("Bearer test-token", ex.getRequestHeaders().getFirst("Authorization"));
            send(ex, 201, "{\"number\":42,\"html_url\":\"https://github.com/octocat/hello/pull/42\","
                    + "\"state\":\"open\",\"draft\":false,\"title\":\"My PR\"}");
        });

        PullRequest pr = client().createPullRequest(new Repo("octocat", "hello"),
                "My PR", "feature", "main", "body", false);
        assertEquals(42, pr.number());
        assertEquals("https://github.com/octocat/hello/pull/42", pr.htmlUrl());
    }

    @Test
    void ssoForbiddenMapsToAuthExit() {
        route("GET /repos/octocat/hello/pulls/1", ex -> {
            ex.getResponseHeaders().add("X-GitHub-SSO",
                    "required; url=https://github.com/orgs/octo-enterprise/sso");
            send(ex, 403, "{\"message\":\"Resource protected by organization SAML enforcement.\"}");
        });

        ApiException ex = assertThrows(ApiException.class,
                () -> client().viewPullRequest(new Repo("octocat", "hello"), 1));
        assertEquals(ApiException.EXIT_AUTH, ex.exitCode());
        assertEquals("sso", ex.category());
        assertTrue(ex.getMessage().toLowerCase().contains("sso"));
        assertTrue(ex.getMessage().contains("octo-enterprise"));
    }

    @Test
    void unprocessableEntityMapsToApiExit() {
        route("POST /repos/octocat/hello/pulls", ex -> {
            ex.getRequestBody().readAllBytes();
            send(ex, 422, "{\"message\":\"Validation Failed\",\"errors\":["
                    + "{\"resource\":\"PullRequest\",\"code\":\"custom\","
                    + "\"message\":\"A pull request already exists for octocat:feature.\"}]}");
        });

        ApiException ex = assertThrows(ApiException.class,
                () -> client().createPullRequest(new Repo("octocat", "hello"),
                        "t", "feature", "main", "b", false));
        assertEquals(ApiException.EXIT_API, ex.exitCode());
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void unauthorizedMapsToAuthExit() {
        route("GET /repos/octocat/hello/pulls/1", ex ->
                send(ex, 401, "{\"message\":\"Bad credentials\"}"));

        ApiException ex = assertThrows(ApiException.class,
                () -> client().viewPullRequest(new Repo("octocat", "hello"), 1));
        assertEquals(ApiException.EXIT_AUTH, ex.exitCode());
    }

    @Test
    void reviewThreadsPaginate() {
        // First page hasNextPage=true, second page hasNextPage=false.
        int[] calls = {0};
        route("POST /graphql", ex -> {
            try {
                ex.getRequestBody().readAllBytes();
            } catch (IOException ignored) {
            }
            calls[0]++;
            String body;
            if (calls[0] == 1) {
                body = "{\"data\":{\"repository\":{\"pullRequest\":{\"reviewThreads\":{"
                        + "\"pageInfo\":{\"hasNextPage\":true,\"endCursor\":\"C1\"},"
                        + "\"nodes\":[{\"id\":\"T1\",\"isResolved\":false,\"isOutdated\":false,"
                        + "\"comments\":{\"nodes\":[{\"id\":\"c1\",\"databaseId\":100,"
                        + "\"author\":{\"login\":\"rev\"},\"body\":\"fix this\",\"path\":\"A.java\","
                        + "\"line\":10,\"originalLine\":10,\"createdAt\":\"now\",\"url\":\"u\"}]}}]"
                        + "}}}}}";
            } else {
                body = "{\"data\":{\"repository\":{\"pullRequest\":{\"reviewThreads\":{"
                        + "\"pageInfo\":{\"hasNextPage\":false,\"endCursor\":null},"
                        + "\"nodes\":[{\"id\":\"T2\",\"isResolved\":true,\"isOutdated\":true,"
                        + "\"comments\":{\"nodes\":[]}}]"
                        + "}}}}}";
            }
            try {
                send(ex, 200, body);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        List<ReviewThread> threads = client().listReviewThreads(new Repo("octocat", "hello"), 7);
        assertEquals(2, calls[0]);
        assertEquals(2, threads.size());
        assertEquals("T1", threads.get(0).id());
        assertFalse(threads.get(0).resolved());
        assertEquals("A.java:10", threads.get(0).location());
        assertTrue(threads.get(1).resolved());
        assertTrue(threads.get(1).outdated());
    }

    @Test
    void graphqlErrorsSurface() {
        route("POST /graphql", ex -> {
            try {
                ex.getRequestBody().readAllBytes();
            } catch (IOException ignored) {
            }
            send(ex, 200, "{\"data\":null,\"errors\":[{\"message\":\"Could not resolve to a node.\"}]}");
        });

        ApiException ex = assertThrows(ApiException.class,
                () -> client().resolveThread("bad-id"));
        assertEquals(ApiException.EXIT_API, ex.exitCode());
        assertTrue(ex.getMessage().contains("Could not resolve to a node"));
    }

    @Test
    void editPullRequestSuccess() throws IOException {
        route("PATCH /repos/octocat/hello/pulls/3", ex -> {
            assertEquals("PATCH", ex.getRequestMethod());
            ex.getRequestBody().readAllBytes();
            send(ex, 200, "{\"number\":3,\"title\":\"New title\",\"state\":\"closed\","
                    + "\"base\":{\"ref\":\"main\"},\"html_url\":\"https://github.com/octocat/hello/pull/3\"}");
        });

        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("title", "New title");
        fields.put("state", "closed");
        PullRequest pr = client().editPullRequest(new Repo("octocat", "hello"), 3, fields);
        assertEquals(3, pr.number());
        assertEquals("New title", pr.title());
        assertEquals("closed", pr.state());
    }

    @Test
    void listPaginatesBeyondOnePage() {
        // limit=150 must fetch two pages (100 + 50) and honor the limit.
        route("GET /repos/octocat/hello/pulls", ex -> {
            String query = ex.getRequestURI().getQuery();
            boolean page2 = query != null && query.contains("page=2");
            StringBuilder sb = new StringBuilder("[");
            int count = page2 ? 100 : 100; // 100 on page 1, 100 available on page 2
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append("{\"number\":").append(page2 ? 100 + i : i).append("}");
            }
            sb.append("]");
            try {
                send(ex, 200, sb.toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        List<PullRequest> prs = client().listPullRequests(new Repo("octocat", "hello"), "open", 150);
        assertEquals(150, prs.size());
    }

    @Test
    void issueCommentSuccess() throws IOException {
        route("POST /repos/octocat/hello/issues/5/comments", ex -> {
            ex.getRequestBody().readAllBytes();
            send(ex, 201, "{\"html_url\":\"https://github.com/octocat/hello/pull/5#issuecomment-1\"}");
        });
        String url = client().addIssueComment(new Repo("octocat", "hello"), 5, "hi");
        assertNotNull(url);
        assertTrue(url.contains("issuecomment"));
    }
}
