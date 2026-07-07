package dev.mygh.cli;

import dev.mygh.core.Json;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.LinkedHashMap;
import java.util.Map;

@Command(name = "unresolve", mixinStandardHelpOptions = true,
        description = "Unresolve a review thread.")
public final class PrUnresolveCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<number>", description = "PR number")
    int number;

    @Option(names = "--thread", required = true, paramLabel = "<threadId>",
            description = "Review thread node id")
    String threadId;

    @Option(names = "--json", description = "Output the result as JSON")
    boolean json;

    @Override
    public Integer call() {
        boolean resolved = client().unresolveThread(threadId);
        if (json) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("threadId", threadId);
            m.put("resolved", resolved);
            out().println(Json.writePretty(m));
        } else {
            out().println("Thread " + threadId + " " + (resolved ? "still resolved" : "unresolved") + ".");
        }
        return resolved ? 1 : 0;
    }
}
