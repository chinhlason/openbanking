package vn.com.truongsonbank.shared.protocol;

import java.net.http.HttpClient;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

public class TsbHttpClientFactory {
    private final ProtocolProperties properties;
    private final TsbProtocolClientHttpRequestInterceptor interceptor;
    private final TsbProtocolPolicyResolver policyResolver;
    private final TsbServiceDiscoveryClient discoveryClient;

    TsbHttpClientFactory(
            ProtocolProperties properties,
            TsbProtocolClientHttpRequestInterceptor interceptor,
            TsbProtocolPolicyResolver policyResolver,
            TsbServiceDiscoveryClient discoveryClient) {
        this.properties = properties;
        this.interceptor = interceptor;
        this.policyResolver = policyResolver;
        this.discoveryClient = discoveryClient;
    }

    public RestClient restClient(String downstream) {
        ProtocolProperties.Downstream config = downstreamConfig(downstream);
        Duration connectTimeout = firstNonNull(config.getConnectTimeout(), properties.getDefaults().getConnectTimeout());
        Duration responseTimeout = firstNonNull(config.getResponseTimeout(), properties.getDefaults().getResponseTimeout());
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(responseTimeout);

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(interceptor.forDownstream(downstream));
        String baseUrl = discoveryClient.httpBaseUrl(config.getServiceId());
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = config.getBaseUrl() == null ? config.getTarget() : config.getBaseUrl();
        }
        baseUrl = appendContextPath(baseUrl, config.getContextPath());
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    public <T> T httpInterface(String downstream, Class<T> clientType) {
        validateDownstream(downstream);
        if (!clientType.isInterface()) {
            throw new IllegalArgumentException("@TsbHttpClient target must be an interface: " + clientType.getName());
        }
        registerOperations(downstream, clientType);
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient(downstream)))
                .build();
        return proxyFactory.createClient(clientType);
    }

    private void registerOperations(String downstream, Class<?> clientType) {
        HttpExchange typeExchange = clientType.getAnnotation(HttpExchange.class);
        String typePath = typeExchange == null ? "" : exchangePath(typeExchange.value(), typeExchange.url());
        boolean hasHttpMethod = false;
        for (Method method : clientType.getMethods()) {
            if (method.getDeclaringClass() == Object.class || method.isDefault() || method.isSynthetic()) {
                continue;
            }
            List<ExchangeDescriptor> exchanges = exchangeDescriptors(method);
            if (exchanges.isEmpty()) {
                throw new IllegalArgumentException("HTTP client method must use a supported exchange annotation: "
                        + clientType.getName() + "#" + method.getName());
            }
            hasHttpMethod = true;
            for (ExchangeDescriptor exchange : exchanges) {
                String route = joinPaths(typePath, exchange.path());
                route = joinPaths(configuredPathPrefix(downstream), route);
                for (HttpMethod httpMethod : exchange.methods()) {
                    policyResolver.register(downstream, httpMethod, route,
                            method.getAnnotation(TsbOperation.class));
                }
            }
        }
        if (!hasHttpMethod) {
            throw new IllegalArgumentException("HTTP client interface has no exchange methods: " + clientType.getName());
        }
    }

    private List<ExchangeDescriptor> exchangeDescriptors(Method method) {
        List<ExchangeDescriptor> descriptors = new ArrayList<>();
        GetExchange get = method.getAnnotation(GetExchange.class);
        if (get != null) {
            descriptors.add(new ExchangeDescriptor(exchangePath(get.value(), get.url()), List.of(HttpMethod.GET)));
        }
        PostExchange post = method.getAnnotation(PostExchange.class);
        if (post != null) {
            descriptors.add(new ExchangeDescriptor(exchangePath(post.value(), post.url()), List.of(HttpMethod.POST)));
        }
        PutExchange put = method.getAnnotation(PutExchange.class);
        if (put != null) {
            descriptors.add(new ExchangeDescriptor(exchangePath(put.value(), put.url()), List.of(HttpMethod.PUT)));
        }
        PatchExchange patch = method.getAnnotation(PatchExchange.class);
        if (patch != null) {
            descriptors.add(new ExchangeDescriptor(exchangePath(patch.value(), patch.url()), List.of(HttpMethod.PATCH)));
        }
        DeleteExchange delete = method.getAnnotation(DeleteExchange.class);
        if (delete != null) {
            descriptors.add(new ExchangeDescriptor(exchangePath(delete.value(), delete.url()), List.of(HttpMethod.DELETE)));
        }
        HttpExchange exchange = method.getAnnotation(HttpExchange.class);
        if (exchange != null) {
            String requestMethod = exchange.method();
            List<HttpMethod> methods = requestMethod == null || requestMethod.isBlank()
                    ? Collections.singletonList(null)
                    : List.of(HttpMethod.valueOf(requestMethod.toUpperCase(Locale.ROOT)));
            descriptors.add(new ExchangeDescriptor(exchangePath(exchange.value(), exchange.url()), methods));
        }
        return descriptors;
    }

    private String configuredPathPrefix(String downstream) {
        ProtocolProperties.Downstream config = downstreamConfig(downstream);
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = config.getTarget();
        }
        String basePath = "";
        if (baseUrl != null && !baseUrl.isBlank()) {
            try {
                basePath = URI.create(baseUrl).getPath();
            } catch (IllegalArgumentException ignored) {
                // Runtime validation reports malformed URLs when the client is created.
            }
        }
        return joinPaths(basePath, config.getContextPath());
    }

    private ProtocolProperties.Downstream downstreamConfig(String downstream) {
        return properties.getDownstreams().getOrDefault(downstream, new ProtocolProperties.Downstream());
    }

    void validateDownstream(String downstream) {
        if (downstream == null || downstream.isBlank()) {
            throw new IllegalArgumentException("HTTP client downstream must not be blank");
        }
        ProtocolProperties.Downstream config = properties.getDownstreams().get(downstream);
        if (config == null) {
            throw new IllegalArgumentException("No protocol downstream configured for '" + downstream + "'");
        }
        if (isBlank(config.getBaseUrl()) && isBlank(config.getTarget()) && isBlank(config.getServiceId())) {
            throw new IllegalArgumentException("Protocol downstream '" + downstream
                    + "' must configure base-url, target, or service-id");
        }
    }

    private static Duration firstNonNull(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }

    private static String appendContextPath(String baseUrl, String contextPath) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return baseUrl;
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = contextPath.startsWith("/") ? contextPath : "/" + contextPath;
        return base + path;
    }

    private static String exchangePath(String value, String url) {
        return value == null || value.isBlank() ? url : value;
    }

    private static String joinPaths(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.isBlank()) {
            return right.isBlank() ? "/" : right;
        }
        if (right.isBlank() || "/".equals(right)) {
            return left;
        }
        String normalizedLeft = left.endsWith("/") ? left.substring(0, left.length() - 1) : left;
        String normalizedRight = right.startsWith("/") ? right : "/" + right;
        return normalizedLeft + normalizedRight;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ExchangeDescriptor(String path, List<HttpMethod> methods) {
    }
}
