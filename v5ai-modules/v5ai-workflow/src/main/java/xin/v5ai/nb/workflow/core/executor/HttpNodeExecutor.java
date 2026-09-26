package xin.v5ai.nb.workflow.core.executor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.workflow.core.NodeExecutionResult;
import xin.v5ai.nb.workflow.core.TemplateResolver;
import xin.v5ai.nb.workflow.core.WorkflowExecutionContext;
import xin.v5ai.nb.workflow.core.WorkflowJson;
import xin.v5ai.nb.workflow.core.WorkflowNode;
import xin.v5ai.nb.workflow.core.WorkflowNodeExecutor;
import xin.v5ai.nb.workflow.core.enums.WorkflowNodeType;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Outbound HTTP node with allow-listed targets, DNS pinning, and config-backed secret references. */
@Component
public class HttpNodeExecutor implements WorkflowNodeExecutor {
    private static final int MAX_BODY_BYTES = 1_048_576;
    private static final int MAX_RESOLVED_ADDRESSES = 16;
    private static final Pattern SECRET_REFERENCE = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    private static final Set<String> NEVER_ALLOWED_HEADERS = Set.of("host", "proxy-authorization", "proxy-connection",
            "connection", "content-length", "transfer-encoding", "te", "trailer", "upgrade", "expect", "accept-encoding");
    private static final Set<String> SECRET_ONLY_HEADERS = Set.of("authorization", "cookie", "set-cookie");

    private final Set<String> allowedHosts;
    private final Set<Integer> allowedPorts;
    private final SecretLookup secrets;
    private final PinnedHttpTransport transport;

    @Autowired
    public HttpNodeExecutor(Environment environment,
                            @Value("${v5ai.workflow.http.allowed-hosts:}") String allowedHosts,
                            @Value("${v5ai.workflow.http.allowed-ports:80,443}") String allowedPorts) {
        this(allowedHosts, allowedPorts,
                name -> environment.getProperty("v5ai.workflow.http.secrets." + name));
    }

    /** Compatibility constructor used by direct WorkflowEngine construction in module consumers. */
    public HttpNodeExecutor(String allowedHosts) {
        this(allowedHosts, "80,443", name -> null);
    }

    HttpNodeExecutor(String allowedHosts, String allowedPorts, SecretLookup secrets) {
        this.allowedHosts = parseHosts(allowedHosts);
        this.allowedPorts = parsePorts(allowedPorts);
        this.secrets = secrets;
        this.transport = new PinnedHttpTransport();
    }

    @Override public WorkflowNodeType type() { return WorkflowNodeType.HTTP; }

