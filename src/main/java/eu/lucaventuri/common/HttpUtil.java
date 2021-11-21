package eu.lucaventuri.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class HttpUtil {
    /** Timeout, in ms, to wait before stopping trying to open a new connection */
    public static final int TIMEOUT_OPEN_CONNECTION_MS = 120_000;
    /** Timeout, in ms, to wait before stopping trying to read from a connection */
    public static final int TIMEOUT_READ_MS = 180_000;
    /** Maximum time allowed before interrupting the operation */
    // FIXME: two timeouts?
    public static final int TIMEOUT_MAX_MS = 3600_000;

    public static HttpResponse<String> get(String uri, int timeoutSeconds) throws IOException, InterruptedException, URISyntaxException {
        return get(new URI(uri), timeoutSeconds);
    }

    public static HttpResponse<String> get(URI uri, int timeoutSeconds) throws IOException, InterruptedException {
        HttpClient client = getHttpClient(timeoutSeconds);

        var request = HttpRequest.newBuilder()
                .uri(uri).GET().build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<byte[]> getBytes(String uri, int timeoutSeconds) throws IOException, InterruptedException, URISyntaxException {
        return getBytes(new URI(uri), timeoutSeconds);
    }

    public static HttpResponse<byte[]> getBytes(URI uri, int timeoutSeconds) throws IOException, InterruptedException {
        HttpClient client = getHttpClient(timeoutSeconds);

        var request = HttpRequest.newBuilder()
                .uri(uri).GET().build();

        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    public static CompletableFuture<HttpResponse<String>> getAsync(String uri, int timeoutSeconds) throws IOException, InterruptedException, URISyntaxException {
        return getAsync(new URI(uri), timeoutSeconds);
    }

    public static CompletableFuture<HttpResponse<String>> getAsync(URI uri, int timeoutSeconds) throws IOException, InterruptedException {
        HttpClient client = getHttpClient(timeoutSeconds);

        var request = HttpRequest.newBuilder()
                .uri(uri).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public static CompletableFuture<HttpResponse<byte[]>> getBytesAsync(String uri, int timeoutSeconds) throws IOException, InterruptedException, URISyntaxException {
        return getBytesAsync(new URI(uri), timeoutSeconds);
    }

    public static CompletableFuture<HttpResponse<byte[]>> getBytesAsync(URI uri, int timeoutSeconds) throws IOException, InterruptedException {
        HttpClient client = getHttpClient(timeoutSeconds);

        var request = HttpRequest.newBuilder()
                .uri(uri).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    public static HttpClient getHttpClient(int timeoutSeconds) {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        return client;
    }

    /**
     * Retrieve the content of a URL
     *
     * @param url URL to retrieve
     * @param maxSize Maximum allowed size, after which it will stop
     * @return the content read from the network
     * @throws IOException
     */
    public static byte[] retrieveURLContent(URL url, int maxSize) throws IOException {
        // FIXME: deal with HTTP errors!
        // TODO: logs
        URLConnection conn = null;
        @SuppressWarnings("resource")
        InputStream is = null;
        ByteArrayOutputStream buffer;

        try {
            conn = url.openConnection();

            conn.setConnectTimeout(TIMEOUT_OPEN_CONNECTION_MS);
            conn.setReadTimeout(TIMEOUT_READ_MS);

            long start = System.currentTimeMillis();

            conn.connect();
            is = conn.getInputStream();

            buffer = new ByteArrayOutputStream();
            int n;

            byte readBuffer[] = new byte[16384];
            int totalBytesRead = 0;

            while (totalBytesRead < maxSize
                    && (n = is.read(readBuffer, 0, Math.min(readBuffer.length, maxSize - totalBytesRead))) >= 0) {
                if (n > 0)
                    totalBytesRead += n;

                buffer.write(readBuffer, 0, n);

                if (System.currentTimeMillis() > start + TIMEOUT_MAX_MS)
                    throw new InterruptedIOException("Timeout expired on web service");
            }
        } finally {
            if (null != is)
                is.close();
        }
        byte result[] = buffer.toByteArray();

        return result;
    }
}
