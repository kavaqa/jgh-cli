package dev.mygh.cli;

import dev.mygh.core.Json;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.LinkedHashMap;
import java.util.Map;

@Command(name = "comment", mixinStandardHelpOptions = true,
        description = "Add a regular (non-inline) issue comment to a PR.")
public final class PrCommentCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<number>", description = "PR number")
    int number;

    @Option(names = "--body", description = "Comment body")
    String body;

    @Option(names = "--body-file", paramLabel = "<path>",
            description = "Read comment body from a file (\"-\" for stdin)")
    String bodyFile;

    @Option(names = "--json", description = "Output the result as JSON")
    boolean json;

    @Override
    public Integer call() {
        String url = client().addIssueComment(repo(), number, resolveBody(body, bodyFile, true));
        if (json) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("number", number);
            m.put("url", url);
            out().println(Json.writePretty(m));
        } else {
            out().println("Comment added" + (url != null ? ": " + url : "") + ".");
        }
        return 0;
    }
}
