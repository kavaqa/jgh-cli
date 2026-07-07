package dev.mygh.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "comment", mixinStandardHelpOptions = true,
        description = "Add a regular (non-inline) issue comment to a PR.")
public final class PrCommentCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<number>", description = "PR number")
    int number;

    @Option(names = "--body", required = true, description = "Comment body")
    String body;

    @Override
    public Integer call() {
        String url = client().addIssueComment(repo(), number, body);
        out().println("Comment added" + (url != null ? ": " + url : "") + ".");
        return 0;
    }
}
