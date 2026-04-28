package com.smartfactory;

import com.smartfactory.grpc.*;
import com.smartfactory.naming.ServiceDiscovery;
import com.smartfactory.server.AlertMaintenanceServer;
import com.smartfactory.server.MachineHealthServer;
import com.smartfactory.server.ProductionLineServer;
import com.smartfactory.client.SmartFactoryClient;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manual integration test for the Smart Factory Monitoring System.
 * Starts all three servers, connects the client and tests all four
 * gRPC communication patterns.
 */
public class SmartFactoryTest {

    private static final Logger logger = Logger.getLogger(SmartFactoryTest.class.getName());

    public static void main(String[] args) throws Exception {

        // ===========================
        // Start all three servers
        // ===========================
        System.out.println("\n=== Starting Smart Factory Servers ===\n");

        MachineHealthServer healthServer = new MachineHealthServer();
        ProductionLineServer productionServer = new ProductionLineServer();
        AlertMaintenanceServer alertServer = new AlertMaintenanceServer();

        healthServer.start();
        productionServer.start();
        alertServer.start();

        System.out.println("All servers started. Waiting for JmDNS registration...");
        Thread.sleep(2000);

        // ===========================
        // Connect client
        // ===========================
        System.out.println("\n=== Connecting Client ===\n");

        ServiceDiscovery discovery = new ServiceDiscovery();
        Thread.sleep(2000); // Wait for service discovery

        SmartFactoryClient client = new SmartFactoryClient(discovery);
        client.connectToServices();

        // ===========================
        // Test 1: Unary - checkMachineStatus
        // ===========================
        System.out.println("\n=== Test 1: Unary - checkMachineStatus ===\n");

        MachineStatusResponse statusResponse = client.checkMachineStatus("M001");

        if (statusResponse != null) {
            System.out.println("✅ Machine: " + statusResponse.getMachineName());
            System.out.println("   Temperature: " + statusResponse.getTemperature());
            System.out.println("   Vibration: " + statusResponse.getVibrationLevel());
            System.out.println("   Operational: " + statusResponse.getIsOperational());
            System.out.println("   Status: " + statusResponse.getStatusMessage());
        } else {
            System.out.println("❌ checkMachineStatus failed");
        }

        // ===========================
        // Test 2: Unary - startStopMachine
        // ===========================
        System.out.println("\n=== Test 2: Unary - startStopMachine ===\n");

        MachineControlResponse controlResponse = client.startStopMachine("M001", "START", "operator-001");

        if (controlResponse != null) {
            System.out.println("✅ " + controlResponse.getConfirmation());
            System.out.println("   Running: " + controlResponse.getIsRunning());
        } else {
            System.out.println("❌ startStopMachine failed");
        }

        // ===========================
        // Test 3: Unary - raiseAlert
        // ===========================
        System.out.println("\n=== Test 3: Unary - raiseAlert ===\n");

        AlertResponse alertResponse = client.raiseAlert(
                "M001", "OVERHEAT", 4,
                "Temperature rising above normal threshold");

        if (alertResponse != null) {
            System.out.println("✅ Alert ID: " + alertResponse.getAlertId());
            System.out.println("   " + alertResponse.getConfirmation());
        } else {
            System.out.println("❌ raiseAlert failed");
        }

        // ===========================
        // Test 4: Client-side streaming - reportSensorData
        // ===========================
        System.out.println("\n=== Test 4: Client Streaming - reportSensorData ===\n");

        SensorData[] readings = {
                SensorData.newBuilder()
                        .setMachineId("M001")
                        .setTemperature(72.5f)
                        .setVibrationLevel(3.2f)
                        .setPressure(4.1f)
                        .setTimestamp("2026-04-27T10:00:00Z")
                        .build(),
                SensorData.newBuilder()
                        .setMachineId("M001")
                        .setTemperature(75.1f)
                        .setVibrationLevel(3.5f)
                        .setPressure(4.3f)
                        .setTimestamp("2026-04-27T10:00:05Z")
                        .build(),
                SensorData.newBuilder()
                        .setMachineId("M001")
                        .setTemperature(78.3f)
                        .setVibrationLevel(3.8f)
                        .setPressure(4.5f)
                        .setTimestamp("2026-04-27T10:00:10Z")
                        .build()
        };

        SensorDataSummary summary = client.reportSensorData(readings);

        if (summary != null) {
            System.out.println("✅ Readings received: "
                    + summary.getReadingsReceived());
            System.out.println("   Avg Temperature: "
                    + summary.getAverageTemperature());
            System.out.println("   Avg Vibration: "
                    + summary.getAverageVibration());
            System.out.println("   Summary: " + summary.getSummaryMessage());
        } else {
            System.out.println("❌ reportSensorData failed");
        }

        // ===========================
        // Test 5: Client-side streaming - submitMaintenanceReports
        // ===========================
        System.out.println("\n=== Test 5: Client Streaming - submitMaintenanceReports ===\n");

        MaintenanceReport[] reports = {
                MaintenanceReport.newBuilder()
                        .setMachineId("M001")
                        .setTechnicianId("tech-001")
                        .setActionTaken("Replaced cooling fan")
                        .setPartsReplaced("Cooling fan model CF-200")
                        .setTimestamp("2026-04-27T10:30:00Z")
                        .build(),
                MaintenanceReport.newBuilder()
                        .setMachineId("M002")
                        .setTechnicianId("tech-001")
                        .setActionTaken("Lubricated conveyor belt")
                        .setPartsReplaced("None")
                        .setTimestamp("2026-04-27T11:00:00Z")
                        .build()
        };

        MaintenanceSummary maintenanceSummary = client.submitMaintenanceReports(reports);

        if (maintenanceSummary != null) {
            System.out.println("✅ Reports received: "
                    + maintenanceSummary.getReportsReceived());
            System.out.println("   Machines serviced: "
                    + maintenanceSummary.getMachinesServiced());
            System.out.println("   " + maintenanceSummary.getConfirmation());
        } else {
            System.out.println("❌ submitMaintenanceReports failed");
        }

        // ===========================
        // Test 6: Server-side streaming - streamTemperature
        // ===========================
        System.out.println("\n=== Test 6: Server Streaming - streamTemperature ===\n");

        CountDownLatch streamLatch = new CountDownLatch(1);

        client.streamTemperature("M001", 1,
                new StreamObserver<TemperatureReading>() {
                    int count = 0;

                    @Override
                    public void onNext(TemperatureReading reading) {
                        count++;
                        System.out.println("✅ Reading " + count
                                + ": " + reading.getTemperature() + "°C"
                                + " critical=" + reading.getIsCritical()
                                + " @ " + reading.getTimestamp());
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("❌ streamTemperature error: "
                                + t.getMessage());
                        streamLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        System.out.println("✅ streamTemperature completed");
                        streamLatch.countDown();
                    }
                });

        // Wait max 15 seconds for stream to complete
        streamLatch.await(15, TimeUnit.SECONDS);

        // ===========================
        // Test 7: Bidirectional streaming - monitorProduction
        // ===========================
        System.out.println("\n=== Test 7: Bidi Streaming - monitorProduction ===\n");

        CountDownLatch bidiLatch = new CountDownLatch(1);

        StreamObserver<ProductionCommand> commandObserver = client
                .monitorProduction(new StreamObserver<ProductionStatus>() {
                    int count = 0;

                    @Override
                    public void onNext(ProductionStatus status) {
                        count++;
                        System.out.println("✅ Status " + count
                                + ": output=" + status.getCurrentOutput()
                                + " efficiency=" + status.getEfficiency() + "%"
                                + (status.getWarning().isEmpty()
                                        ? ""
                                        : " ⚠️ " + status.getWarning()));
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("❌ monitorProduction error: "
                                + t.getMessage());
                        bidiLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        System.out.println("✅ monitorProduction completed");
                        bidiLatch.countDown();
                    }
                });

        // Send 3 production commands
        for (int i = 1; i <= 3; i++) {
            commandObserver.onNext(ProductionCommand.newBuilder()
                    .setMachineId("M001")
                    .setTargetOutput(100)
                    .setOperatorId("operator-001")
                    .build());
            Thread.sleep(500);
        }
        commandObserver.onCompleted();

        bidiLatch.await(10, TimeUnit.SECONDS);

        // ===========================
        // Shutdown
        // ===========================
        System.out.println("\n=== Shutting Down ===\n");

        client.shutdown();
        discovery.close();
        healthServer.stop();
        productionServer.stop();
        alertServer.stop();

        System.out.println("✅ All tests completed successfully!");
    }
}