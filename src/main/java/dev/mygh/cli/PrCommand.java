package dev.mygh.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * `mygh pr <command>` group.
 */
@Command(name = "pr",
        mixinStandardHelpOptions = true,
        description = "Work with pull requests and review threads.",
        subcommands = {
                PrCreateCommand.class,
                PrListCommand.class,
                PrViewCommand.class,
                PrEditCommand.class,
                PrThreadsCommand.class,
                PrReplyCommand.class,
                PrResolveCommand.class,
                PrUnresolveCommand.class,
                PrCommentCommand.class,
        })
public final class PrCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
