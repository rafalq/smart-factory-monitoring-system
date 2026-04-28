package com.smartfactory.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
// TODO: Consider adding interceptors for logging, authentication, or metrics in the future
// import io.grpc.ServerInterceptors;
import io.grpc.ServerInterceptors;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.smartfactory.naming.ServiceRegistrar;

/**
 * gRPC server for the MachineHealthMonitor service.
 * Starts the server on a configured port and handles graceful shutdown.
 */
public class MachineHealthServer {

    private static final Logger logger = Logger.getLogger(MachineHealthServer.class.getName());

    public static final int PORT = 50051;

    private Server server;
    private ServiceRegistrar registrar;

    /**
     * Starts the gRPC server and registers the MachineHealthMonitor service.
     *
     * @throws IOException if the server fails to start
     */
    public void start() throws IOException {
        server = ServerBuilder
                .forPort(PORT)
                .addService(ServerInterceptors.intercept(
                        new MachineHealthMonitorImpl(),
                        new AuthInterceptor()))
                .build()
                .start();

        logger.info("MachineHealthServer started on port " + PORT);

        // Register with JmDNS
        registrar = new ServiceRegistrar();
        registrar.registerService(
                "MachineHealthMonitor",
                PORT,
                "Monitors machine temperature, vibration and health status");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down MachineHealthServer...");
            try {
                MachineHealthServer.this.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Shutdown interrupted: " + e.getMessage());
            }
            logger.info("MachineHealthServer shut down");
        }));
    }

    /**
     * Stops the gRPC server and closes JmDNS registrar.
     *
     * @throws InterruptedException if shutdown is interrupted
     */
    public void stop() throws InterruptedException {
        if (registrar != null) {
            registrar.close();
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
     * Entry point for running the MachineHealthServer standalone.
     *
     * @param args command-line arguments (not used)
     * @throws IOException          if the server fails to start
     * @throws InterruptedException if the server is interrupted
     */
    public static void main(String[] args)
            throws IOException, InterruptedException {

        MachineHealthServer server = new MachineHealthServer();
        server.start();
        server.blockUntilShutdown();
    }
}