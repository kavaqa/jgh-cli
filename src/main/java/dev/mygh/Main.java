package dev.mygh;

import dev.mygh.cli.PrCommand;
import dev.mygh.core.ApiException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

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
        // Force UTF-8 on stdout/stderr so JSON output with non-ASCII (e.g. review bodies
        // in Cyrillic) is not mangled by a legacy console codepage on Windows. An agent
        // parsing our output depends on this.
        PrintStream utf8Out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream utf8Err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        System.setOut(utf8Out);
        System.setErr(utf8Err);

        CommandLine cmd = new CommandLine(new Main());
        cmd.setOut(new PrintWriter(utf8Out, true));
        cmd.setErr(new PrintWriter(utf8Err, true));
        cmd.setExecutionExceptionHandler(new MyghExceptionHandler());
        cmd.setParameterExceptionHandler(new MyghParameterExceptionHandler());
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        int code = cmd.execute(args);
        System.exit(code);
    }

    /**
     * Maps thrown exceptions to friendly messages and the exit codes from spec sections 4/8.
     * Error lines carry a stable category tag — {@code error [category]: message} — so an
     * agent can branch on the category instead of matching message text.
     */
    static final class MyghExceptionHandler implements IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(Exception ex, CommandLine cmd, ParseResult parseResult) {
            PrintWriter err = cmd.getErr();
            if (ex instanceof ApiException apiEx) {
                err.println("error [" + apiEx.category() + "]: " + apiEx.getMessage());
                return apiEx.exitCode();
            }
            // Unexpected failure: surface the message, exit 1.
            err.println("error [internal]: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
            return ApiException.EXIT_API;
        }
    }

    /**
     * Usage / argument errors (both parse-time and those thrown during execution)
     * are rendered with the same {@code error [usage]: ...} shape and exit code 2,
     * instead of picocli's verbose default, to keep the error contract uniform.
     */
    static final class MyghParameterExceptionHandler implements CommandLine.IParameterExceptionHandler {
        @Override
        public int handleParseException(CommandLine.ParameterException ex, String[] args) {
            CommandLine cmd = ex.getCommandLine();
            PrintWriter err = cmd.getErr();
            err.println("error [usage]: " + ex.getMessage());
            err.println("run '" + cmd.getCommandSpec().qualifiedName() + " --help' for usage.");
            return CommandLine.ExitCode.USAGE;
        }
    }
}
