package dev.mygh.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

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

    @Override
    public Integer call() {
        String url = client().addIssueComment(repo(), number, resolveBody(body, bodyFile, true));
        out().println("Comment added" + (url != null ? ": " + url : "") + ".");
        return 0;
    }
}
