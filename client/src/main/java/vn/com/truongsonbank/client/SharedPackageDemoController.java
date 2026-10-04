package vn.com.truongsonbank.client;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Service;
import vn.com.truongsonbank.shared.cache.TsbCacheEvict;
import vn.com.truongsonbank.shared.cache.TsbCacheable;
import vn.com.truongsonbank.shared.cache.TsbCachePut;
import vn.com.truongsonbank.shared.exception.BusinessException;
import vn.com.truongsonbank.shared.exception.ErrorDescriptor;
import vn.com.truongsonbank.shared.kafka.TsbKafkaDlqReplay;
import vn.com.truongsonbank.shared.kafka.TsbKafkaDlqStatus;
import vn.com.truongsonbank.shared.kafka.TsbKafkaOutbox;
import vn.com.truongsonbank.shared.kafka.TsbKafkaPublisher;
import vn.com.truongsonbank.shared.response.ResponseWrapper;
import vn.com.truongsonbank.shared.security.RequireEntitlement;
import vn.com.truongsonbank.shared.sequence.TsbSequenceGenerator;
import vn.com.truongsonbank.shared.validation.InputValidator;
import vn.com.truongsonbank.grpc.demo.DemoEchoServiceGrpc;
import vn.com.truongsonbank.grpc.demo.EchoReply;
import vn.com.truongsonbank.grpc.demo.EchoRequest;

@Slf4j
@RestController
class SharedPackageDemoController {
    private final SharedPackageCacheDemoService cacheDemoService;
    private final ClientSelfHttpClient clientSelfHttpClient;
    private final Client2HttpClient client2HttpClient;
    private final DemoEchoServiceGrpc.DemoEchoServiceBlockingStub client2EchoGrpcClient;
    private final TsbKafkaPublisher kafkaPublisher;
    private final TsbKafkaOutbox kafkaOutbox;
    private final TsbKafkaDlqReplay kafkaDlqReplay;
    private final TsbKafkaDlqStatus kafkaDlqStatus;
    private final TsbSequenceGenerator sequenceGenerator;

    SharedPackageDemoController(SharedPackageCacheDemoService cacheDemoService,
                                ClientSelfHttpClient clientSelfHttpClient,
                                Client2HttpClient client2HttpClient,
                                DemoEchoServiceGrpc.DemoEchoServiceBlockingStub client2EchoGrpcClient,
                                TsbKafkaPublisher kafkaPublisher,
                                TsbKafkaOutbox kafkaOutbox,
                                TsbKafkaDlqReplay kafkaDlqReplay,
                                TsbKafkaDlqStatus kafkaDlqStatus,
                                TsbSequenceGenerator sequenceGenerator) {
        this.cacheDemoService = cacheDemoService;
        this.clientSelfHttpClient = clientSelfHttpClient;
        this.client2HttpClient = client2HttpClient;
        this.client2EchoGrpcClient = client2EchoGrpcClient;
        this.kafkaPublisher = kafkaPublisher;
        this.kafkaOutbox = kafkaOutbox;
        this.kafkaDlqReplay = kafkaDlqReplay;
        this.kafkaDlqStatus = kafkaDlqStatus;
        this.sequenceGenerator = sequenceGenerator;
    }

    @ResponseWrapper
    @GetMapping("/shared-test/wrapped")
    Map<String, String> wrapped() {
        return Map.of("service", "client");
    }

    @ResponseWrapper
    @RequireEntitlement("TEST2")
    @GetMapping("/shared-test/entitlement/test2")
    Map<String, Object> entitlementTest2() {
        return Map.of("allowed", true, "operation", "TEST2", "service", "client");
    }

    @GetMapping("/shared-test/error")
    Map<String, String> error() {
        throw new BusinessException(ClientErrors.CLIENT_ERROR);
    }

    @ResponseWrapper
    @PostMapping("/shared-test/validate")
    Map<String, String> validate(@RequestBody ValidateRequest request) {
        log.info("login_requested username={} phone={}", request.username, request.phone);
        return Map.of("username", request.username());
    }

    @ResponseWrapper
    @GetMapping("/shared-test/cache/{id}")
    Map<String, Object> cached(@PathVariable String id) {
        return Map.of("value", cacheDemoService.cached(id), "calls", cacheDemoService.calls());
    }

    @ResponseWrapper
    @PostMapping("/shared-test/cache/{id}/evict")
    Map<String, Object> evictCache(@PathVariable String id) {
        cacheDemoService.evict(id);
        return Map.of("evicted", id, "calls", cacheDemoService.calls());
    }

    @ResponseWrapper
    @PostMapping("/shared-test/cache/{id}")
    Map<String, Object> updateCache(@PathVariable String id, @RequestBody CacheUpdateRequest request) {
        return Map.of("value", cacheDemoService.update(id, request.value()), "calls", cacheDemoService.calls());
    }

