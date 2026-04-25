package com.smartfactory.server;

import com.smartfactory.grpc.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.logging.Logger;

/**
 * Implementation of the MachineHealthMonitor gRPC service.
 * Provides unary and streaming RPCs for monitoring factory machine health.
 */
public class MachineHealthMonitorImpl
        extends MachineHealthMonitorGrpc.MachineHealthMonitorImplBase {

    private static final Logger logger = Logger.getLogger(MachineHealthMonitorImpl.class.getName());

    private final MachineRegistry registry;

    /**
     * Constructs the service implementation using the shared MachineRegistry.
     */
    public MachineHealthMonitorImpl() {
        this.registry = MachineRegistry.getInstance();
    }

    // ===========================
    // Unary RPC
    // ===========================

    /**
     * Unary RPC - returns the current health status of a single machine.
     *
     * @param request          contains the machine ID to check
     * @param responseObserver used to send the response back to the client
     */
    @Override
    public void checkMachineStatus(
            MachineStatusRequest request,
            StreamObserver<MachineStatusResponse> responseObserver) {

        String machineId = request.getMachineId();
        logger.info("CheckMachineStatus called for machine: " + machineId);

        // Validate machine ID is not empty
        if (machineId == null || machineId.trim().isEmpty()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Machine ID cannot be empty")
                            .asRuntimeException());
            return;
        }

        // Check machine exists in registry
        if (!registry.machineExists(machineId)) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription("Machine not found: " + machineId)
                            .asRuntimeException());
            return;
        }

        MachineData machine = registry.getMachine(machineId);
        machine.simulateSensorUpdate();

        MachineStatusResponse response = MachineStatusResponse.newBuilder()
                .setMachineId(machine.getMachineId())
                .setMachineName(machine.getMachineName())
                .setTemperature(machine.getTemperature())
                .setVibrationLevel(machine.getVibrationLevel())
                .setIsOperational(machine.isOperational())
                .setStatusMessage(machine.getStatusMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        logger.info("CheckMachineStatus completed for machine: " + machineId);
    }

    // ===========================
    // Server-side Streaming RPC
    // ===========================

    /**
     * Server-side streaming RPC - streams temperature readings at set intervals.
     *
     * @param request          contains machine ID and interval in seconds
     * @param responseObserver used to stream readings back to the client
     */
    @Override
    public void streamTemperature(
            TemperatureStreamRequest request,
            StreamObserver<TemperatureReading> responseObserver) {

        String machineId = request.getMachineId();
        int intervalSeconds = request.getIntervalSeconds();

        logger.info("StreamTemperature started for machine: " + machineId);

        // Validate machine ID
        if (machineId == null || machineId.trim().isEmpty()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Machine ID cannot be empty")
                            .asRuntimeException());
            return;
        }

        // Validate machine exists
        if (!registry.machineExists(machineId)) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription("Machine not found: " + machineId)
                            .asRuntimeException());
            return;
        }

        // Clamp interval between 1 and 10 seconds
        int interval = Math.max(1, Math.min(intervalSeconds, 10));

        try {
            // Stream 10 temperature readings
            for (int i = 0; i < 10; i++) {
                MachineData machine = registry.getMachine(machineId);
                machine.simulateSensorUpdate();

                TemperatureReading reading = TemperatureReading.newBuilder()
                        .setMachineId(machineId)
                        .setTemperature(machine.getTemperature())
                        .setTimestamp(Instant.now().toString())
                        .setIsCritical(machine.isCriticalTemperature())
                        .build();

                responseObserver.onNext(reading);

                logger.info("StreamTemperature sent reading " + (i + 1)
                        + " for machine: " + machineId
                        + " temp: " + machine.getTemperature());

                Thread.sleep(interval * 1000L);
            }

            responseObserver.onCompleted();
            logger.info("StreamTemperature completed for machine: " + machineId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(
                    Status.CANCELLED
                            .withDescription("Temperature stream was interrupted")
                            .asRuntimeException());
        }
    }
}