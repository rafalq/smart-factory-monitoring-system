package com.smartfactory.client;

import com.smartfactory.grpc.*;
import com.smartfactory.naming.ServiceDiscovery;
import com.smartfactory.naming.ServiceDiscovery.DiscoveredService;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * gRPC client for the Smart Factory Monitoring System.
 * Discovers services via JmDNS and communicates with all three
 * gRPC services using all four communication patterns.
 */
public class SmartFactoryClient {

        private static final Logger logger = Logger.getLogger(SmartFactoryClient.class.getName());

        private static final String API_KEY = "smart-factory-2026";
        private static final String OPERATOR_ID = "operator-001";
        private static final int DEADLINE_SECONDS = 10;

        private ManagedChannel healthChannel;
        private ManagedChannel productionChannel;
        private ManagedChannel alertChannel;

        private MachineHealthMonitorGrpc.MachineHealthMonitorBlockingStub healthStub;
        private MachineHealthMonitorGrpc.MachineHealthMonitorStub healthAsyncStub;
        private ProductionLineControllerGrpc.ProductionLineControllerBlockingStub productionStub;
        private ProductionLineControllerGrpc.ProductionLineControllerStub productionAsyncStub;
        private AlertMaintenanceServiceGrpc.AlertMaintenanceServiceBlockingStub alertStub;
        private AlertMaintenanceServiceGrpc.AlertMaintenanceServiceStub alertAsyncStub;

        private final ServiceDiscovery serviceDiscovery;

        /**
         * Constructs the client and initialises service discovery.
         *
         * @param serviceDiscovery the JmDNS discovery instance
         */
        public SmartFactoryClient(ServiceDiscovery serviceDiscovery) {
                this.serviceDiscovery = serviceDiscovery;
        }

        /**
         * Connects to all three gRPC services using discovered addresses.
         * Falls back to localhost with default ports if discovery fails.
         */
        public void connectToServices() {
                MetadataUtils metadataInterceptor = new MetadataUtils(API_KEY, OPERATOR_ID);

                healthChannel = buildChannel(
                                "MachineHealthMonitor", 50051, metadataInterceptor);
                productionChannel = buildChannel(
                                "ProductionLineController", 50052, metadataInterceptor);
                alertChannel = buildChannel(
                                "AlertMaintenanceService", 50053, metadataInterceptor);

                healthStub = MachineHealthMonitorGrpc
                                .newBlockingStub(healthChannel);
                healthAsyncStub = MachineHealthMonitorGrpc
                                .newStub(healthChannel);
                productionStub = ProductionLineControllerGrpc
                                .newBlockingStub(productionChannel);
                productionAsyncStub = ProductionLineControllerGrpc
                                .newStub(productionChannel);
                alertStub = AlertMaintenanceServiceGrpc
                                .newBlockingStub(alertChannel);
                alertAsyncStub = AlertMaintenanceServiceGrpc
                                .newStub(alertChannel);

                logger.info("Connected to all three gRPC services");
        }

        /**
         * Builds a managed channel for a service using discovered address
         * or falling back to localhost.
         *
         * @param serviceName         name of the service to discover
         * @param defaultPort         port to use if discovery fails
         * @param metadataInterceptor interceptor to attach metadata
         * @return a configured ManagedChannel
         */
        private ManagedChannel buildChannel(
                        String serviceName, int defaultPort,
                        MetadataUtils metadataInterceptor) {

                String host = "localhost";
                int port = defaultPort;

                DiscoveredService service = serviceDiscovery.findService(serviceName);
                if (service != null) {
                        host = service.getHost();
                        port = service.getPort();
                        logger.info("Discovered " + serviceName
                                        + " at " + host + ":" + port);
                } else {
                        logger.warning("Could not discover " + serviceName
                                        + " - using localhost:" + defaultPort);
                }

                return ManagedChannelBuilder
                                .forAddress(host, port)
                                .intercept(metadataInterceptor)
                                .usePlaintext()
                                .build();
        }

        // ===========================
        // MachineHealthMonitor calls
        // ===========================

