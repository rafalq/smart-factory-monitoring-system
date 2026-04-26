package com.smartfactory.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * gRPC server for the ProductionLineController service.
 * Starts the server on a configured port and handles graceful shutdown.
 */
public class ProductionLineServer {

    private static final Logger logger = Logger.getLogger(ProductionLineServer.class.getName());

    public static final int PORT = 50052;

    private Server server;

    /**
     * Starts the gRPC server and registers the ProductionLineController service.
     *
     * @throws IOException if the server fails to start
     */
    public void start() throws IOException {
        server = ServerBuilder
                .forPort(PORT)
                .addService(new ProductionLineControllerImpl())
                .build()
                .start();

        logger.info("ProductionLineServer started on port " + PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down ProductionLineServer...");
            try {
                ProductionLineServer.this.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Shutdown interrupted: " + e.getMessage());
            }
            logger.info("ProductionLineServer shut down");
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
     * Entry point for running the ProductionLineServer standalone.
     *
     * @param args command-line arguments (not used)
     * @throws IOException          if the server fails to start
     * @throws InterruptedException if the server is interrupted
     */
    public static void main(String[] args)
            throws IOException, InterruptedException {

        ProductionLineServer server = new ProductionLineServer();
        server.start();
        server.blockUntilShutdown();
    }
}