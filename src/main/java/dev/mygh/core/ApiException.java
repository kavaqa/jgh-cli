package dev.mygh.core;

/**
 * Represents an error surfaced to the user with a fixed process exit code.
 *
 * Exit codes follow the task spec:
 *   1 - API / validation error
 *   2 - invalid arguments (handled by picocli)
 *   4 - authentication / SSO problem
 */
public class ApiException extends RuntimeException {

    public static final int EXIT_API = 1;
    public static final int EXIT_AUTH = 4;

    private final int exitCode;

    public ApiException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }

    public static ApiException api(String message) {
        return new ApiException(message, EXIT_API);
    }

    public static ApiException auth(String message) {
        return new ApiException(message, EXIT_AUTH);
    }
}
