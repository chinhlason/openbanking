package vn.com.truongsonbank.shared.protocol;

import java.util.function.Function;

import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class TsbGrpcClientFactory {
    private final ProtocolProperties properties;
    private final TsbGrpcClientInterceptor interceptor;
    private final TsbServiceDiscoveryClient discoveryClient;

    TsbGrpcClientFactory(
            ProtocolProperties properties,
            TsbGrpcClientInterceptor interceptor,
            TsbServiceDiscoveryClient discoveryClient) {
        this.properties = properties;
        this.interceptor = interceptor;
        this.discoveryClient = discoveryClient;
    }

    public ManagedChannel channel(String downstream) {
        ProtocolProperties.Downstream config = properties.getDownstreams().get(downstream);
        String target = config == null ? null : discoveryClient.grpcTarget(config.getServiceId());
        if ((target == null || target.isBlank()) && config != null) {
            target = config.getTarget();
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Missing gRPC target for downstream: " + downstream);
        }
        return ManagedChannelBuilder.forTarget(target).usePlaintext().build();
    }

    public <T> T blockingStub(String downstream, Function<Channel, T> stubFactory) {
        Channel intercepted = ClientInterceptors.intercept(channel(downstream), interceptor.forDownstream(downstream));
        return stubFactory.apply(intercepted);
    }
}
