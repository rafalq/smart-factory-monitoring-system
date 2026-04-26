package com.smartfactory.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
// TODO: Consider adding interceptors for logging, authentication, or metrics in the future
// import io.grpc.ServerInterceptors;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * gRPC server for the MachineHealthMonitor service.
 * Starts the server on a configured port and handles graceful shutdown.
 */
public class MachineHealthServer {

    private static final Logger logger = Logger.getLogger(MachineHealthServer.class.getName());

    public static final int PORT = 50051;

    private Server server;

    /**
     * Starts the gRPC server and registers the MachineHealthMonitor service.
     *
     * @throws IOException if the server fails to start
     */
    public void start() throws IOException {
        server = ServerBuilder
                .forPort(PORT)
                .addService(new MachineHealthMonitorImpl())
                .build()
                .start();

        logger.info("MachineHealthServer started on port " + PORT);

        // Register shutdown hook for graceful shutdown
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
     * Stops the gRPC server gracefully within a timeout period.
     *
     * @throws InterruptedException if shutdown is interrupted
     */
    public void stop() throws InterruptedException {
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