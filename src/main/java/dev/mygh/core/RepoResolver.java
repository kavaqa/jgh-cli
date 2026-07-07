package dev.mygh.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the target repository (owner/name).
 *
 * Order: explicit --repo flag → GH_REPO env → `git remote get-url origin`.
 * Both SSH and HTTPS remote formats are supported (spec section 5).
 */
public final class RepoResolver {

    /** owner/name pair. */
    public record Repo(String owner, String name) {
        @Override
        public String toString() {
            return owner + "/" + name;
        }
    }

    // git@github.com:owner/repo(.git)   or   ssh://git@host/owner/repo(.git)
    private static final Pattern SCP_LIKE =
            Pattern.compile("^(?:ssh://)?[^@]+@[^:/]+[:/](?<owner>[^/]+)/(?<name>[^/]+?)(?:\\.git)?/?$");
    // https://github.com/owner/repo(.git)  or  http://...
    private static final Pattern HTTP_LIKE =
            Pattern.compile("^https?://[^/]+/(?<owner>[^/]+)/(?<name>[^/]+?)(?:\\.git)?/?$");

    private final Config config;

    public RepoResolver(Config config) {
        this.config = config;
    }

    /**
     * Resolve the repo, honoring an explicit override first.
     *
     * @param explicit value of --repo (may be null)
     */
    public Repo resolve(String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return parseOwnerName(explicit)
                    .orElseThrow(() -> ApiException.of("config",
                            "invalid --repo value '" + explicit + "', expected owner/name",
                            ApiException.EXIT_API));
        }
        if (config.defaultRepo().isPresent()) {
            return parseOwnerName(config.defaultRepo().get())
                    .orElseThrow(() -> ApiException.of("config",
                            "invalid GH_REPO value '" + config.defaultRepo().get() + "', expected owner/name",
                            ApiException.EXIT_API));
        }
        Optional<Repo> fromGit = fromGitRemote();
        return fromGit.orElseThrow(() -> ApiException.of("config",
                "could not determine repository: pass --repo owner/name, set GH_REPO, "
                        + "or run inside a git repo with a github remote", ApiException.EXIT_API));
    }

    /** Parse a plain "owner/name" string. */
    public static Optional<Repo> parseOwnerName(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.trim();
        int slash = v.indexOf('/');
        if (slash <= 0 || slash == v.length() - 1 || v.indexOf('/', slash + 1) >= 0) {
            return Optional.empty();
        }
        return Optional.of(new Repo(v.substring(0, slash), v.substring(slash + 1)));
    }

    /** Parse a git remote URL (SSH or HTTPS form) into owner/name. */
    public static Optional<Repo> parseRemoteUrl(String url) {
        if (url == null) {
            return Optional.empty();
        }
        String u = url.trim();
        Matcher m = HTTP_LIKE.matcher(u);
        if (m.matches()) {
            return Optional.of(new Repo(m.group("owner"), m.group("name")));
        }
        m = SCP_LIKE.matcher(u);
        if (m.matches()) {
            return Optional.of(new Repo(m.group("owner"), m.group("name")));
        }
        return Optional.empty();
    }

    private Optional<Repo> fromGitRemote() {
        for (String[] cmd : List.of(
                new String[]{"git", "remote", "get-url", "origin"},
                new String[]{"git", "remote", "get-url", "--all", "origin"})) {
            Optional<String> out = runGit(cmd);
            if (out.isPresent()) {
                Optional<Repo> repo = parseRemoteUrl(out.get());
                if (repo.isPresent()) {
                    return repo;
                }
            }
        }
        // Fallback: first configured remote of any name.
        Optional<String> remotes = runGit(new String[]{"git", "remote"});
        if (remotes.isPresent()) {
            for (String remote : remotes.get().split("\\R")) {
                if (remote.isBlank()) {
                    continue;
                }
                Optional<String> url = runGit(new String[]{"git", "remote", "get-url", remote.trim()});
                if (url.isPresent()) {
                    Optional<Repo> repo = parseRemoteUrl(url.get());
                    if (repo.isPresent()) {
                        return repo;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> runGit(String[] cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            String out;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                out = r.readLine();
            }
            p.getErrorStream().readAllBytes();
            boolean done = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return Optional.empty();
            }
            if (p.exitValue() != 0 || out == null || out.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(out.trim());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
