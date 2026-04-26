package com.smartfactory.server;

import com.smartfactory.grpc.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.logging.Logger;

/**
 * Implementation of the ProductionLineController gRPC service.
 * Provides unary and bidirectional streaming RPCs for managing
 * factory production line operations.
 */
public class ProductionLineControllerImpl
        extends ProductionLineControllerGrpc.ProductionLineControllerImplBase {

    private static final Logger logger = Logger.getLogger(ProductionLineControllerImpl.class.getName());

    private final MachineRegistry registry;

    /**
     * Constructs the service implementation using the shared MachineRegistry.
     */
    public ProductionLineControllerImpl() {
        this.registry = MachineRegistry.getInstance();
    }

    // ===========================
    // Unary RPC
    // ===========================

    /**
     * Unary RPC - starts or stops a machine on the production line.
     *
     * @param request          contains machine ID, command and operator ID
     * @param responseObserver used to send the response back to the client
     */
    @Override
    public void startStopMachine(
            MachineControlRequest request,
            StreamObserver<MachineControlResponse> responseObserver) {

        String machineId = request.getMachineId();
        String command = request.getCommand().toUpperCase().trim();
        String operatorId = request.getOperatorId();

        logger.info("StartStopMachine called - machine: " + machineId
                + " command: " + command
                + " operator: " + operatorId);

        // Validate machine ID
        if (machineId == null || machineId.trim().isEmpty()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Machine ID cannot be empty")
                            .asRuntimeException());
            return;
        }

        // Validate command
        if (!command.equals("START") && !command.equals("STOP")) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Command must be START or STOP, received: "
                                    + command)
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

        MachineData machine = registry.getMachine(machineId);
        boolean alreadyInState = command.equals("START")
                ? machine.isOperational()
                : !machine.isOperational();

        if (alreadyInState) {
            responseObserver.onError(
                    Status.FAILED_PRECONDITION
                            .withDescription("Machine " + machineId + " is already "
                                    + (command.equals("START") ? "running" : "stopped"))
                            .asRuntimeException());
            return;
        }

        // Apply command
        machine.setOperational(command.equals("START"));

        String confirmation = "Machine " + machineId
                + " successfully " + (machine.isOperational()
                        ? "started"
                        : "stopped")
                + " by operator " + operatorId;

        MachineControlResponse response = MachineControlResponse.newBuilder()
                .setMachineId(machineId)
                .setSuccess(true)
                .setIsRunning(machine.isOperational())
                .setConfirmation(confirmation)
                .setTimestamp(Instant.now().toString())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        logger.info("StartStopMachine completed - " + confirmation);
    }

    // ===========================
    // Bidirectional Streaming RPC
    // ===========================

    /**
     * Bidirectional streaming RPC - client sends production commands,
     * server responds with live production statistics for each command.
     *
     * @param responseObserver used to stream production status back to client
     * @return StreamObserver that processes each incoming production command
     */
    @Override
    public StreamObserver<ProductionCommand> monitorProduction(
            StreamObserver<ProductionStatus> responseObserver) {

        return new StreamObserver<ProductionCommand>() {

            @Override
            public void onNext(ProductionCommand command) {
                String machineId = command.getMachineId();
                int targetOutput = command.getTargetOutput();
                String operatorId = command.getOperatorId();

                logger.info("MonitorProduction received command - machine: "
                        + machineId + " target: " + targetOutput
                        + " operator: " + operatorId);

                // Validate machine exists
                if (!registry.machineExists(machineId)) {
                    responseObserver.onError(
                            Status.NOT_FOUND
                                    .withDescription("Machine not found: " + machineId)
                                    .asRuntimeException());
                    return;
                }

                MachineData machine = registry.getMachine(machineId);

                // Validate machine is running
                if (!machine.isOperational()) {
                    responseObserver.onError(
                            Status.FAILED_PRECONDITION
                                    .withDescription("Machine " + machineId
                                            + " is not running")
                                    .asRuntimeException());
                    return;
                }

                machine.simulateSensorUpdate();

                // Simulate production output based on target
                int currentOutput = simulateOutput(targetOutput, machine);
                float efficiency = calculateEfficiency(
                        currentOutput, targetOutput);
                int unitsProduced = currentOutput
                        + (int) (Math.random() * 10);
                String warning = buildWarning(machine, efficiency);

                ProductionStatus status = ProductionStatus.newBuilder()
                        .setMachineId(machineId)
                        .setCurrentOutput(currentOutput)
                        .setEfficiency(efficiency)
                        .setUnitsProduced(unitsProduced)
                        .setWarning(warning)
                        .setTimestamp(Instant.now().toString())
                        .build();

                responseObserver.onNext(status);

                logger.info("MonitorProduction sent status - machine: "
                        + machineId + " output: " + currentOutput
                        + " efficiency: " + efficiency + "%");
            }

            @Override
            public void onError(Throwable throwable) {
                logger.warning("MonitorProduction error: "
                        + throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
                logger.info("MonitorProduction stream completed");
            }

            /**
             * Simulates current output based on target and machine health.
             *
             * @param targetOutput desired output rate
             * @param machine      current machine state
             * @return simulated current output
             */
            private int simulateOutput(int targetOutput, MachineData machine) {
                float healthFactor = 1.0f;

                if (machine.isCriticalTemperature()) {
                    healthFactor = 0.5f;
                } else if (machine.getTemperature() > MachineData.CRITICAL_TEMPERATURE * 0.8f) {
                    healthFactor = 0.75f;
                }

                int output = (int) (targetOutput * healthFactor
                        * (0.85 + Math.random() * 0.3));
                return Math.max(0, Math.min(output, targetOutput));
            }

            /**
             * Calculates efficiency percentage based on output vs target.
             *
             * @param currentOutput actual output achieved
             * @param targetOutput  desired output
             * @return efficiency as a percentage
             */
            private float calculateEfficiency(
                    int currentOutput, int targetOutput) {
                if (targetOutput == 0)
                    return 0.0f;
                return Math.min(100.0f,
                        (currentOutput / (float) targetOutput) * 100.0f);
            }

            /**
             * Builds a warning message based on current machine state.
             *
             * @param machine    current machine state
             * @param efficiency current efficiency percentage
             * @return warning message or empty string if no issues
             */
            private String buildWarning(
                    MachineData machine, float efficiency) {
                if (machine.isCriticalTemperature()) {
                    return "CRITICAL: Machine overheating - output reduced";
                } else if (efficiency < 60.0f) {
                    return "WARNING: Efficiency below 60% - maintenance advised";
                } else if (machine.getVibrationLevel() >= MachineData.CRITICAL_VIBRATION) {
                    return "WARNING: High vibration detected";
                }
                return "";
            }
        };
    }
}