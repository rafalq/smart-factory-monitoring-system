package com.smartfactory.server;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.util.logging.Logger;

/**
 * gRPC server interceptor that validates an API key sent in request metadata.
 * Demonstrates authentication using gRPC metadata headers.
 */
public class AuthInterceptor implements ServerInterceptor {

    private static final Logger logger = Logger.getLogger(AuthInterceptor.class.getName());

    // Metadata key for API key header
    public static final Metadata.Key<String> API_KEY = Metadata.Key.of("api-key", Metadata.ASCII_STRING_MARSHALLER);

    // Context key to pass operator info downstream
    public static final Context.Key<String> OPERATOR_ID = Context.key("operator-id");

    private static final String VALID_API_KEY = "smart-factory-2026";

    /**
     * Intercepts incoming gRPC calls to validate the API key in metadata.
     * Rejects calls with missing or invalid API keys.
     *
     * @param call    the incoming server call
     * @param headers the metadata headers from the client
     * @param next    the next handler in the chain
     * @return a listener for the call
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String apiKey = headers.get(API_KEY);
        String operatorId = headers.get(
                Metadata.Key.of("operator-id",
                        Metadata.ASCII_STRING_MARSHALLER));

        logger.info("Intercepting call: " + call.getMethodDescriptor().getFullMethodName()
                + " from operator: " + operatorId);

        // Validate API key
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warning("Rejected call - missing API key");
            call.close(
                    Status.UNAUTHENTICATED
                            .withDescription("API key is missing"),
                    new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        if (!apiKey.equals(VALID_API_KEY)) {
            logger.warning("Rejected call - invalid API key: " + apiKey);
            call.close(
                    Status.UNAUTHENTICATED
                            .withDescription("Invalid API key"),
                    new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        // Pass operator ID downstream via context
        Context context = Context.current().withValue(
                OPERATOR_ID,
                operatorId != null ? operatorId : "unknown");

        logger.info("Authorised call from operator: "
                + (operatorId != null ? operatorId : "unknown"));

        return Contexts.interceptCall(context, call, headers, next);
    }
}