    @ResponseWrapper
    @GetMapping("/shared-test/protocol/cache/{id}")
    Map<String, Object> protocolCached(@PathVariable String id) {
        return clientSelfHttpClient.cached(id);
    }

    @ResponseWrapper
    @GetMapping("/shared-test/protocol/client2/{id}")
    Map<String, Object> protocolClient2(@PathVariable String id) {
        return client2HttpClient.echo(id);
    }

    @ResponseWrapper
    @GetMapping("/shared-test/protocol/client2/fail/{status}")
    Map<String, Object> protocolClient2Fail(@PathVariable int status) {
        return client2HttpClient.fail(status);
    }

    @ResponseWrapper
    @GetMapping("/shared-test/protocol/client2/slow/{millis}")
    Map<String, Object> protocolClient2Slow(@PathVariable long millis) {
        return client2HttpClient.slow(millis);
    }

    @ResponseWrapper
    @GetMapping("/shared-test/protocol/client2/flaky/{key}")
    Map<String, Object> protocolClient2Flaky(@PathVariable String key) {
        return client2HttpClient.flaky(key);
    }

    @ResponseWrapper
    @GetMapping("/shared-test/protocol/grpc/client2/echo/{id}")
    Map<String, Object> grpcClient2Echo(@PathVariable String id) {
        EchoReply response = client2EchoGrpcClient.echo(EchoRequest.newBuilder()
                .setId(id)
                .setMessage("hello-from-client")
                .build());
        return Map.of(
                "id", response.getId(),
                "message", response.getMessage(),
                "service", response.getService(),
                "timestamp", response.getTimestamp());
    }

    @ResponseWrapper
    @PostMapping("/shared-test/kafka/{id}")
    Map<String, Object> publishKafka(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of("id", id) : body;
        SendResult<String, Object> result = kafkaPublisher.send("tsb.demo.events", id, payload).join();
        return Map.of(
                "mode", "direct",
                "topic", result.getRecordMetadata().topic(),
                "partition", result.getRecordMetadata().partition(),
                "offset", result.getRecordMetadata().offset());
    }

    @ResponseWrapper
    @PostMapping("/shared-test/kafka/outbox/{id}")
    Map<String, Object> publishKafkaViaOutbox(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of("id", id) : body;
        String outboxId = kafkaOutbox.save("tsb.demo.events", id, payload);
        return Map.of(
                "mode", "outbox",
                "outboxId", outboxId,
                "status", "PENDING");
    }

    @ResponseWrapper
    @PostMapping("/shared-test/kafka/dlq/replay")
    TsbKafkaDlqReplay.ReplayResult replayDlq(
            @RequestParam String topic,
            @RequestParam int partition,
            @RequestParam long offset) {
        return kafkaDlqReplay.replay(topic, partition, offset);
    }

    @ResponseWrapper
    @GetMapping("/shared-test/kafka/dlq/status")
    Object dlqStatus(@RequestParam(required = false) String topic) {
        return topic == null || topic.isBlank() ? kafkaDlqStatus.all() : kafkaDlqStatus.topic(topic);
    }

    @ResponseWrapper
    @GetMapping("/shared-test/sequence/{name}")
    Map<String, Object> sequence(@PathVariable String name,
                                 @RequestParam(defaultValue = "1") int size) {
        return Map.of(
                "name", name,
                "ids", sequenceGenerator.nextBatch(name, size));
    }

    record ValidateRequest(
            @InputValidator(fieldName = "username", required = true, min = 3, max = 10, regex = "^[a-zA-Z0-9_]+$")
            String username,
            String phone
            ) {
    }

    record CacheUpdateRequest(String value) {
    }

    enum ClientErrors implements ErrorDescriptor {
        CLIENT_ERROR("CLIENT_001", 400, "Client test error");

        private final String code;
        private final int httpStatus;
        private final String defaultMessage;

        ClientErrors(String code, int httpStatus, String defaultMessage) {
            this.code = code;
            this.httpStatus = httpStatus;
            this.defaultMessage = defaultMessage;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public int httpStatus() {
            return httpStatus;
        }

        @Override
        public String defaultMessage() {
            return defaultMessage;
        }
    }
}

@Service
class SharedPackageCacheDemoService {
    private final AtomicInteger calls = new AtomicInteger();

    @TsbCacheable(cacheName = "client-demo", key = "#p0")
    public String cached(String id) {
        return id + "-" + calls.incrementAndGet();
    }

    @TsbCacheEvict(cacheName = "client-demo", key = "#p0")
    public void evict(String id) {
    }

    @TsbCachePut(cacheName = "client-demo", key = "#p0")
    public String update(String id, String value) {
        return value;
    }

    public int calls() {
        return calls.get();
    }
}