        /**
         * Unary RPC - checks the current status of a machine.
         *
         * @param machineId the machine ID to check
         * @return the status response or null on error
         */
        public MachineStatusResponse checkMachineStatus(String machineId) {
                try {
                        MachineStatusRequest request = MachineStatusRequest.newBuilder()
                                        .setMachineId(machineId)
                                        .build();

                        return healthStub
                                        .withDeadline(Deadline.after(
                                                        DEADLINE_SECONDS, TimeUnit.SECONDS))
                                        .checkMachineStatus(request);

                } catch (StatusRuntimeException e) {
                        logger.warning("CheckMachineStatus failed: "
                                        + e.getStatus().getDescription());
                        return null;
                }
        }

        /**
         * Server-side streaming RPC - streams temperature readings for a machine.
         *
         * @param machineId       machine to monitor
         * @param intervalSeconds interval between readings
         * @param observer        observer to handle incoming readings
         */
        public void streamTemperature(
                        String machineId, int intervalSeconds,
                        StreamObserver<TemperatureReading> observer) {

                TemperatureStreamRequest request = TemperatureStreamRequest.newBuilder()
                                .setMachineId(machineId)
                                .setIntervalSeconds(intervalSeconds)
                                .build();

                healthAsyncStub
                                .withDeadline(Deadline.after(120, TimeUnit.SECONDS))
                                .streamTemperature(request, observer);
        }

        /**
         * Client-side streaming RPC - sends multiple sensor readings to the server.
         *
         * @param readings array of sensor data to send
         * @return the summary response or null on error
         * @throws InterruptedException if the call is interrupted
         */
        public SensorDataSummary reportSensorData(SensorData[] readings)
                        throws InterruptedException {

                final SensorDataSummary[] result = { null };
                final CountDownLatch latch = new CountDownLatch(1);

                StreamObserver<SensorDataSummary> responseObserver = new StreamObserver<>() {

                        @Override
                        public void onNext(SensorDataSummary summary) {
                                result[0] = summary;
                        }

                        @Override
                        public void onError(Throwable t) {
                                logger.warning("ReportSensorData error: " + t.getMessage());
                                latch.countDown();
                        }

                        @Override
                        public void onCompleted() {
                                latch.countDown();
                        }
                };

                StreamObserver<SensorData> requestObserver = healthAsyncStub.reportSensorData(responseObserver);

                for (SensorData reading : readings) {
                        requestObserver.onNext(reading);
                }
                requestObserver.onCompleted();

                latch.await(DEADLINE_SECONDS, TimeUnit.SECONDS);
                return result[0];
        }

        // ===========================
        // ProductionLineController calls
        // ===========================

        /**
         * Unary RPC - starts or stops a production line machine.
         *
         * @param machineId  machine to control
         * @param command    START or STOP
         * @param operatorId operator issuing the command
         * @return the control response or null on error
         */
        public MachineControlResponse startStopMachine(
                        String machineId, String command, String operatorId) {
                try {
                        MachineControlRequest request = MachineControlRequest.newBuilder()
                                        .setMachineId(machineId)
                                        .setCommand(command)
                                        .setOperatorId(operatorId)
                                        .build();

                        return productionStub
                                        .withDeadline(Deadline.after(
                                                        DEADLINE_SECONDS, TimeUnit.SECONDS))
                                        .startStopMachine(request);

                } catch (StatusRuntimeException e) {
                        logger.warning("StartStopMachine failed: "
                                        + e.getStatus().getDescription());
                        return null;
                }
        }

        /**
         * Bidirectional streaming RPC - sends production commands and
         * receives live production statistics.
         *
         * @param responseObserver observer to handle incoming production status
         * @return StreamObserver to send production commands
         */
        public StreamObserver<ProductionCommand> monitorProduction(
                        StreamObserver<ProductionStatus> responseObserver) {

                return productionAsyncStub
                                .withDeadline(Deadline.after(120, TimeUnit.SECONDS))
                                .monitorProduction(responseObserver);
        }

        // ===========================
        // AlertMaintenanceService calls
        // ===========================

