package vn.com.truongsonbank.client2;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.com.truongsonbank.grpc.demo.DemoEchoServiceGrpc;
import vn.com.truongsonbank.grpc.demo.EchoReply;
import vn.com.truongsonbank.grpc.demo.EchoRequest;
import vn.com.truongsonbank.shared.protocol.TsbGrpcServerInterceptor;

import java.io.IOException;
import java.time.Instant;

@Configuration
class Client2GrpcHealthServerConfiguration {
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    Client2GrpcHealthServer client2GrpcHealthServer(TsbGrpcServerInterceptor interceptor) {
        return new Client2GrpcHealthServer(9092, interceptor);
    }
}

class Client2GrpcHealthServer {
    private final Server server;

    Client2GrpcHealthServer(int port, TsbGrpcServerInterceptor interceptor) {
        this.server = NettyServerBuilder.forPort(port)
                .intercept(interceptor)
                .addService(new Client2DemoEchoGrpcService())
                .build();
    }

    void start() throws IOException {
        server.start();
    }

    void shutdown() {
        server.shutdown();
    }
}

class Client2DemoEchoGrpcService extends DemoEchoServiceGrpc.DemoEchoServiceImplBase {
    @Override
    public void echo(EchoRequest request, StreamObserver<EchoReply> responseObserver) {
        responseObserver.onNext(EchoReply.newBuilder()
                .setId(request.getId())
                .setMessage(request.getMessage())
                .setService("client2-grpc")
                .setTimestamp(Instant.now().toString())
                .build());
        responseObserver.onCompleted();
    }
}
