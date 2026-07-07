package dev.mygh.cli;

import dev.mygh.core.ApiException;
import dev.mygh.core.Config;
import dev.mygh.core.GitHubClient;
import dev.mygh.core.RepoResolver;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Shared plumbing for the pr subcommands: --repo/--json options,
 * lazy Config/GitHubClient/Repo resolution and stdout access.
 */
public abstract class BaseCommand implements Callable<Integer> {

    @Option(names = "--repo", paramLabel = "owner/name",
            description = "Repository, overriding git remote / GH_REPO detection")
    protected String repoOption;

    @Spec
    protected CommandSpec spec;

    private Config config;
    private GitHubClient client;

    protected Config config() {
        if (config == null) {
            config = Config.fromEnv();
        }
        return config;
    }

    protected GitHubClient client() {
        if (client == null) {
            client = GitHubClient.fromEnv(config());
        }
        return client;
    }

    protected RepoResolver.Repo repo() {
        return new RepoResolver(config()).resolve(repoOption);
    }

    protected PrintStream out() {
        return System.out;
    }

    /**
     * Resolve a body from either --body or --body-file (path, or "-" for stdin).
     *
     * @param required if true, throws a usage error (exit 2) when neither is provided
     */
    protected String resolveBody(String body, String bodyFile, boolean required) {
        if (body != null && bodyFile != null) {
            throw new ParameterException(spec.commandLine(),
                    "specify only one of --body or --body-file");
        }
        if (bodyFile != null) {
            try {
                if (bodyFile.equals("-")) {
                    return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
                }
                return Files.readString(Path.of(bodyFile));
            } catch (IOException e) {
                throw ApiException.api("failed to read --body-file '" + bodyFile + "': " + e.getMessage());
            }
        }
        if (body != null) {
            return body;
        }
        if (required) {
            throw new ParameterException(spec.commandLine(),
                    "missing body: provide --body <text> or --body-file <path>");
        }
        return null;
    }
}
