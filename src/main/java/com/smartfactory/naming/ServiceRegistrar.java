package com.smartfactory.naming;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Handles registration of gRPC services using JmDNS (mDNS).
 * Each service registers itself on the local network so that
 * clients can discover it without hardcoded addresses.
 */
public class ServiceRegistrar {

    private static final Logger logger = Logger.getLogger(ServiceRegistrar.class.getName());

    public static final String SERVICE_TYPE = "_grpc._tcp.local.";

    private JmDNS jmdns;

    /**
     * Initialises JmDNS on the local machine's IP address.
     *
     * @throws IOException if JmDNS cannot be initialised
     */
    public ServiceRegistrar() throws IOException {
        InetAddress localAddress = InetAddress.getLocalHost();
        jmdns = JmDNS.create(localAddress);
        logger.info("JmDNS initialised on: " + localAddress.getHostAddress());
    }

    /**
     * Registers a gRPC service with JmDNS so it can be discovered
     * by clients on the local network.
     *
     * @param serviceName human-readable name of the service
     * @param port        port the gRPC service is running on
     * @param description short description of the service
     * @throws IOException if registration fails
     */
    public void registerService(
            String serviceName, int port, String description)
            throws IOException {

        Map<String, String> properties = new HashMap<>();
        properties.put("description", description);
        properties.put("version", "1.0");

        ServiceInfo serviceInfo = ServiceInfo.create(
                SERVICE_TYPE,
                serviceName,
                port,
                0,
                0,
                properties);

        jmdns.registerService(serviceInfo);

        logger.info("Service registered: " + serviceName
                + " on port " + port
                + " - " + description);
    }

    /**
     * Unregisters all services and closes the JmDNS instance.
     * Should be called on application shutdown.
     */
    public void close() {
        if (jmdns != null) {
            jmdns.unregisterAllServices();
            try {
                jmdns.close();
                logger.info("JmDNS closed and all services unregistered");
            } catch (IOException e) {
                logger.warning("Error closing JmDNS: " + e.getMessage());
            }
        }
    }
}