package com.smartfactory.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import java.util.logging.Logger;

/**
 * Client-side interceptor that attaches metadata headers to every gRPC call.
 * Sends API key and operator ID with each request for authentication.
 */
public class MetadataUtils implements ClientInterceptor {

    private static final Logger logger = Logger.getLogger(MetadataUtils.class.getName());

    private final String apiKey;
    private final String operatorId;

    /**
     * Constructs a MetadataUtils interceptor with credentials.
     *
     * @param apiKey     the API key for authentication
     * @param operatorId the ID of the operator making requests
     */
    public MetadataUtils(String apiKey, String operatorId) {
        this.apiKey = apiKey;
        this.operatorId = operatorId;
    }

    /**
     * Intercepts outgoing gRPC calls and attaches API key
     * and operator ID to the metadata headers.
     *
     * @param method      the method being called
     * @param callOptions options for the call
     * @param channel     the channel being used
     * @return a new client call with metadata attached
     */
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel channel) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                channel.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener,
                    Metadata headers) {

                // Attach API key
                headers.put(
                        Metadata.Key.of("api-key",
                                Metadata.ASCII_STRING_MARSHALLER),
                        apiKey);

                // Attach operator ID
                headers.put(
                        Metadata.Key.of("operator-id",
                                Metadata.ASCII_STRING_MARSHALLER),
                        operatorId);

                logger.info("Attaching metadata - operator: " + operatorId);

                super.start(responseListener, headers);
            }
        };
    }
}