package xin.v5ai.nb.workflow.core.executor;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Minimal HTTP/1.1 client that connects only to the addresses checked by HttpNodeExecutor. */
final class PinnedHttpTransport {
    private static final int MAX_HEADER_LINE = 8 * 1024;
    private static final int MAX_HEADERS = 64 * 1024;
    private static final int MAX_BODY = 1_048_576;
    private final SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();

    Response execute(URI uri, List<InetAddress> addresses, String method, Map<String, String> headers,
                     byte[] requestBody, Duration timeout) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        int port = uri.getPort() >= 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        IOException lastFailure = null;
        for (InetAddress address : addresses) {
            checkInterrupted();
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(address, port), remainingMillis(deadline));
            } catch (IOException e) {
                lastFailure = e;
                try { socket.close(); } catch (IOException ignored) { }
                if (System.nanoTime() >= deadline) throw new IOException("HTTP node request timed out", e);
                continue;
            }
            try {
                if ("https".equalsIgnoreCase(uri.getScheme())) socket = secure(socket, uri.getHost(), port, deadline);
                socket.setSoTimeout(remainingMillis(deadline));
                writeRequest(socket.getOutputStream(), uri, port, method, headers, requestBody);
                return readResponse(new DeadlineInputStream(socket, deadline), deadline);
            } finally {
                try { socket.close(); } catch (IOException ignored) { }
            }
        }
        throw lastFailure == null ? new IOException("HTTP node has no checked DNS address") : lastFailure;
    }

    private Socket secure(Socket connected, String hostname, int port, long deadline) throws IOException {
        SSLSocket tls = (SSLSocket) sslSocketFactory.createSocket(connected, hostname, port, true);
        SSLParameters parameters = tls.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        parameters.setServerNames(List.of(new SNIHostName(hostname)));
        tls.setSSLParameters(parameters);
        tls.setSoTimeout(remainingMillis(deadline));
        tls.startHandshake();
        return tls;
    }

    private void writeRequest(OutputStream output, URI uri, int port, String method,
                              Map<String, String> headers, byte[] body) throws IOException {
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
        String host = uri.getHost();
        if (port != ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80)) host += ":" + port;
        StringBuilder request = new StringBuilder(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                .append("Host: ").append(host).append("\r\n");
        headers.forEach((name, value) -> request.append(name).append(": ").append(value).append("\r\n"));
        request.append("Connection: close\r\nContent-Length: ").append(body.length).append("\r\n\r\n");
        output.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
        output.write(body);
        output.flush();
    }

    private Response readResponse(InputStream input, long deadline) throws IOException, InterruptedException {
        String statusLine;
        int status;
        Map<String, List<String>> responseHeaders;
        do {
            statusLine = readLine(input, deadline, MAX_HEADER_LINE);
            String[] parts = statusLine.split(" ", 3);
            if (parts.length < 2 || !parts[0].matches("HTTP/1\\.[01]")) throw new IOException("Invalid HTTP status line");
            try { status = Integer.parseInt(parts[1]); }
            catch (NumberFormatException e) { throw new IOException("Invalid HTTP status code", e); }
            responseHeaders = readHeaders(input, deadline);
        } while (status >= 100 && status < 200 && status != 101);
        if (status == 101) throw new IOException("HTTP protocol upgrades are not supported");
        byte[] body;
        if (status == 204 || status == 304 || status >= 100 && status < 200) body = new byte[0];
        else if (containsToken(responseHeaders.get("transfer-encoding"), "chunked")) body = readChunked(input, deadline);
        else {
            if (responseHeaders.containsKey("transfer-encoding")) throw new IOException("Unsupported HTTP transfer encoding");
            String length = first(responseHeaders.get("content-length"));
            body = length == null ? readToEnd(input, deadline) : readFixed(input, parseLength(length), deadline);
        }
        return new Response(status, responseHeaders, body);
    }

    private Map<String, List<String>> readHeaders(InputStream input, long deadline) throws IOException, InterruptedException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        int total = 0;
        while (true) {
            String line = readLine(input, deadline, MAX_HEADER_LINE);
            total += line.length() + 2;
            if (total > MAX_HEADERS) throw new IOException("HTTP response headers exceed 64 KiB");
            if (line.isEmpty()) return result;
            int colon = line.indexOf(':');
            if (colon < 1) throw new IOException("Invalid HTTP response header");
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            result.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
    }

    private byte[] readChunked(InputStream input, long deadline) throws IOException, InterruptedException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(input, deadline, MAX_HEADER_LINE);
            int extension = sizeLine.indexOf(';');
            String hex = (extension < 0 ? sizeLine : sizeLine.substring(0, extension)).trim();
            long size;
            try { size = Long.parseLong(hex, 16); }
            catch (NumberFormatException e) { throw new IOException("Invalid chunk size", e); }
            if (size < 0 || size > MAX_BODY - body.size()) throw new IOException("HTTP response body exceeds 1 MiB");
            if (size == 0) {
                while (!readLine(input, deadline, MAX_HEADER_LINE).isEmpty()) { }
                return body.toByteArray();
            }
            body.write(readFixed(input, (int) size, deadline));
            if (!"\r\n".equals(readExactString(input, 2, deadline))) throw new IOException("Malformed chunk delimiter");
        }
    }

    private byte[] readToEnd(InputStream input, long deadline) throws IOException, InterruptedException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = read(input, buffer, deadline)) >= 0) {
            if (body.size() + count > MAX_BODY) throw new IOException("HTTP response body exceeds 1 MiB");
            body.write(buffer, 0, count);
        }
        return body.toByteArray();
    }

    private byte[] readFixed(InputStream input, int length, long deadline) throws IOException, InterruptedException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = read(input, result, offset, length - offset, deadline);
            if (count < 0) throw new EOFException("Unexpected end of HTTP response body");
            offset += count;
        }
        return result;
    }

    private String readLine(InputStream input, long deadline, int maxLength) throws IOException, InterruptedException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        while (line.size() <= maxLength) {
            int next = read(input, deadline);
            if (next < 0) throw new EOFException("Unexpected end of HTTP response");
            if (previous == '\r' && next == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.ISO_8859_1);
            }
            line.write(next);
            previous = next;
        }
        throw new IOException("HTTP response line is too long");
    }

    private String readExactString(InputStream input, int length, long deadline) throws IOException, InterruptedException {
        return new String(readFixed(input, length, deadline), StandardCharsets.US_ASCII);
    }

    private int read(InputStream input, long deadline) throws IOException, InterruptedException {
        checkInterrupted();
        remainingMillis(deadline);
        return input.read();
    }

    private int read(InputStream input, byte[] buffer, long deadline) throws IOException, InterruptedException {
        checkInterrupted();
        remainingMillis(deadline);
        return input.read(buffer);
    }

    private int read(InputStream input, byte[] buffer, int offset, int length, long deadline) throws IOException, InterruptedException {
        checkInterrupted();
        remainingMillis(deadline);
        return input.read(buffer, offset, length);
    }

    private static int remainingMillis(long deadline) throws IOException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new IOException("HTTP node request timed out");
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, (remaining + 999_999) / 1_000_000));
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("HTTP request interrupted");
    }

    private static int parseLength(String value) throws IOException {
        try {
            long length = Long.parseLong(value);
            if (length < 0 || length > MAX_BODY) throw new IOException("HTTP response body exceeds 1 MiB");
            return (int) length;
        } catch (NumberFormatException e) { throw new IOException("Invalid HTTP content length", e); }
    }

    private static boolean containsToken(List<String> values, String token) {
        if (values == null) return false;
        return values.stream().flatMap(value -> List.of(value.split(",")).stream())
                .anyMatch(value -> value.trim().equalsIgnoreCase(token));
    }

    private static String first(List<String> values) { return values == null || values.isEmpty() ? null : values.get(0); }

    private static final class DeadlineInputStream extends FilterInputStream {
        private final Socket socket;
        private final long deadline;

        private DeadlineInputStream(Socket socket, long deadline) throws IOException {
            super(socket.getInputStream());
            this.socket = socket;
            this.deadline = deadline;
        }

        @Override public int read() throws IOException {
            socket.setSoTimeout(remainingMillis(deadline));
            return super.read();
        }

        @Override public int read(byte[] buffer) throws IOException {
            socket.setSoTimeout(remainingMillis(deadline));
            return super.read(buffer);
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            socket.setSoTimeout(remainingMillis(deadline));
            return super.read(buffer, offset, length);
        }
    }

    record Response(int statusCode, Map<String, List<String>> headers, byte[] body) {
        String firstHeader(String name) { return first(headers.get(name.toLowerCase(Locale.ROOT))); }
    }
}
