package dev.mygh.core;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Builds a java.net.http.HttpClient wired for the corporate environment:
 *  - honors HTTPS_PROXY / HTTP_PROXY / NO_PROXY (the JVM does NOT read these itself);
 *  - installs a custom SSLContext from GH_CA_BUNDLE (Zscaler root PEM) when provided.
 *
 * See spec sections 2 and 5.
 */
public final class HttpClientFactory {

    private HttpClientFactory() {
    }

    public static HttpClient create(Config config) {
        return create(config, System::getenv);
    }

    public static HttpClient create(Config config, Function<String, String> env) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL);

        proxySelector(env).ifPresent(builder::proxy);
        config.caBundlePath().ifPresent(path -> builder.sslContext(sslContextFromPem(path)));

        return builder.build();
    }

    // ---- proxy ----

    static java.util.Optional<ProxySelector> proxySelector(Function<String, String> env) {
        String httpsProxy = firstNonBlank(env.apply("HTTPS_PROXY"), env.apply("https_proxy"));
        String httpProxy = firstNonBlank(env.apply("HTTP_PROXY"), env.apply("http_proxy"));
        String noProxy = firstNonBlank(env.apply("NO_PROXY"), env.apply("no_proxy"));

        // We talk HTTPS to the API, so HTTPS_PROXY takes priority; fall back to HTTP_PROXY.
        String proxy = httpsProxy != null ? httpsProxy : httpProxy;
        if (proxy == null) {
            return java.util.Optional.empty();
        }
        InetSocketAddress addr = parseProxy(proxy);
        if (addr == null) {
            return java.util.Optional.empty();
        }
        List<String> noProxyHosts = new ArrayList<>();
        if (noProxy != null) {
            for (String h : noProxy.split(",")) {
                if (!h.isBlank()) {
                    noProxyHosts.add(h.trim().toLowerCase());
                }
            }
        }
        ProxySelector base = ProxySelector.of(addr);
        if (noProxyHosts.isEmpty()) {
            return java.util.Optional.of(base);
        }
        return java.util.Optional.of(new NoProxyAwareSelector(base, noProxyHosts));
    }

    static InetSocketAddress parseProxy(String value) {
        String v = value.trim();
        // Accept http://host:port, host:port, or bare host (default 80).
        String withoutScheme = v.replaceFirst("^[a-zA-Z]+://", "");
        // Drop any trailing path.
        int slash = withoutScheme.indexOf('/');
        if (slash >= 0) {
            withoutScheme = withoutScheme.substring(0, slash);
        }
        // Strip credentials if present (user:pass@host:port).
        int at = withoutScheme.lastIndexOf('@');
        if (at >= 0) {
            withoutScheme = withoutScheme.substring(at + 1);
        }
        int colon = withoutScheme.lastIndexOf(':');
        try {
            if (colon >= 0) {
                String host = withoutScheme.substring(0, colon);
                int port = Integer.parseInt(withoutScheme.substring(colon + 1));
                return new InetSocketAddress(host, port);
            }
            return new InetSocketAddress(withoutScheme, 80);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class NoProxyAwareSelector extends ProxySelector {
        private final ProxySelector delegate;
        private final List<String> noProxyHosts;

        NoProxyAwareSelector(ProxySelector delegate, List<String> noProxyHosts) {
            this.delegate = delegate;
            this.noProxyHosts = noProxyHosts;
        }

        @Override
        public List<java.net.Proxy> select(URI uri) {
            String host = uri.getHost();
            if (host != null) {
                String h = host.toLowerCase();
                for (String np : noProxyHosts) {
                    String suffix = np.startsWith(".") ? np : "." + np;
                    if (h.equals(np) || h.endsWith(suffix)) {
                        return List.of(java.net.Proxy.NO_PROXY);
                    }
                }
            }
            return delegate.select(uri);
        }

        @Override
        public void connectFailed(URI uri, java.net.SocketAddress sa, IOException ioe) {
            delegate.connectFailed(uri, sa, ioe);
        }
    }

    // ---- custom truststore from PEM ----

    static SSLContext sslContextFromPem(String pemPath) {
        try {
            Path path = Path.of(pemPath);
            if (!Files.exists(path)) {
                throw ApiException.auth("GH_CA_BUNDLE points to a missing file: " + pemPath);
            }
            byte[] pem = Files.readAllBytes(path);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection<? extends java.security.cert.Certificate> certs;
            try (var in = new java.io.ByteArrayInputStream(pem)) {
                certs = cf.generateCertificates(in);
            }
            if (certs.isEmpty()) {
                throw ApiException.auth("no certificates found in GH_CA_BUNDLE: " + pemPath);
            }

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            // Seed with the JDK default trust anchors so public CAs still validate.
            loadDefaultTrustAnchors(ks);
            int i = 0;
            for (java.security.cert.Certificate c : certs) {
                ks.setCertificateEntry("gh-ca-bundle-" + (i++), (X509Certificate) c);
            }

            TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.auth("failed to load GH_CA_BUNDLE '" + pemPath + "': " + e.getMessage());
        }
    }

    private static void loadDefaultTrustAnchors(KeyStore target) {
        try {
            TrustManagerFactory def =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            def.init((KeyStore) null);
            for (var tm : def.getTrustManagers()) {
                if (tm instanceof javax.net.ssl.X509TrustManager x509) {
                    int i = 0;
                    for (X509Certificate anchor : x509.getAcceptedIssuers()) {
                        target.setCertificateEntry("jdk-anchor-" + (i++), anchor);
                    }
                }
            }
        } catch (Exception e) {
            // If default anchors cannot be loaded we still proceed with just the custom CA.
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }
}
