package dev.mygh.core;

import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves runtime configuration from environment variables:
 * token, host, REST/GraphQL URLs, default repo, proxy and CA bundle.
 *
 * See spec section 5.
 */
public final class Config {

    public static final String VERSION = "0.1.0";

    private final String token;
    private final String host;
    private final String restUrl;
    private final String graphqlUrl;
    private final String defaultRepo;   // may be null
    private final String caBundlePath;  // may be null

    private Config(String token, String host, String restUrl, String graphqlUrl,
                   String defaultRepo, String caBundlePath) {
        this.token = token;
        this.host = host;
        this.restUrl = restUrl;
        this.graphqlUrl = graphqlUrl;
        this.defaultRepo = defaultRepo;
        this.caBundlePath = caBundlePath;
    }

    /** Build a Config from the process environment. */
    public static Config fromEnv() {
        return fromEnv(System::getenv);
    }

    /** Build a Config from an arbitrary environment lookup (testable). */
    public static Config fromEnv(Function<String, String> env) {
        String token = firstNonBlank(env.apply("GH_TOKEN"), env.apply("GITHUB_TOKEN"));

        String host = orDefault(env.apply("GH_HOST"), "github.com");
        boolean dotCom = host.equalsIgnoreCase("github.com");

        String restUrl = orDefault(env.apply("GH_REST_URL"),
                dotCom ? "https://api.github.com" : "https://" + host + "/api/v3");
        String graphqlUrl = orDefault(env.apply("GH_GRAPHQL_URL"),
                dotCom ? "https://api.github.com/graphql" : "https://" + host + "/api/graphql");

        String defaultRepo = blankToNull(env.apply("GH_REPO"));
        String caBundle = blankToNull(env.apply("GH_CA_BUNDLE"));

        // Strip a trailing slash so URL concatenation stays clean.
        restUrl = stripTrailingSlash(restUrl);
        graphqlUrl = stripTrailingSlash(graphqlUrl);

        return new Config(token, host, restUrl, graphqlUrl, defaultRepo, caBundle);
    }

    public Optional<String> token() {
        return Optional.ofNullable(blankToNull(token));
    }

    /** Token or a friendly auth error if absent. */
    public String requireToken() {
        return token().orElseThrow(() -> ApiException.auth(
                "no token found: set GH_TOKEN or GITHUB_TOKEN (a GitHub personal access token)"));
    }

    public String host() {
        return host;
    }

    public String restUrl() {
        return restUrl;
    }

    public String graphqlUrl() {
        return graphqlUrl;
    }

    public Optional<String> defaultRepo() {
        return Optional.ofNullable(defaultRepo);
    }

    public Optional<String> caBundlePath() {
        return Optional.ofNullable(caBundlePath);
    }

    // ---- helpers ----

    private static String firstNonBlank(String a, String b) {
        String an = blankToNull(a);
        return an != null ? an : blankToNull(b);
    }

    private static String orDefault(String v, String def) {
        String n = blankToNull(v);
        return n != null ? n : def;
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String stripTrailingSlash(String v) {
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
