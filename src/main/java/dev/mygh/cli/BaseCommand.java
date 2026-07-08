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
            warnIfArgvEncodingLossy(body, "--body",
                    "pass it via a file: --body-file <path> (or '-' for stdin)");
            return body;
        }
        if (required) {
            throw new ParameterException(spec.commandLine(),
                    "missing body: provide --body <text> or --body-file <path>");
        }
        return null;
    }

    /**
     * Warn (once, to stderr) when a command-line argument likely lost non-ASCII
     * characters to the platform's argv decoding. On Windows the JVM launcher decodes
     * argv with the legacy ANSI code page (sun.jnu.encoding, e.g. Cp1252), turning any
     * unmappable character into '?' before our code ever runs — this cannot be recovered
     * in-process, so we point the caller at the lossless --body-file / stdin path.
     */
    protected void warnIfArgvEncodingLossy(String value, String optName, String remedy) {
        if (value == null || value.indexOf('?') < 0) {
            return; // no '?' -> nothing to suspect
        }
        String jnu = System.getProperty("sun.jnu.encoding", "");
        if (jnu.equalsIgnoreCase("UTF-8") || jnu.equalsIgnoreCase("UTF8")) {
            return; // argv is decoded losslessly on this platform
        }
        System.err.println("warning: " + optName + " contains '?' and this JVM decodes command-line "
                + "arguments as " + jnu + " (not UTF-8), so non-ASCII characters may have been "
                + "corrupted. For non-ASCII text " + remedy + ".");
    }
}
