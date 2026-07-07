package dev.mygh.core;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;

/**
 * Translates low-level networking exceptions into user-facing {@link ApiException}s
 * with actionable hints for the corporate proxy / TLS-interception environment
 * (spec section 8).
 */
public final class NetworkErrors {

    private NetworkErrors() {
    }

    public static ApiException translate(IOException e) {
        if (isTlsTrustProblem(e)) {
            return ApiException.of("tls",
                    "TLS handshake failed (" + rootMessage(e) + ").\n"
                    + "You appear to be behind a TLS-intercepting proxy (e.g. Zscaler). "
                    + "Export its root CA and point GH_CA_BUNDLE at the PEM file, "
                    + "or import the root into the JDK cacerts truststore.", ApiException.EXIT_AUTH);
        }
        if (e instanceof UnknownHostException) {
            return ApiException.of("network",
                    "cannot resolve host (" + e.getMessage() + ").\n"
                    + "This environment is likely behind a proxy — check HTTPS_PROXY / HTTP_PROXY.",
                    ApiException.EXIT_API);
        }
        if (e instanceof ConnectException) {
            return ApiException.of("network",
                    "connection refused/failed (" + e.getMessage() + ").\n"
                    + "Check the proxy settings in HTTPS_PROXY / HTTP_PROXY.", ApiException.EXIT_API);
        }
        return ApiException.of("network", "network error: " + rootMessage(e), ApiException.EXIT_API);
    }

    private static boolean isTlsTrustProblem(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SSLException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("pkix path building failed")
                        || lower.contains("unable to find valid certification path")
                        || lower.contains("self signed certificate")
                        || lower.contains("self-signed certificate")) {
                    return true;
                }
            }
            if (t.getClass().getName().contains("sun.security.validator")) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
