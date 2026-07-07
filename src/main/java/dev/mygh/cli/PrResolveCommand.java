package dev.mygh.cli;

import dev.mygh.core.Json;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.LinkedHashMap;
import java.util.Map;

@Command(name = "resolve", mixinStandardHelpOptions = true,
        description = "Resolve a review thread.")
public final class PrResolveCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<number>", description = "PR number")
    int number;

    @Option(names = "--thread", required = true, paramLabel = "<threadId>",
            description = "Review thread node id")
    String threadId;

    @Option(names = "--json", description = "Output the result as JSON")
    boolean json;

    @Override
    public Integer call() {
        boolean resolved = client().resolveThread(threadId);
        if (json) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("threadId", threadId);
            m.put("resolved", resolved);
            out().println(Json.writePretty(m));
        } else {
            out().println("Thread " + threadId + " " + (resolved ? "resolved" : "not resolved") + ".");
        }
        return resolved ? 0 : 1;
    }
}
