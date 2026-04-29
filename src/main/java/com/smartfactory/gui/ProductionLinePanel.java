package com.smartfactory.gui;

import com.smartfactory.client.SmartFactoryClient;
import com.smartfactory.grpc.*;
import io.grpc.stub.StreamObserver;

import javax.swing.*;
import java.awt.*;

/**
 * GUI panel for the ProductionLineController service.
 * Provides controls for unary and bidirectional streaming RPCs.
 */
public class ProductionLinePanel extends JPanel {

    private final SmartFactoryClient client;

    // Unary components
    private JComboBox<String> machineCombo;
    private JComboBox<String> commandCombo;
    private JTextField operatorField;
    private JTextArea controlOutput;

    // Bidi streaming components
    private JComboBox<String> monitorMachineCombo;
    private JSpinner targetOutputSpinner;
    private JTextArea monitorOutput;
    private JButton startMonitorBtn;
    private JButton stopMonitorBtn;
    private StreamObserver<ProductionCommand> commandObserver;

    private static final String[] MACHINE_IDS = { "M001", "M002", "M003", "M004", "M005" };

    /**
     * Constructs the ProductionLinePanel with all UI components.
     *
     * @param client the gRPC client to use for service calls
     */
    public ProductionLinePanel(SmartFactoryClient client) {
        this.client = client;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(createUnarySection());
        add(Box.createVerticalStrut(10));
        add(createBidiStreamingSection());
    }

    // ===========================
    // Unary Section
    // ===========================

    /**
     * Creates the unary RPC section for starting/stopping machines.
     *
     * @return the configured panel
     */
    private JPanel createUnarySection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                "Unary RPC - Start / Stop Machine"));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        machineCombo = new JComboBox<>(MACHINE_IDS);
        commandCombo = new JComboBox<>(new String[] { "START", "STOP" });
        operatorField = new JTextField("operator-001", 12);
        JButton sendBtn = new JButton("Send Command");

        controls.add(new JLabel("Machine:"));
        controls.add(machineCombo);
        controls.add(new JLabel("Command:"));
        controls.add(commandCombo);
        controls.add(new JLabel("Operator:"));
        controls.add(operatorField);
        controls.add(sendBtn);

        controlOutput = new JTextArea(4, 60);
        controlOutput.setEditable(false);
        controlOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));

        sendBtn.addActionListener(e -> sendMachineCommand());

        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(controlOutput), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Sends a start/stop command to the production line controller.
     */
    private void sendMachineCommand() {
        String machineId = (String) machineCombo.getSelectedItem();
        String command = (String) commandCombo.getSelectedItem();
        String operatorId = operatorField.getText().trim();

        if (operatorId.isEmpty()) {
            controlOutput.setText("[ERROR] Operator ID cannot be empty");
            return;
        }

        MachineControlResponse response = client.startStopMachine(machineId, command, operatorId);

        if (response != null) {
            controlOutput.setText(
                    "[OK] " + response.getConfirmation() + "\n" +
                            "   Machine running: " + response.getIsRunning() + "\n" +
                            "   Timestamp: " + response.getTimestamp());
        } else {
            controlOutput.setText(
                    "[ERROR] Command failed - check if machine is already "
                            + (command.equals("START") ? "running" : "stopped"));
        }
    }

    // ===========================
    // Bidirectional Streaming Section
    // ===========================

    /**
     * Creates the bidirectional streaming section for production monitoring.
     *
     * @return the configured panel
     */
    private JPanel createBidiStreamingSection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                "Bidirectional Streaming - Monitor Production"));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        monitorMachineCombo = new JComboBox<>(MACHINE_IDS);
        targetOutputSpinner = new JSpinner(
                new SpinnerNumberModel(100, 1, 1000, 10));
        startMonitorBtn = new JButton("Start Monitoring");
        stopMonitorBtn = new JButton("Stop Monitoring");
        stopMonitorBtn.setEnabled(false);

        controls.add(new JLabel("Machine:"));
        controls.add(monitorMachineCombo);
        controls.add(new JLabel("Target Output:"));
        controls.add(targetOutputSpinner);
        controls.add(startMonitorBtn);
        controls.add(stopMonitorBtn);

        monitorOutput = new JTextArea(8, 60);
        monitorOutput.setEditable(false);
        monitorOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));

        startMonitorBtn.addActionListener(e -> startMonitoring());
        stopMonitorBtn.addActionListener(e -> stopMonitoring());

        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(monitorOutput), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Starts bidirectional streaming for production monitoring.
     */
    private void startMonitoring() {
        startMonitorBtn.setEnabled(false);
        stopMonitorBtn.setEnabled(true);
        monitorOutput.setText("");

        String machineId = (String) monitorMachineCombo.getSelectedItem();
        int targetOutput = (int) targetOutputSpinner.getValue();

        commandObserver = client.monitorProduction(
                new StreamObserver<ProductionStatus>() {

                    @Override
                    public void onNext(ProductionStatus status) {
                        SwingUtilities.invokeLater(() -> monitorOutput.append(
                                "[" + status.getTimestamp() + "]\n" +
                                        "  Output: " + status.getCurrentOutput()
                                        + " units/hr\n" +
                                        "  Efficiency: " + status.getEfficiency() + "%\n" +
                                        "  Units produced: " + status.getUnitsProduced() + "\n" +
                                        (status.getWarning().isEmpty()
                                                ? ""
                                                : "  [WARNING] " + status.getWarning() + "\n")
                                        + "\n"));
                    }

                    @Override
                    public void onError(Throwable t) {
                        SwingUtilities.invokeLater(() -> {
                            monitorOutput.append(
                                    "[ERROR] Monitor error: " + t.getMessage() + "\n");
                            resetMonitorButtons();
                        });
                    }

                    @Override
                    public void onCompleted() {
                        SwingUtilities.invokeLater(() -> {
                            monitorOutput.append("[COMPLETED] Monitoring completed\n");
                            resetMonitorButtons();
                        });
                    }
                });

        // Send commands in background thread
        new Thread(() -> {
            try {
                while (stopMonitorBtn.isEnabled()) {
                    commandObserver.onNext(ProductionCommand.newBuilder()
                            .setMachineId(machineId)
                            .setTargetOutput(targetOutput)
                            .setOperatorId("operator-001")
                            .build());
                    Thread.sleep(2000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Stops the bidirectional production monitoring stream.
     */
    private void stopMonitoring() {
        if (commandObserver != null) {
            commandObserver.onCompleted();
        }
        resetMonitorButtons();
        monitorOutput.append("[STOPPED] Monitoring stopped by user\n");
    }

    /**
     * Resets monitor button states.
     */
    private void resetMonitorButtons() {
        startMonitorBtn.setEnabled(true);
        stopMonitorBtn.setEnabled(false);
    }
}
