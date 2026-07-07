package dev.mygh.cli;

import dev.mygh.core.Config;
import dev.mygh.core.GitHubClient;
import dev.mygh.core.RepoResolver;
import picocli.CommandLine.Option;

import java.io.PrintStream;
import java.util.concurrent.Callable;

/**
 * Shared plumbing for the pr subcommands: --repo/--json options,
 * lazy Config/GitHubClient/Repo resolution and stdout access.
 */
public abstract class BaseCommand implements Callable<Integer> {

    @Option(names = "--repo", paramLabel = "owner/name",
            description = "Repository, overriding git remote / GH_REPO detection")
    protected String repoOption;

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
}
