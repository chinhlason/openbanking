package vn.com.truongsonbank.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import vn.com.truongsonbank.client.customer.config.CustomerOnboardingProperties;
import vn.com.truongsonbank.shared.protocol.TsbGrpcClientFactory;
import vn.com.truongsonbank.shared.protocol.TsbHttpClientFactory;
import vn.com.truongsonbank.shared.protocol.TsbOperation;
import vn.com.truongsonbank.grpc.demo.DemoEchoServiceGrpc;

import java.util.Map;

@SpringBootApplication
@EnableConfigurationProperties(CustomerOnboardingProperties.class)
public class ClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    @Bean
    ClientSelfHttpClient clientSelfHttpClient(TsbHttpClientFactory factory) {
        return factory.httpInterface("client-self", ClientSelfHttpClient.class);
    }

    @Bean
    Client2HttpClient client2HttpClient(TsbHttpClientFactory factory) {
        return factory.httpInterface("client2", Client2HttpClient.class);
    }

    @Bean
    DemoEchoServiceGrpc.DemoEchoServiceBlockingStub client2EchoGrpcClient(TsbGrpcClientFactory factory) {
        return factory.blockingStub("client2-grpc", DemoEchoServiceGrpc::newBlockingStub);
    }

}

interface ClientSelfHttpClient {
    @TsbOperation(value = "get-cache", responseTimeout = "1s")
    @GetExchange("/shared-test/cache/{id}")
    Map<String, Object> cached(@PathVariable String id);
}

interface Client2HttpClient {
    @TsbOperation(value = "client2-echo", responseTimeout = "1s")
    @GetExchange("/client2/echo/{id}")
    Map<String, Object> echo(@PathVariable String id);

    @TsbOperation(
            value = "client2-fail",
            responseTimeout = "1s",
            retryAttempts = 0,
            circuitBreakerMinimumCalls = 3,
            circuitBreakerSlidingWindowSize = 3,
            circuitBreakerFailureRateThreshold = 50,
            circuitBreakerOpenDuration = "10s",
            circuitBreakerHalfOpenCalls = 1)
    @GetExchange("/client2/fail/{status}")
    Map<String, Object> fail(@PathVariable int status);

    @TsbOperation(value = "client2-slow", responseTimeout = "500ms", retryAttempts = 0)
    @GetExchange("/client2/slow/{millis}")
    Map<String, Object> slow(@PathVariable long millis);

    @TsbOperation(
            value = "client2-flaky",
            responseTimeout = "1s",
            retryAttempts = 2,
            retryInitialDelay = "100ms",
            retryMaxDelay = "300ms",
            retryJitter = false)
    @GetExchange("/client2/flaky/{key}")
    Map<String, Object> flaky(@PathVariable String key);
}
