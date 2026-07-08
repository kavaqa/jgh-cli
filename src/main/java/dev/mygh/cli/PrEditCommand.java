package dev.mygh.cli;

import dev.mygh.core.Json;
import dev.mygh.model.PullRequest;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;

import java.util.LinkedHashMap;
import java.util.Map;

@Command(name = "edit", mixinStandardHelpOptions = true,
        description = "Edit a pull request's title, body, base branch or open/closed state.")
public final class PrEditCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<number>", description = "PR number")
    int number;

    @Option(names = "--title", description = "New title")
    String title;

    @Option(names = "--body", description = "New body")
    String body;

    @Option(names = "--body-file", paramLabel = "<path>",
            description = "Read the new body from a file (\"-\" for stdin)")
    String bodyFile;

    @Option(names = "--base", description = "New base branch")
    String base;

    @Option(names = "--state", description = "New state: ${COMPLETION-CANDIDATES}")
    State state;

    @Option(names = "--json", description = "Output the updated PR as JSON")
    boolean json;

    enum State {open, closed}

    @Override
    public Integer call() {
        String resolvedBody = resolveBody(body, bodyFile, false);
        if (title != null) {
            warnIfArgvEncodingLossy(title, "--title",
                    "keep the title ASCII, or enable UTF-8 for argv (see README)");
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        if (title != null) {
            fields.put("title", title);
        }
        if (resolvedBody != null) {
            fields.put("body", resolvedBody);
        }
        if (base != null) {
            fields.put("base", base);
        }
        if (state != null) {
            fields.put("state", state.name());
        }
        if (fields.isEmpty()) {
            throw new ParameterException(spec.commandLine(),
                    "nothing to edit: pass at least one of --title, --body/--body-file, --base, --state");
        }

        PullRequest pr = client().editPullRequest(repo(), number, fields);
        if (json) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("number", pr.number());
            m.put("title", pr.title());
            m.put("state", pr.state());
            m.put("baseRef", pr.baseRef());
            m.put("url", pr.htmlUrl());
            out().println(Json.writePretty(m));
        } else {
            out().printf("Updated PR #%d (%s): %s%n", pr.number(), pr.state(), pr.htmlUrl());
        }
        return 0;
    }
}
