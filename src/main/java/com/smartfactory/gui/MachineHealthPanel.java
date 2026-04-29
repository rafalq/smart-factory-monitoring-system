package com.smartfactory.gui;

import com.smartfactory.client.SmartFactoryClient;
import com.smartfactory.grpc.*;
import io.grpc.stub.StreamObserver;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;

/**
 * GUI panel for the MachineHealthMonitor service.
 * Provides controls for unary, server-side streaming
 * and client-side streaming RPCs.
 */
public class MachineHealthPanel extends JPanel {

    private final SmartFactoryClient client;

    // Unary components
    private JComboBox<String> machineIdCombo;
    private JTextArea statusOutput;

    // Server streaming components
    private JComboBox<String> streamMachineCombo;
    private JTextArea streamOutput;
    private JButton startStreamBtn;
    private JButton stopStreamBtn;
    private volatile boolean streaming = false;

    // Client streaming components
    private DefaultTableModel sensorTableModel;
    private JTextArea sensorSummaryOutput;

    private static final String[] MACHINE_IDS = { "M001", "M002", "M003", "M004", "M005" };

    /**
     * Constructs the MachineHealthPanel with all UI components.
     *
     * @param client the gRPC client to use for service calls
     */
    public MachineHealthPanel(SmartFactoryClient client) {
        this.client = client;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(createUnarySection());
        add(Box.createVerticalStrut(10));
        add(createServerStreamingSection());
        add(Box.createVerticalStrut(10));
        add(createClientStreamingSection());
    }

    // ===========================
    // Unary Section
    // ===========================

