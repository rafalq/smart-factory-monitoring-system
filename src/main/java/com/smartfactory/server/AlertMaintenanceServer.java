package com.smartfactory.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.smartfactory.naming.ServiceRegistrar;

/**
 * gRPC server for the AlertMaintenanceService.
 * Starts the server on a configured port and handles graceful shutdown.
 */
public class AlertMaintenanceServer {

    private static final Logger logger = Logger.getLogger(AlertMaintenanceServer.class.getName());

    public static final int PORT = 50053;

    private Server server;
    private AlertMaintenanceServiceImpl serviceImpl;

    private ServiceRegistrar registrar;

    public void start() throws IOException {
        serviceImpl = new AlertMaintenanceServiceImpl();

        server = ServerBuilder
                .forPort(PORT)
                .addService(serviceImpl)
                .build()
                .start();

        logger.info("AlertMaintenanceServer started on port " + PORT);

        // Register with JmDNS
        registrar = new ServiceRegistrar();
        registrar.registerService(
                "AlertMaintenanceService",
                PORT,
                "Manages factory alerts and maintenance reports");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down AlertMaintenanceServer...");
            try {
                AlertMaintenanceServer.this.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Shutdown interrupted: " + e.getMessage());
            }
            logger.info("AlertMaintenanceServer shut down");
        }));
    }

    public void stop() throws InterruptedException {
        if (registrar != null) {
            registrar.close();
        }
        if (serviceImpl != null) {
            serviceImpl.shutdown();
        }
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Blocks the main thread until the server shuts down.
     *
     * @throws InterruptedException if the thread is interrupted
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Entry point for running the AlertMaintenanceServer standalone.
     *
     * @param args command-line arguments (not used)
     * @throws IOException          if the server fails to start
     * @throws InterruptedException if the server is interrupted
     */
    public static void main(String[] args)
            throws IOException, InterruptedException {

        AlertMaintenanceServer server = new AlertMaintenanceServer();
        server.start();
        server.blockUntilShutdown();
    }
}