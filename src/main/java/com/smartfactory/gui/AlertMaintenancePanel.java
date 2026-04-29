package com.smartfactory.gui;

import com.smartfactory.client.SmartFactoryClient;
import com.smartfactory.grpc.*;
import io.grpc.stub.StreamObserver;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;

/**
 * GUI panel for the AlertMaintenanceService.
 * Provides controls for unary, server-side streaming
 * and client-side streaming RPCs.
 */
public class AlertMaintenancePanel extends JPanel {

    private final SmartFactoryClient client;

    // Unary components
    private JComboBox<String> alertMachineCombo;
    private JComboBox<String> alertTypeCombo;
    private JSpinner severitySpinner;
    private JTextField descriptionField;
    private JTextArea alertOutput;

    // Server streaming components
    private JSpinner minSeveritySpinner;
    private JTextArea alertStreamOutput;
    private JButton startAlertStreamBtn;
    private JButton stopAlertStreamBtn;
    private volatile boolean streamingAlerts = false;

    // Client streaming components
    private DefaultTableModel maintenanceTableModel;
    private JTextArea maintenanceSummaryOutput;

    private static final String[] MACHINE_IDS = { "M001", "M002", "M003", "M004", "M005" };
    private static final String[] ALERT_TYPES = { "OVERHEAT", "VIBRATION", "PRESSURE", "ELECTRICAL", "MECHANICAL" };

