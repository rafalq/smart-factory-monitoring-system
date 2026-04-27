package com.smartfactory.server;

import com.smartfactory.grpc.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Implementation of the AlertMaintenanceService gRPC service.
 * Provides unary, server-side streaming and client-side streaming RPCs
 * for managing factory alerts and maintenance reports.
 */
public class AlertMaintenanceServiceImpl
        extends AlertMaintenanceServiceGrpc.AlertMaintenanceServiceImplBase {

    private static final Logger logger = Logger.getLogger(AlertMaintenanceServiceImpl.class.getName());

    private static final int MAX_ALERTS = 15;

    private final MachineRegistry registry;
    private final List<LiveAlert> activeAlerts;
    private final ScheduledExecutorService scheduler;

    /**
     * Constructs the service and starts background alert simulation.
     */
    public AlertMaintenanceServiceImpl() {
        this.registry = MachineRegistry.getInstance();
        this.activeAlerts = new ArrayList<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        startAlertSimulation();
    }

    /**
     * Starts a background task that periodically simulates alerts
     * based on current machine sensor readings.
     */
    private void startAlertSimulation() {
        scheduler.scheduleAtFixedRate(() -> {
            for (MachineData machine : registry.getAllMachines()) {
                if (!machine.isOperational())
                    continue;

                machine.simulateSensorUpdate();

                if (machine.isCriticalTemperature()) {
                    addSimulatedAlert(
                            machine.getMachineId(),
                            "OVERHEAT",
                            5,
                            "Temperature exceeded critical threshold: "
                                    + machine.getTemperature() + "C");
                } else if (machine.getVibrationLevel() >= MachineData.CRITICAL_VIBRATION) {
                    addSimulatedAlert(
                            machine.getMachineId(),
                            "VIBRATION",
                            4,
                            "Vibration level exceeded safe limit: "
                                    + machine.getVibrationLevel() + " mm/s");
                } else if (machine.getPressure() >= MachineData.CRITICAL_PRESSURE) {
                    addSimulatedAlert(
                            machine.getMachineId(),
                            "PRESSURE",
                            3,
                            "Pressure level elevated: "
                                    + machine.getPressure() + " bar");
                }
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    /**
     * Adds a simulated alert to the active alerts list.
     * Maintains a maximum list size to prevent memory overflow.
     *
     * @param machineId   machine that triggered the alert
     * @param alertType   type of alert
     * @param severity    severity level 1-5
     * @param description detailed alert description
     */
    private void addSimulatedAlert(
            String machineId, String alertType,
            int severity, String description) {

        LiveAlert alert = LiveAlert.newBuilder()
                .setAlertId(UUID.randomUUID().toString().substring(0, 8))
                .setMachineId(machineId)
                .setAlertType(alertType)
                .setSeverity(severity)
                .setDescription(description)
                .setTimestamp(Instant.now().toString())
                .build();

        synchronized (activeAlerts) {
            activeAlerts.add(alert);
            if (activeAlerts.size() > MAX_ALERTS) {
                activeAlerts.remove(0);
            }
        }

        logger.info("Simulated alert added - machine: " + machineId
                + " type: " + alertType + " severity: " + severity);
    }

    // ===========================
    // Unary RPC
    // ===========================

    /**
     * Unary RPC - raises a new alert for a machine fault.
     *
     * @param request          contains machine ID, alert type, severity,
     *                         description
     * @param responseObserver used to send the response back to the client
     */
    @Override
    public void raiseAlert(
            AlertRequest request,
            StreamObserver<AlertResponse> responseObserver) {

        String machineId = request.getMachineId();
        String alertType = request.getAlertType().toUpperCase().trim();
        int severity = request.getSeverity();
        String description = request.getDescription();

        logger.info("RaiseAlert called - machine: " + machineId
                + " type: " + alertType + " severity: " + severity);

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

        // Validate severity range
        if (severity < 1 || severity > 5) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Severity must be between 1 and 5, "
                                    + "received: " + severity)
                            .asRuntimeException());
            return;
        }

        // Validate alert type
        if (alertType.isEmpty()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Alert type cannot be empty")
                            .asRuntimeException());
            return;
        }

        String alertId = UUID.randomUUID().toString().substring(0, 8);
        String timestamp = Instant.now().toString();

        // Add to active alerts list
        addSimulatedAlert(machineId, alertType, severity, description);

        String confirmation = "Alert " + alertId + " registered for machine "
                + machineId + " - type: " + alertType
                + " severity: " + severity;

        AlertResponse response = AlertResponse.newBuilder()
                .setAlertId(alertId)
                .setMachineId(machineId)
                .setAcknowledged(true)
                .setConfirmation(confirmation)
                .setTimestamp(timestamp)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        logger.info("RaiseAlert completed - alert ID: " + alertId);
    }

    // ===========================
    // Server-side Streaming RPC
    // ===========================

    /**
     * Server-side streaming RPC - streams live alerts to the client
     * filtered by minimum severity level.
     *
     * @param request          contains minimum severity filter
     * @param responseObserver used to stream alerts back to the client
     */
    @Override
    public void streamAlerts(
            AlertStreamRequest request,
            StreamObserver<LiveAlert> responseObserver) {

        int minSeverity = request.getMinSeverity();

        logger.info("StreamAlerts started with minSeverity: " + minSeverity);

        // Validate severity
        if (minSeverity < 1 || minSeverity > 5) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Minimum severity must be between 1 and 5")
                            .asRuntimeException());
            return;
        }

        try {
            // Stream alerts for 30 seconds
            long endTime = System.currentTimeMillis() + 30_000;

            while (System.currentTimeMillis() < endTime) {
                synchronized (activeAlerts) {
                    for (LiveAlert alert : activeAlerts) {
                        if (alert.getSeverity() >= minSeverity) {
                            responseObserver.onNext(alert);
                            logger.info("StreamAlerts sent alert - machine: "
                                    + alert.getMachineId()
                                    + " type: " + alert.getAlertType()
                                    + " severity: " + alert.getSeverity());
                        }
                    }
                    activeAlerts.clear();
                }
                Thread.sleep(5000);
            }

            responseObserver.onCompleted();
            logger.info("StreamAlerts completed");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(
                    Status.CANCELLED
                            .withDescription("Alert stream was interrupted")
                            .asRuntimeException());
        }
    }

    // ===========================
    // Client-side Streaming RPC
    // ===========================

    /**
     * Client-side streaming RPC - receives multiple maintenance reports
     * from client and returns a final summary.
     *
     * @param responseObserver used to send the final summary to the client
     * @return StreamObserver that processes each incoming maintenance report
     */
    @Override
    public StreamObserver<MaintenanceReport> submitMaintenanceReports(
            StreamObserver<MaintenanceSummary> responseObserver) {

        return new StreamObserver<MaintenanceReport>() {

            private int reportsCount = 0;
            private final List<String> servicedMachines = new ArrayList<>();

            @Override
            public void onNext(MaintenanceReport report) {
                reportsCount++;
                String machineId = report.getMachineId();

                if (!servicedMachines.contains(machineId)) {
                    servicedMachines.add(machineId);
                }

                logger.info("SubmitMaintenanceReports received report "
                        + reportsCount
                        + " - machine: " + machineId
                        + " technician: " + report.getTechnicianId()
                        + " action: " + report.getActionTaken());
            }

            @Override
            public void onError(Throwable throwable) {
                logger.warning("SubmitMaintenanceReports error: "
                        + throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                if (reportsCount == 0) {
                    responseObserver.onError(
                            Status.INVALID_ARGUMENT
                                    .withDescription("No maintenance reports received")
                                    .asRuntimeException());
                    return;
                }

                String confirmation = "Successfully processed "
                        + reportsCount + " maintenance report(s) for "
                        + servicedMachines.size() + " machine(s)";

                MaintenanceSummary summary = MaintenanceSummary.newBuilder()
                        .setReportsReceived(reportsCount)
                        .setMachinesServiced(servicedMachines.size())
                        .setConfirmation(confirmation)
                        .setTimestamp(Instant.now().toString())
                        .build();

                responseObserver.onNext(summary);
                responseObserver.onCompleted();

                logger.info("SubmitMaintenanceReports completed - "
                        + reportsCount + " reports processed");
            }
        };
    }

    /**
     * Shuts down the background alert simulation scheduler.
     */
    public void shutdown() {
        scheduler.shutdown();
    }
}