    /**
     * Creates the unary RPC section for checking machine status.
     *
     * @return the configured panel
     */
    private JPanel createUnarySection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                "Unary RPC – Check Machine Status"));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        machineIdCombo = new JComboBox<>(MACHINE_IDS);
        JButton checkBtn = new JButton("Check Status");

        controls.add(new JLabel("Machine ID:"));
        controls.add(machineIdCombo);
        controls.add(checkBtn);

        statusOutput = new JTextArea(4, 60);
        statusOutput.setEditable(false);
        statusOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));

        checkBtn.addActionListener(e -> checkMachineStatus());

        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(statusOutput), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Calls checkMachineStatus unary RPC and displays the result.
     */
    private void checkMachineStatus() {
        String machineId = (String) machineIdCombo.getSelectedItem();
        MachineStatusResponse response = client.checkMachineStatus(machineId);

        if (response != null) {
            statusOutput.setText(
                    "Machine:     " + response.getMachineName() + "\n" +
                            "Temperature: " + response.getTemperature() + " °C\n" +
                            "Vibration:   " + response.getVibrationLevel() + " mm/s\n" +
                            "Operational: " + response.getIsOperational() + "\n" +
                            "Status:      " + response.getStatusMessage());
        } else {
            statusOutput.setText("❌ Failed to get machine status");
        }
    }

    // ===========================
    // Server Streaming Section
    // ===========================

    /**
     * Creates the server-side streaming section for temperature monitoring.
     *
     * @return the configured panel
     */
    private JPanel createServerStreamingSection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                "Server-side Streaming – Stream Temperature"));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        streamMachineCombo = new JComboBox<>(MACHINE_IDS);
        startStreamBtn = new JButton("Start Stream");
        stopStreamBtn = new JButton("Stop Stream");
        stopStreamBtn.setEnabled(false);

        controls.add(new JLabel("Machine ID:"));
        controls.add(streamMachineCombo);
        controls.add(startStreamBtn);
        controls.add(stopStreamBtn);

        streamOutput = new JTextArea(4, 60);
        streamOutput.setEditable(false);
        streamOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));

        startStreamBtn.addActionListener(e -> startTemperatureStream());
        stopStreamBtn.addActionListener(e -> stopTemperatureStream());

        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(streamOutput), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Starts server-side streaming of temperature readings.
     */
    private void startTemperatureStream() {
        streaming = true;
        startStreamBtn.setEnabled(false);
        stopStreamBtn.setEnabled(true);
        streamOutput.setText("");

        String machineId = (String) streamMachineCombo.getSelectedItem();

        client.streamTemperature(machineId, 1,
                new StreamObserver<TemperatureReading>() {

                    @Override
                    public void onNext(TemperatureReading reading) {
                        if (!streaming)
                            return;
                        SwingUtilities.invokeLater(() -> streamOutput.append(
                                "[" + reading.getTimestamp() + "] "
                                        + reading.getTemperature() + " °C"
                                        + (reading.getIsCritical() ? " [CRITICAL]" : "")
                                        + "\n"));
                    }

                    @Override
                    public void onError(Throwable t) {
                        SwingUtilities.invokeLater(() -> {
                            streamOutput.append("❌ Stream error: "
                                    + t.getMessage() + "\n");
                            resetStreamButtons();
                        });
                    }

                    @Override
                    public void onCompleted() {
                        SwingUtilities.invokeLater(() -> {
                            streamOutput.append("[COMPLETED] Stream completed\n");
                            resetStreamButtons();
                        });
                    }
                });
    }

    /**
     * Stops the temperature stream.
     */
    private void stopTemperatureStream() {
        streaming = false;
        resetStreamButtons();
        streamOutput.append("[STOPPED] Stream stopped by user\n");
    }

    /**
     * Resets stream button states after streaming ends.
     */
    private void resetStreamButtons() {
        startStreamBtn.setEnabled(true);
        stopStreamBtn.setEnabled(false);
    }

    // ===========================
    // Client Streaming Section
    // ===========================

    /**
     * Creates the client-side streaming section for sensor data reporting.
     *
     * @return the configured panel
     */
    private JPanel createClientStreamingSection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                "Client-side Streaming – Report Sensor Data"));

        // Table for entering sensor readings
        String[] columns = {
                "Machine ID", "Temperature", "Vibration", "Pressure" };
        sensorTableModel = new DefaultTableModel(columns, 0);
        JTable sensorTable = new JTable(sensorTableModel);

        // Add default rows
        sensorTableModel.addRow(new Object[] { "M001", "72.5", "3.2", "4.1" });
        sensorTableModel.addRow(new Object[] { "M001", "75.1", "3.5", "4.3" });
        sensorTableModel.addRow(new Object[] { "M001", "78.3", "3.8", "4.5" });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addRowBtn = new JButton("Add Row");
        JButton removeRowBtn = new JButton("Remove Row");
        JButton sendBtn = new JButton("Send Reports");

        buttonPanel.add(addRowBtn);
        buttonPanel.add(removeRowBtn);
        buttonPanel.add(sendBtn);

        sensorSummaryOutput = new JTextArea(3, 60);
        sensorSummaryOutput.setEditable(false);
        sensorSummaryOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));

        addRowBtn.addActionListener(e -> sensorTableModel.addRow(
                new Object[] { "M001", "70.0", "3.0", "4.0" }));

        removeRowBtn.addActionListener(e -> {
            int row = sensorTable.getSelectedRow();
            if (row >= 0)
                sensorTableModel.removeRow(row);
        });

        sendBtn.addActionListener(e -> sendSensorData());

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.add(new JScrollPane(sensorTable), BorderLayout.CENTER);
        tablePanel.add(buttonPanel, BorderLayout.SOUTH);

        panel.add(tablePanel, BorderLayout.CENTER);
        panel.add(new JScrollPane(sensorSummaryOutput), BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Sends all sensor readings from the table via client-side streaming RPC.
     */
    private void sendSensorData() {
        int rowCount = sensorTableModel.getRowCount();
        if (rowCount == 0) {
            sensorSummaryOutput.setText("❌ No sensor readings to send");
            return;
        }

        SensorData[] readings = new SensorData[rowCount];
        for (int i = 0; i < rowCount; i++) {
            readings[i] = SensorData.newBuilder()
                    .setMachineId(
                            sensorTableModel.getValueAt(i, 0).toString())
                    .setTemperature(Float.parseFloat(
                            sensorTableModel.getValueAt(i, 1).toString()))
                    .setVibrationLevel(Float.parseFloat(
                            sensorTableModel.getValueAt(i, 2).toString()))
                    .setPressure(Float.parseFloat(
                            sensorTableModel.getValueAt(i, 3).toString()))
                    .setTimestamp(Instant.now().toString())
                    .build();
        }

        try {
            SensorDataSummary summary = client.reportSensorData(readings);
            if (summary != null) {
                sensorSummaryOutput.setText(
                        "✅ Readings received: " + summary.getReadingsReceived() + "\n" +
                                "   Avg Temperature: " + summary.getAverageTemperature() + " °C\n" +
                                "   Summary: " + summary.getSummaryMessage());
            } else {
                sensorSummaryOutput.setText("❌ Failed to send sensor data");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sensorSummaryOutput.setText("❌ Interrupted: " + e.getMessage());
        }
    }
}