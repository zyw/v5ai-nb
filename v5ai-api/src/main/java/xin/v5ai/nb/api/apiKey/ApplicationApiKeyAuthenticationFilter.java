package xin.v5ai.nb.api.apiKey;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import xin.v5ai.nb.common.apiKey.ApplicationApiKeyVerifier;

public class ApplicationApiKeyAuthenticationFilter implements WebFilter {
    private static final String RUNTIME_PREFIX = "/api/v1/apps/";
    private static final String BEARER_PREFIX = "Bearer ";
    private final ApplicationApiKeyVerifier verifier;

    public ApplicationApiKeyAuthenticationFilter(ApplicationApiKeyVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.startsWith(RUNTIME_PREFIX)) {
            return chain.filter(exchange);
        }

        var appKey = extractAppKey(path);
        var authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (appKey == null || authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange);
        }

        var apiKey = authorization.substring(BEARER_PREFIX.length()).trim();
        if (apiKey.isEmpty() || !verifier.verify(appKey, apiKey)) {
            return unauthorized(exchange);
        }
        return chain.filter(exchange);
    }

    private String extractAppKey(String path) {
        var rest = path.substring(RUNTIME_PREFIX.length());
        var slash = rest.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        return rest.substring(0, slash);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
