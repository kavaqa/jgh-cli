package dev.mygh.cli;

import dev.mygh.core.Json;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.LinkedHashMap;
import java.util.Map;

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

    @Option(names = "--json", description = "Output the result as JSON")
    boolean json;

    @Override
    public Integer call() {
        String url = client().addReviewThreadReply(threadId, resolveBody(body, bodyFile, true));
        Boolean resolved = null;
        if (resolve) {
            resolved = client().resolveThread(threadId);
        }
        if (json) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("threadId", threadId);
            m.put("replyUrl", url);
            m.put("resolved", resolved);
            out().println(Json.writePretty(m));
        } else {
            out().println("Replied to thread " + threadId + (url != null ? ": " + url : ""));
            if (resolved != null) {
                out().println("Thread " + (resolved ? "resolved" : "not resolved") + ".");
            }
        }
        return 0;
    }
}
