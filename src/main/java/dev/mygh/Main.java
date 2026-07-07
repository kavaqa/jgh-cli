package dev.mygh;

import dev.mygh.cli.PrCommand;
import dev.mygh.core.ApiException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

import java.io.PrintWriter;

/**
 * Root command: `mygh <group> <command> [flags]`.
 * Only the `pr` group is implemented (spec section 4).
 */
@Command(name = "mygh",
        mixinStandardHelpOptions = true,
        version = "mygh " + dev.mygh.core.Config.VERSION,
        description = "Minimal gh-compatible CLI for pull request review workflows.",
        subcommands = {PrCommand.class})
public final class Main implements Runnable {

    @Override
    public void run() {
        // No subcommand -> show help.
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new Main());
        cmd.setExecutionExceptionHandler(new MyghExceptionHandler());
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        int code = cmd.execute(args);
        System.exit(code);
    }

    /**
     * Maps thrown exceptions to friendly messages and the exit codes from spec sections 4/8.
     */
    static final class MyghExceptionHandler implements IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(Exception ex, CommandLine cmd, ParseResult parseResult) {
            PrintWriter err = cmd.getErr();
            if (ex instanceof ApiException apiEx) {
                err.println("error: " + apiEx.getMessage());
                return apiEx.exitCode();
            }
            // Unexpected failure: surface the message, exit 1.
            err.println("error: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
            return ApiException.EXIT_API;
        }
    }
}
