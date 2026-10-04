package vn.com.truongsonbank.shared.security;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public final class ServiceAuthGrpcServerInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Context.Key<ServicePrincipal> PRINCIPAL = Context.key("tsb.servicePrincipal");
    private final ServiceJwtVerifier verifier;

    public ServiceAuthGrpcServerInterceptor(ServiceJwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public <ReqT, RespT> io.grpc.ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String value = headers.get(AUTHORIZATION);
        if (value == null || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            call.close(Status.UNAUTHENTICATED.withDescription(ServiceAuthErrors.MISSING.code()), new Metadata());
            return new io.grpc.ServerCall.Listener<>() { };
        }
        try {
            ServicePrincipal principal = verifier.verify(value.substring(7).trim());
            return Contexts.interceptCall(Context.current().withValue(PRINCIPAL, principal), call, headers, next);
        } catch (vn.com.truongsonbank.shared.exception.TsbException exception) {
            call.close(Status.PERMISSION_DENIED.withDescription(exception.error().code()), new Metadata());
            return new io.grpc.ServerCall.Listener<>() { };
        }
    }

    public static ServicePrincipal current() {
        return PRINCIPAL.get();
    }
}
