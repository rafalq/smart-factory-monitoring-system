package com.smartfactory.gui;

import com.smartfactory.client.SmartFactoryClient;
import com.smartfactory.naming.ServiceDiscovery;
import com.smartfactory.server.AlertMaintenanceServer;
import com.smartfactory.server.MachineHealthServer;
import com.smartfactory.server.ProductionLineServer;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Main GUI frame for the Smart Factory Monitoring System.
 * Starts all three gRPC servers, discovers them via JmDNS,
 * and provides a tabbed interface to interact with each service.
 */
public class SmartFactoryGUI extends JFrame {

    private static final Logger logger = Logger.getLogger(SmartFactoryGUI.class.getName());

    private SmartFactoryClient client;
    private ServiceDiscovery serviceDiscovery;

    private MachineHealthServer healthServer;
    private ProductionLineServer productionServer;
    private AlertMaintenanceServer alertServer;

    private JTabbedPane tabbedPane;
    private JLabel statusBar;

    /**
     * Constructs the main GUI frame and initialises all components.
     */
    public SmartFactoryGUI() {
        super("Smart Factory Monitoring System - SDG 9");
        initialiseServers();
        initialiseClient();
        initialiseUI();
    }

    /**
     * Starts all three gRPC servers in background threads.
     */
    private void initialiseServers() {
        try {
            healthServer = new MachineHealthServer();
            productionServer = new ProductionLineServer();
            alertServer = new AlertMaintenanceServer();

            healthServer.start();
            productionServer.start();
            alertServer.start();

            // Wait for JmDNS registration
            Thread.sleep(2000);

            logger.info("All servers started successfully");

        } catch (IOException | InterruptedException e) {
            logger.severe("Failed to start servers: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Failed to start servers: " + e.getMessage(),
                    "Server Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Initialises the gRPC client and connects to all services via JmDNS.
     */
    private void initialiseClient() {
        try {
            serviceDiscovery = new ServiceDiscovery();
            Thread.sleep(2000);

            client = new SmartFactoryClient(serviceDiscovery);
            client.connectToServices();

            logger.info("Client connected to all services");

        } catch (IOException | InterruptedException e) {
            logger.severe("Failed to initialise client: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Failed to connect to services: " + e.getMessage(),
                    "Client Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Builds and displays the main UI with tabbed panels for each service.
     */
    private void initialiseUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1000, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Header panel
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Tabbed pane with three service panels
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab(
                "Machine Health",
                new MachineHealthPanel(client));
        tabbedPane.addTab(
                "Production Line",
                new ProductionLinePanel(client));
        tabbedPane.addTab(
                "Alert & Maintenance",
                new AlertMaintenancePanel(client));

        add(tabbedPane, BorderLayout.CENTER);

        // Status bar at bottom
        statusBar = new JLabel(
                " Connected to all services | SDG 9 – Industry, Innovation and Infrastructure");
        statusBar.setBorder(BorderFactory.createLoweredBevelBorder());
        statusBar.setFont(new Font("Arial", Font.PLAIN, 12));
        add(statusBar, BorderLayout.SOUTH);

        // Handle window close
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                handleShutdown();
            }
        });

        setVisible(true);
    }

    /**
     * Creates the header panel with title and description.
     *
     * @return the configured header panel
     */
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(33, 37, 41));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel titleLabel = new JLabel(
                "Smart Factory Monitoring System");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);

        JLabel subtitleLabel = new JLabel(
                "SDG 9 – Industry, Innovation and Infrastructure | gRPC Distributed System");
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        subtitleLabel.setForeground(new Color(173, 181, 189));

        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);

        return headerPanel;
    }

    /**
     * Gracefully shuts down all servers and closes the application.
     */
    private void handleShutdown() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to exit?",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                if (client != null)
                    client.shutdown();
                if (serviceDiscovery != null)
                    serviceDiscovery.close();
                if (healthServer != null)
                    healthServer.stop();
                if (productionServer != null)
                    productionServer.stop();
                if (alertServer != null)
                    alertServer.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            dispose();
            System.exit(0);
        }
    }

    /**
     * Entry point for the Smart Factory GUI application.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(SmartFactoryGUI::new);
    }
}