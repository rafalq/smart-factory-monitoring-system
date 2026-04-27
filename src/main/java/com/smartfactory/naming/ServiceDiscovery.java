package com.smartfactory.naming;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Handles discovery of gRPC services using JmDNS (mDNS).
 * Listens for service registrations on the local network and
 * maintains a list of discovered services for the GUI client.
 */
public class ServiceDiscovery {

    private static final Logger logger = Logger.getLogger(ServiceDiscovery.class.getName());

    /**
     * Represents a discovered gRPC service with its connection details.
     */
    public static class DiscoveredService {

        private final String name;
        private final String host;
        private final int port;
        private final String description;

        /**
         * Constructs a DiscoveredService with connection details.
         *
         * @param name        service name
         * @param host        host address of the service
         * @param port        port the service is running on
         * @param description short description of the service
         */
        public DiscoveredService(
                String name, String host,
                int port, String description) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return name + " @ " + host + ":" + port
                    + " (" + description + ")";
        }
    }

    private JmDNS jmdns;
    private final List<DiscoveredService> discoveredServices;

    /**
     * Initialises JmDNS and starts listening for service registrations.
     *
     * @throws IOException if JmDNS cannot be initialised
     */
    public ServiceDiscovery() throws IOException {
        InetAddress localAddress = InetAddress.getLocalHost();
        jmdns = JmDNS.create(localAddress);
        discoveredServices = Collections.synchronizedList(new ArrayList<>());

        // Register listener for gRPC services
        jmdns.addServiceListener(
                ServiceRegistrar.SERVICE_TYPE,
                new SmartFactoryServiceListener());

        logger.info("ServiceDiscovery started on: "
                + localAddress.getHostAddress());
    }

    /**
     * Returns a copy of the current list of discovered services.
     *
     * @return list of discovered services
     */
    public List<DiscoveredService> getDiscoveredServices() {
        return new ArrayList<>(discoveredServices);
    }

    /**
     * Searches for a discovered service by name.
     *
     * @param serviceName the name of the service to find
     * @return the DiscoveredService if found, or null
     */
    public DiscoveredService findService(String serviceName) {
        return discoveredServices.stream()
                .filter(s -> s.getName().equals(serviceName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Closes the JmDNS instance and stops listening for services.
     */
    public void close() {
        if (jmdns != null) {
            try {
                jmdns.close();
                logger.info("ServiceDiscovery closed");
            } catch (IOException e) {
                logger.warning("Error closing ServiceDiscovery: "
                        + e.getMessage());
            }
        }
    }

    // ===========================
    // Service Listener
    // ===========================

    /**
     * Listens for gRPC service registration events on the local network.
     */
    private class SmartFactoryServiceListener implements ServiceListener {

        @Override
        public void serviceAdded(ServiceEvent event) {
            logger.info("Service found: " + event.getName());
            // Request full service info
            jmdns.requestServiceInfo(event.getType(), event.getName());
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            String serviceName = event.getName();
            discoveredServices.removeIf(
                    s -> s.getName().equals(serviceName));
            logger.info("Service removed: " + serviceName);
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            ServiceInfo info = event.getInfo();
            String name = info.getName();
            String host = info.getHostAddresses()[0];
            int port = info.getPort();
            String description = info.getPropertyString("description");

            if (description == null)
                description = "No description";

            DiscoveredService service = new DiscoveredService(
                    name, host, port, description);

            // Avoid duplicates
            discoveredServices.removeIf(s -> s.getName().equals(name));
            discoveredServices.add(service);

            logger.info("Service resolved: " + service);
        }
    }
}