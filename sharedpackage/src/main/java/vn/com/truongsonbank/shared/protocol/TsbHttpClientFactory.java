package vn.com.truongsonbank.shared.protocol;

import java.net.http.HttpClient;
import java.lang.reflect.Method;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.service.annotation.GetExchange;
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
        if (baseUrl != null && !baseUrl.isBlank()) {
            baseUrl = appendContextPath(baseUrl, config.getContextPath());
        } else {
            baseUrl = config.getBaseUrl() == null ? config.getTarget() : config.getBaseUrl();
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    public <T> T httpInterface(String downstream, Class<T> clientType) {
        registerOperations(downstream, clientType);
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient(downstream)))
                .build();
        return proxyFactory.createClient(clientType);
    }

    private void registerOperations(String downstream, Class<?> clientType) {
        for (Method method : clientType.getMethods()) {
            TsbOperation operation = method.getAnnotation(TsbOperation.class);
            GetExchange exchange = method.getAnnotation(GetExchange.class);
            if (operation != null && exchange != null) {
                policyResolver.register(downstream, path(exchange), operation);
            }
        }
    }

    private String path(GetExchange exchange) {
        if (!exchange.value().isBlank()) {
            return exchange.value();
        }
        return exchange.url();
    }

    private ProtocolProperties.Downstream downstreamConfig(String downstream) {
        return properties.getDownstreams().getOrDefault(downstream, new ProtocolProperties.Downstream());
    }

    private static Duration firstNonNull(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }

    private static String appendContextPath(String baseUrl, String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return baseUrl;
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = contextPath.startsWith("/") ? contextPath : "/" + contextPath;
        return base + path;
    }
}