    /**
     * Constructs the AlertMaintenancePanel with all UI components.
     *
     * @param client the gRPC client to use for service calls
     */
    public AlertMaintenancePanel(SmartFactoryClient client) {
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
     * Creates the unary RPC section for raising alerts.
     *
     * @return the configured panel
     */
    private JPanel createUnarySection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                "Unary RPC - Raise Alert"));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        alertMachineCombo = new JComboBox<>(MACHINE_IDS);
        alertTypeCombo = new JComboBox<>(ALERT_TYPES);
        severitySpinner = new JSpinner(
                new SpinnerNumberModel(3, 1, 5, 1));
        descriptionField = new JTextField("Temperature rising above threshold", 25);
        JButton raiseBtn = new JButton("Raise Alert");

        controls.add(new JLabel("Machine:"));
        controls.add(alertMachineCombo);
        controls.add(new JLabel("Type:"));
        controls.add(alertTypeCombo);
        controls.add(new JLabel("Severity (1-5):"));
        controls.add(severitySpinner);
        controls.add(new JLabel("Description:"));
        controls.add(descriptionField);
        controls.add(raiseBtn);

        alertOutput = new JTextArea(6, 60);
        alertOutput.setEditable(false);
        alertOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        alertOutput.setMargin(new Insets(8, 8, 8, 8));

        raiseBtn.addActionListener(e -> raiseAlert());

        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(alertOutput), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Calls raiseAlert unary RPC and displays the result.
     */
    private void raiseAlert() {
        String machineId = (String) alertMachineCombo.getSelectedItem();
        String alertType = (String) alertTypeCombo.getSelectedItem();
        int severity = (int) severitySpinner.getValue();
        String description = descriptionField.getText().trim();

        if (description.isEmpty()) {
            alertOutput.setText("[ERROR] Description cannot be empty");
            return;
        }

        AlertResponse response = client.raiseAlert(
                machineId, alertType, severity, description);

        if (response != null) {
            alertOutput.setText(
                    "[OK] Alert ID: " + response.getAlertId() + "\n" +
                            "   " + response.getConfirmation() + "\n" +
                            "   Timestamp: " + response.getTimestamp());
        } else {
            alertOutput.setText("[ERROR] Failed to raise alert");
        }
    }

    // ===========================
    // Server Streaming Section
    // ===========================

    /**
     * Creates the server-side streaming section for live alerts.
     *
     * @return the configured panel
     */
    private JPanel createServerStreamingSection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                "Server-side Streaming - Live Alerts"));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        minSeveritySpinner = new JSpinner(
                new SpinnerNumberModel(3, 1, 5, 1));
        startAlertStreamBtn = new JButton("Start Alert Stream");
        stopAlertStreamBtn = new JButton("Stop Alert Stream");
        stopAlertStreamBtn.setEnabled(false);

        controls.add(new JLabel("Min Severity:"));
        controls.add(minSeveritySpinner);
        controls.add(startAlertStreamBtn);
        controls.add(stopAlertStreamBtn);

        alertStreamOutput = new JTextArea(6, 60);
        alertStreamOutput.setEditable(false);
        alertStreamOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        alertStreamOutput.setMargin(new Insets(8, 8, 8, 8));

        startAlertStreamBtn.addActionListener(e -> startAlertStream());
        stopAlertStreamBtn.addActionListener(e -> stopAlertStream());

        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(alertStreamOutput), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Starts server-side streaming of live alerts.
     */
    private void startAlertStream() {
        streamingAlerts = true;
        startAlertStreamBtn.setEnabled(false);
        stopAlertStreamBtn.setEnabled(true);
        alertStreamOutput.setText("");

        int minSeverity = (int) minSeveritySpinner.getValue();

        client.streamAlerts(minSeverity, new StreamObserver<LiveAlert>() {

            @Override
            public void onNext(LiveAlert alert) {
                if (!streamingAlerts)
                    return;
                SwingUtilities.invokeLater(() -> alertStreamOutput.append(
                        "[" + alert.getTimestamp() + "] "
                                + "[ALERT] " + alert.getAlertType()
                                + " | Machine: " + alert.getMachineId()
                                + " | Severity: " + alert.getSeverity()
                                + " | " + alert.getDescription() + "\n"));
            }

            @Override
            public void onError(Throwable t) {
                SwingUtilities.invokeLater(() -> {
                    alertStreamOutput.append(
                            "[ERROR] Stream error: " + t.getMessage() + "\n");
                    resetAlertStreamButtons();
                });
            }

            @Override
            public void onCompleted() {
                SwingUtilities.invokeLater(() -> {
                    alertStreamOutput.append("[COMPLETED] Alert stream completed\n");
                    resetAlertStreamButtons();
                });
            }
        });
    }

    /**
     * Stops the live alert stream.
     */
    private void stopAlertStream() {
        streamingAlerts = false;
        resetAlertStreamButtons();
        alertStreamOutput.append("[STOPPED] Alert stream stopped by user\n");
    }

    /**
     * Resets alert stream button states.
     */
    private void resetAlertStreamButtons() {
        startAlertStreamBtn.setEnabled(true);
        stopAlertStreamBtn.setEnabled(false);
    }

    // ===========================
    // Client Streaming Section
    // ===========================

    /**
     * Creates the client-side streaming section for maintenance reports.
     *
     * @return the configured panel
     */
    private JPanel createClientStreamingSection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                "Client-side Streaming - Submit Maintenance Reports"));

        String[] columns = {
                "Machine ID", "Technician ID", "Action Taken", "Parts Replaced" };
        maintenanceTableModel = new DefaultTableModel(columns, 0);
        JTable maintenanceTable = new JTable(maintenanceTableModel);
        maintenanceTable.setRowHeight(22);

        // Add default rows
        maintenanceTableModel.addRow(new Object[] {
                "M001", "tech-001", "Replaced cooling fan", "Fan CF-200" });
        maintenanceTableModel.addRow(new Object[] {
                "M002", "tech-001", "Lubricated conveyor belt", "None" });

        // Fixed height scroll pane for table - only 4 rows visible
        JScrollPane tableScrollPane = new JScrollPane(maintenanceTable);
        tableScrollPane.setPreferredSize(new Dimension(0, 110));
        tableScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addRowBtn = new JButton("Add Row");
        JButton removeRowBtn = new JButton("Remove Row");
        JButton submitBtn = new JButton("Submit Reports");

        buttonPanel.add(addRowBtn);
        buttonPanel.add(removeRowBtn);
        buttonPanel.add(submitBtn);

        maintenanceSummaryOutput = new JTextArea(4, 60);
        maintenanceSummaryOutput.setEditable(false);
        maintenanceSummaryOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        maintenanceSummaryOutput.setMargin(new Insets(8, 8, 8, 8));

        addRowBtn.addActionListener(e -> maintenanceTableModel.addRow(new Object[] {
                "M001", "tech-001", "Inspection", "None" }));

        removeRowBtn.addActionListener(e -> {
            int row = maintenanceTable.getSelectedRow();
            if (row >= 0)
                maintenanceTableModel.removeRow(row);
        });

        submitBtn.addActionListener(e -> submitMaintenanceReports());

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        tablePanel.add(buttonPanel, BorderLayout.SOUTH);

        panel.add(tablePanel, BorderLayout.CENTER);
        panel.add(new JScrollPane(maintenanceSummaryOutput), BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Submits all maintenance reports from the table via client-side streaming.
     */
    private void submitMaintenanceReports() {
        int rowCount = maintenanceTableModel.getRowCount();
        if (rowCount == 0) {
            maintenanceSummaryOutput.setText(
                    "[ERROR] No maintenance reports to submit");
            return;
        }

        MaintenanceReport[] reports = new MaintenanceReport[rowCount];
        for (int i = 0; i < rowCount; i++) {
            reports[i] = MaintenanceReport.newBuilder()
                    .setMachineId(
                            maintenanceTableModel.getValueAt(i, 0).toString())
                    .setTechnicianId(
                            maintenanceTableModel.getValueAt(i, 1).toString())
                    .setActionTaken(
                            maintenanceTableModel.getValueAt(i, 2).toString())
                    .setPartsReplaced(
                            maintenanceTableModel.getValueAt(i, 3).toString())
                    .setTimestamp(Instant.now().toString())
                    .build();
        }

        try {
            MaintenanceSummary summary = client.submitMaintenanceReports(reports);
            if (summary != null) {
                maintenanceSummaryOutput.setText(
                        "[OK] Reports received: " + summary.getReportsReceived() + "\n" +
                                "   Machines serviced: " + summary.getMachinesServiced() + "\n" +
                                "   " + summary.getConfirmation());
            } else {
                maintenanceSummaryOutput.setText(
                        "[ERROR] Failed to submit maintenance reports");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            maintenanceSummaryOutput.setText(
                    "[ERROR] Interrupted: " + e.getMessage());
        }
    }
}
