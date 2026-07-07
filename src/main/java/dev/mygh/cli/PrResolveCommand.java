package dev.mygh.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "resolve", mixinStandardHelpOptions = true,
        description = "Resolve a review thread.")
public final class PrResolveCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<number>", description = "PR number")
    int number;

    @Option(names = "--thread", required = true, paramLabel = "<threadId>",
            description = "Review thread node id")
    String threadId;

    @Override
    public Integer call() {
        boolean resolved = client().resolveThread(threadId);
        out().println("Thread " + threadId + " " + (resolved ? "resolved" : "not resolved") + ".");
        return resolved ? 0 : 1;
    }
}
