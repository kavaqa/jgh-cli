package dev.mygh;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;

/**
 * Prints the full usage of every command, ANSI-free. Useful as a single-call
 * reference for agents/tooling, and used at build time to generate HELP.txt.
 */
@Command(name = "help", hidden = true,
        description = "Print full usage for every command.")
final class HelpCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        CommandLine root = spec.root().commandLine();
        PrintWriter out = root.getOut();
        print(root, out);
        out.flush();
    }

    private static void print(CommandLine cl, PrintWriter out) {
        out.println(cl.getUsageMessage(Ansi.OFF));
        for (CommandLine sub : cl.getSubcommands().values()) {
            if (!"help".equals(sub.getCommandName())) {
                print(sub, out);
            }
        }
    }
}
