package dev.mygh.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "unresolve", mixinStandardHelpOptions = true,
        description = "Unresolve a review thread.")
public final class PrUnresolveCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<number>", description = "PR number")
    int number;

    @Option(names = "--thread", required = true, paramLabel = "<threadId>",
            description = "Review thread node id")
    String threadId;

    @Override
    public Integer call() {
        boolean resolved = client().unresolveThread(threadId);
        out().println("Thread " + threadId + " " + (resolved ? "still resolved" : "unresolved") + ".");
        return resolved ? 1 : 0;
    }
}
