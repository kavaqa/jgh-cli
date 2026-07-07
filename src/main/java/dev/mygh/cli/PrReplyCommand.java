package dev.mygh.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "reply", mixinStandardHelpOptions = true,
        description = "Reply to a review thread; optionally resolve it.")
public final class PrReplyCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<number>", description = "PR number")
    int number;

    @Option(names = "--thread", required = true, paramLabel = "<threadId>",
            description = "Review thread node id (from `pr threads`)")
    String threadId;

    @Option(names = "--body", description = "Reply body")
    String body;

    @Option(names = "--body-file", paramLabel = "<path>",
            description = "Read reply body from a file (\"-\" for stdin)")
    String bodyFile;

    @Option(names = "--resolve", description = "Resolve the thread after replying")
    boolean resolve;

    @Override
    public Integer call() {
        String url = client().addReviewThreadReply(threadId, resolveBody(body, bodyFile, true));
        out().println("Replied to thread " + threadId + (url != null ? ": " + url : ""));
        if (resolve) {
            boolean resolved = client().resolveThread(threadId);
            out().println("Thread " + (resolved ? "resolved" : "not resolved") + ".");
        }
        return 0;
    }
}