    /** Validate the persisted shape without resolving deployment secrets or templated targets. */
    public static void validateConfig(Map<String, Object> config) {
        if (config == null) return;
        Object rawHeaders = config.get("headers");
        if (rawHeaders == null) return;
        if (!(rawHeaders instanceof Map<?, ?> configured)) {
            throw new IllegalArgumentException("HTTP node headers must be a JSON object");
        }
        Set<String> seenNames = new LinkedHashSet<>();
        configured.forEach((rawName, rawValue) -> {
            String name = String.valueOf(rawName);
            if (!HEADER_NAME.matcher(name).matches()) throw new IllegalArgumentException("HTTP node header name is invalid");
            String lowerName = name.toLowerCase(Locale.ROOT);
            if (!seenNames.add(lowerName)) throw new IllegalArgumentException("HTTP node header names must be unique ignoring case");
            if (NEVER_ALLOWED_HEADERS.contains(lowerName)) throw new IllegalArgumentException("HTTP node header is not allowed: " + name);
            boolean secretReference = rawValue instanceof Map<?, ?> map && map.size() == 1 && map.containsKey("$secretRef");
            if (secretReference) {
                Object ref = ((Map<?, ?>) rawValue).get("$secretRef");
                if (ref == null || !SECRET_REFERENCE.matcher(String.valueOf(ref)).matches()) {
                    throw new IllegalArgumentException("HTTP node secret reference is invalid for header " + name);
                }
            } else if (SECRET_ONLY_HEADERS.contains(lowerName) || isSensitiveHeaderName(lowerName)) {
                throw new IllegalArgumentException("HTTP node sensitive header must use a secret reference: " + name);
            } else if (rawValue instanceof Map<?, ?> || rawValue instanceof Iterable<?>) {
                throw new IllegalArgumentException("HTTP node header values must be strings or secret references: " + name);
            }
        });
    }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, WorkflowExecutionContext context) {
        if (allowedHosts.isEmpty()) {
            throw new IllegalStateException("workflow HTTP executor is disabled: configure v5ai.workflow.http.allowed-hosts");
        }
        String rawUrl = TemplateResolver.resolve(AgentNodeExecutor.text(node.config().get("url")), context);
        URI uri;
        try { uri = URI.create(rawUrl); }
        catch (RuntimeException e) { throw new IllegalArgumentException("HTTP node URL is invalid", e); }
        List<InetAddress> addresses = validateAndResolveTarget(uri);

        String method = AgentNodeExecutor.text(node.config().get("method"));
        if (method == null) method = "GET";
        method = method.toUpperCase(Locale.ROOT);
        if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
            throw new IllegalArgumentException("HTTP node method is not allowed: " + method);
        }
        long timeoutMillis = Math.min(AgentNodeExecutor.positiveLong(node.config().get("timeoutMs"), 10_000L), 60_000L);
        Map<String, String> headers = resolveHeaders(node.config().get("headers"), context);
        headers.put("Accept", "application/json, text/plain;q=0.9");
        headers.put("Accept-Encoding", "identity");

        byte[] requestBody = new byte[0];
        Object rawBody = node.config().get("body");
        if (rawBody != null && !method.equals("GET")) {
            try {
                Object body = resolveTemplates(rawBody, context);
                requestBody = WorkflowJson.MAPPER.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalArgumentException("HTTP node request body cannot be serialized", e);
            }
            if (requestBody.length > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("HTTP node request body exceeds 1 MiB");
            }
            headers.put("Content-Type", "application/json");
        }

        try {
            PinnedHttpTransport.Response response = transport.execute(uri, addresses, method, headers,
                    requestBody, Duration.ofMillis(timeoutMillis));
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                throw new IllegalStateException("HTTP node redirects are not allowed");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP node returned status " + response.statusCode());
            }
            String text = new String(response.body(), StandardCharsets.UTF_8);
            String contentType = response.firstHeader("content-type");
            if (contentType == null) contentType = "";
            Object body = text;
            if (contentType.toLowerCase(Locale.ROOT).contains("json") && !text.isBlank()) {
                body = WorkflowJson.MAPPER.readValue(text, Object.class);
            }
            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("status", response.statusCode());
            outputs.put("body", body);
            outputs.put("contentType", contentType);
            String outputVar = AgentNodeExecutor.text(node.config().get("outputVar"));
            if (outputVar != null && !outputVar.isBlank()) context.setVariable(outputVar, body);
            return NodeExecutionResult.of(outputs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP node was interrupted", e);
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("HTTP node request failed: " + e.getClass().getSimpleName());
        }
    }

    private List<InetAddress> validateAndResolveTarget(URI uri) {
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("HTTP node URL must be an absolute http(s) URL without user info or fragment");
        }
        String host = normalizeHost(uri.getHost());
        if (host.indexOf(':') >= 0) throw new IllegalArgumentException("HTTP node IP literals are not allowed");
        if (!allowedHosts.contains(host)) throw new IllegalArgumentException("HTTP node host is not allow-listed: " + host);
        int port = uri.getPort() >= 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        if (!allowedPorts.contains(port)) throw new IllegalArgumentException("HTTP node port is not allow-listed: " + port);
        try {
            InetAddress[] resolved = InetAddress.getAllByName(host);
            if (resolved.length == 0 || resolved.length > MAX_RESOLVED_ADDRESSES) {
                throw new IllegalArgumentException("HTTP node host returned an invalid number of DNS addresses");
            }
            List<InetAddress> addresses = new ArrayList<>(resolved.length);
            for (InetAddress address : resolved) {
                if (isForbiddenAddress(address)) {
                    throw new IllegalArgumentException("HTTP node host resolves to a non-public address");
                }
                addresses.add(address);
            }
            return List.copyOf(addresses);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("HTTP node host cannot be resolved", e);
        }
    }

    private Map<String, String> resolveHeaders(Object rawHeaders, WorkflowExecutionContext context) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (rawHeaders == null) return headers;
        if (!(rawHeaders instanceof Map<?, ?> configured)) {
            throw new IllegalArgumentException("HTTP node headers must be a JSON object");
        }
        Set<String> seenNames = new LinkedHashSet<>();
        configured.forEach((rawName, rawValue) -> {
            String name = String.valueOf(rawName);
            if (!HEADER_NAME.matcher(name).matches()) throw new IllegalArgumentException("HTTP node header name is invalid");
            String lowerName = name.toLowerCase(Locale.ROOT);
            if (!seenNames.add(lowerName)) throw new IllegalArgumentException("HTTP node header names must be unique ignoring case");
            if (NEVER_ALLOWED_HEADERS.contains(lowerName)) {
                throw new IllegalArgumentException("HTTP node header is not allowed: " + name);
            }
            boolean secretReference = rawValue instanceof Map<?, ?> map && map.size() == 1 && map.containsKey("$secretRef");
            String value;
            if (secretReference) {
                Object refValue = ((Map<?, ?>) rawValue).get("$secretRef");
                String reference = refValue == null ? "" : String.valueOf(refValue);
                if (!SECRET_REFERENCE.matcher(reference).matches()) {
                    throw new IllegalArgumentException("HTTP node secret reference is invalid for header " + name);
                }
                value = secrets.resolve(reference);
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException("HTTP node secret reference is not configured: " + reference);
                }
            } else {
                if (SECRET_ONLY_HEADERS.contains(lowerName) || isSensitiveHeader(lowerName)) {
                    throw new IllegalArgumentException("HTTP node sensitive header must use a secret reference: " + name);
                }
                value = TemplateResolver.resolve(rawValue == null ? "" : String.valueOf(rawValue), context);
            }
            if (value == null || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("HTTP node header value contains invalid control characters: " + name);
            }
            headers.put(name, value);
        });
        return headers;
    }

    private boolean isSensitiveHeader(String name) {
        return isSensitiveHeaderName(name);
    }

    private static boolean isSensitiveHeaderName(String name) {
        return name.contains("authorization") || name.contains("api-key") || name.contains("apikey")
                || name.contains("access-token") || name.contains("secret");
    }

    private static Set<String> parseHosts(String configured) {
        if (configured == null || configured.isBlank()) return Set.of();
        Set<String> hosts = new LinkedHashSet<>();
        for (String entry : configured.split(",")) {
            String host = normalizeHost(entry.trim());
            if (host.isBlank() || host.indexOf(':') >= 0) {
                throw new IllegalArgumentException("workflow HTTP allowed-hosts must contain DNS hostnames only");
            }
            hosts.add(host);
        }
        return Set.copyOf(hosts);
    }

    private static Set<Integer> parsePorts(String configured) {
        if (configured == null || configured.isBlank()) return Set.of();
        Set<Integer> ports = new LinkedHashSet<>();
        for (String value : configured.split(",")) {
            try {
                int port = Integer.parseInt(value.trim());
                if (port < 1 || port > 65_535) throw new IllegalArgumentException("workflow HTTP allowed port is invalid");
                ports.add(port);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("workflow HTTP allowed port is invalid", e);
            }
        }
        return Set.copyOf(ports);
    }

    private static String normalizeHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) return "";
        String host = rawHost.trim().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        try { return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("HTTP node host is invalid", e); }
    }

    private boolean isForbiddenAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 16 && isIpv4Mapped(bytes)) {
            byte[] ipv4 = java.util.Arrays.copyOfRange(bytes, 12, 16);
            try { return isForbiddenAddress(InetAddress.getByAddress(ipv4)); }
            catch (UnknownHostException impossible) { return true; }
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first == 0 || first >= 224
                    || first == 100 && second >= 64 && second <= 127
                    || first == 192 && (second == 0 || second == 2 && third == 0 || second == 88 && third == 99)
                    || first == 198 && (second == 18 || second == 19 || second == 51 && third == 100)
                    || first == 203 && second == 0 && third == 113;
        }
        if (bytes.length == 16) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            boolean uniqueLocal = (first & 0xFE) == 0xFC;
            boolean documentation = first == 0x20 && second == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0D && Byte.toUnsignedInt(bytes[3]) == 0xB8;
            boolean nat64WellKnown = first == 0x00 && bytes[1] == 0x64 && bytes[2] == (byte) 0xFF && bytes[3] == (byte) 0x9B;
            return uniqueLocal || documentation || nat64WellKnown;
        }
        return true;
    }

    private boolean isIpv4Mapped(byte[] address) {
        for (int i = 0; i < 10; i++) if (address[i] != 0) return false;
        return address[10] == (byte) 0xFF && address[11] == (byte) 0xFF;
    }

    private Object resolveTemplates(Object value, WorkflowExecutionContext context) {
        if (value instanceof String text) return TemplateResolver.resolve(text, context);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            map.forEach((key, child) -> resolved.put(String.valueOf(key), resolveTemplates(child, context)));
            return resolved;
        }
        if (value instanceof Iterable<?> values) {
            List<Object> resolved = new ArrayList<>();
            values.forEach(child -> resolved.add(resolveTemplates(child, context)));
            return resolved;
        }
        return value;
    }

    @FunctionalInterface
    interface SecretLookup { String resolve(String reference); }
}
