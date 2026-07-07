package dev.mygh.core;

/**
 * Represents an error surfaced to the user with a fixed process exit code and a
 * stable, machine-readable category (so an agent can branch on the category
 * instead of pattern-matching the message text).
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
    private final String category;

    public ApiException(String message, int exitCode, String category) {
        super(message);
        this.exitCode = exitCode;
        this.category = category;
    }

    public int exitCode() {
        return exitCode;
    }

    /** Stable machine-readable category, e.g. "auth", "sso", "rate_limit", "not_found". */
    public String category() {
        return category;
    }

    // ---- factories ----

    public static ApiException of(String category, String message, int exitCode) {
        return new ApiException(message, exitCode, category);
    }

    /** Generic API/validation error (exit 1, category "api"). */
    public static ApiException api(String message) {
        return new ApiException(message, EXIT_API, "api");
    }

    /** Authentication problem (exit 4, category "auth"). */
    public static ApiException auth(String message) {
        return new ApiException(message, EXIT_AUTH, "auth");
    }
}