        /**
         * Unary RPC - raises a new alert for a machine fault.
         *
         * @param machineId   machine triggering the alert
         * @param alertType   type of alert
         * @param severity    severity level 1-5
         * @param description detailed description
         * @return the alert response or null on error
         */
        public AlertResponse raiseAlert(
                        String machineId, String alertType,
                        int severity, String description) {
                try {
                        AlertRequest request = AlertRequest.newBuilder()
                                        .setMachineId(machineId)
                                        .setAlertType(alertType)
                                        .setSeverity(severity)
                                        .setDescription(description)
                                        .build();

                        return alertStub
                                        .withDeadline(Deadline.after(
                                                        DEADLINE_SECONDS, TimeUnit.SECONDS))
                                        .raiseAlert(request);

                } catch (StatusRuntimeException e) {
                        logger.warning("RaiseAlert failed: "
                                        + e.getStatus().getDescription());
                        return null;
                }
        }

        /**
         * Server-side streaming RPC - streams live alerts filtered by severity.
         *
         * @param minSeverity minimum severity level to receive
         * @param observer    observer to handle incoming alerts
         */
        public void streamAlerts(
                        int minSeverity,
                        StreamObserver<LiveAlert> observer) {

                AlertStreamRequest request = AlertStreamRequest.newBuilder()
                                .setMinSeverity(minSeverity)
                                .build();

                alertAsyncStub
                                .withDeadline(Deadline.after(60, TimeUnit.SECONDS))
                                .streamAlerts(request, observer);
        }

        /**
         * Client-side streaming RPC - submits multiple maintenance reports.
         *
         * @param reports array of maintenance reports to submit
         * @return the summary response or null on error
         * @throws InterruptedException if the call is interrupted
         */
        public MaintenanceSummary submitMaintenanceReports(
                        MaintenanceReport[] reports) throws InterruptedException {

                final MaintenanceSummary[] result = { null };
                final CountDownLatch latch = new CountDownLatch(1);

                StreamObserver<MaintenanceSummary> responseObserver = new StreamObserver<>() {

                        @Override
                        public void onNext(MaintenanceSummary summary) {
                                result[0] = summary;
                        }

                        @Override
                        public void onError(Throwable t) {
                                logger.warning("SubmitMaintenanceReports error: "
                                                + t.getMessage());
                                latch.countDown();
                        }

                        @Override
                        public void onCompleted() {
                                latch.countDown();
                        }
                };

                StreamObserver<MaintenanceReport> requestObserver = alertAsyncStub
                                .submitMaintenanceReports(responseObserver);

                for (MaintenanceReport report : reports) {
                        requestObserver.onNext(report);
                }
                requestObserver.onCompleted();

                latch.await(DEADLINE_SECONDS, TimeUnit.SECONDS);
                return result[0];
        }

        /**
         * Cancels an ongoing gRPC call by closing the channel and
         * reconnecting. Used to demonstrate call cancellation.
         *
         * @param serviceName name of the service to cancel calls for
         */
        public void cancelCalls(String serviceName) {
                try {
                        switch (serviceName) {
                                case "MachineHealthMonitor" -> {
                                        if (healthChannel != null) {
                                                healthChannel.shutdownNow();
                                                logger.info("Cancelled all calls to MachineHealthMonitor");
                                        }
                                }
                                case "ProductionLineController" -> {
                                        if (productionChannel != null) {
                                                productionChannel.shutdownNow();
                                                logger.info("Cancelled all calls to ProductionLineController");
                                        }
                                }
                                case "AlertMaintenanceService" -> {
                                        if (alertChannel != null) {
                                                alertChannel.shutdownNow();
                                                logger.info("Cancelled all calls to AlertMaintenanceService");
                                        }
                                }
                                default -> logger.warning("Unknown service: " + serviceName);
                        }
                } catch (Exception e) {
                        logger.warning("Error cancelling calls: " + e.getMessage());
                }
        }

        // ===========================
        // Shutdown
        // ===========================

        /**
         * Shuts down all managed channels gracefully.
         *
         * @throws InterruptedException if shutdown is interrupted
         */
        public void shutdown() throws InterruptedException {
                if (healthChannel != null) {
                        healthChannel.shutdown()
                                        .awaitTermination(5, TimeUnit.SECONDS);
                }
                if (productionChannel != null) {
                        productionChannel.shutdown()
                                        .awaitTermination(5, TimeUnit.SECONDS);
                }
                if (alertChannel != null) {
                        alertChannel.shutdown()
                                        .awaitTermination(5, TimeUnit.SECONDS);
                }
                logger.info("All client channels shut down");
        }
}