package com.smartfactory.server;

/**
 * Represents the simulated state of a factory machine.
 * Used internally by services to simulate real sensor data.
 */
public class MachineData {

    private final String machineId;
    private final String machineName;
    private float temperature;
    private float vibrationLevel;
    private float pressure;
    private boolean isOperational;
    private String statusMessage;

    // Critical thresholds
    public static final float CRITICAL_TEMPERATURE = 85.0f;
    public static final float CRITICAL_VIBRATION = 7.5f;
    public static final float CRITICAL_PRESSURE = 10.0f;

    /**
     * Constructs a MachineData instance with initial values.
     *
     * @param machineId   Unique identifier for the machine
     * @param machineName Human-readable name of the machine
     */
    public MachineData(String machineId, String machineName) {
        this.machineId = machineId;
        this.machineName = machineName;
        this.temperature = 20.0f;
        this.vibrationLevel = 0.0f;
        this.pressure = 1.0f;
        this.isOperational = false;
        this.statusMessage = "Machine initialised";
    }

    /**
     * Simulates a sensor update by generating realistic random fluctuations.
     * Temperature, vibration and pressure change slightly each call.
     */
    public void simulateSensorUpdate() {
        if (isOperational) {
            temperature += (float) (Math.random() * 4 - 1);
            vibrationLevel += (float) (Math.random() * 0.4 - 0.1);
            pressure += (float) (Math.random() * 0.3 - 0.1);

            // Clamp values to realistic ranges
            temperature = Math.max(15.0f, Math.min(temperature, 100.0f));
            vibrationLevel = Math.max(0.0f, Math.min(vibrationLevel, 10.0f));
            pressure = Math.max(0.5f, Math.min(pressure, 12.0f));

            updateStatusMessage();
        }
    }

    /**
     * Updates the status message based on current sensor readings.
     */
    private void updateStatusMessage() {
        if (temperature >= CRITICAL_TEMPERATURE) {
            statusMessage = "WARNING: Critical temperature detected";
        } else if (vibrationLevel >= CRITICAL_VIBRATION) {
            statusMessage = "WARNING: Critical vibration detected";
        } else if (pressure >= CRITICAL_PRESSURE) {
            statusMessage = "WARNING: Critical pressure detected";
        } else {
            statusMessage = "Machine operating normally";
        }
    }

    /**
     * Returns true if any sensor reading exceeds critical threshold.
     *
     * @return true if temperature is critical
     */
    public boolean isCriticalTemperature() {
        return temperature >= CRITICAL_TEMPERATURE;
    }

    // ===========================
    // Getters and Setters
    // ===========================

    public String getMachineId() {
        return machineId;
    }

    public String getMachineName() {
        return machineName;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public float getVibrationLevel() {
        return vibrationLevel;
    }

    public void setVibrationLevel(float vibrationLevel) {
        this.vibrationLevel = vibrationLevel;
    }

    public float getPressure() {
        return pressure;
    }

    public void setPressure(float pressure) {
        this.pressure = pressure;
    }

    public boolean isOperational() {
        return isOperational;
    }

    public void setOperational(boolean operational) {
        isOperational = operational;
        if (operational) {
            statusMessage = "Machine started successfully";
            temperature = 20.0f + (float) (Math.random() * 10);
            vibrationLevel = 0.5f + (float) (Math.random() * 1.5);
            pressure = 1.0f + (float) (Math.random() * 2);
        } else {
            statusMessage = "Machine stopped";
            temperature = 20.0f;
            vibrationLevel = 0.0f;
            pressure = 1.0f;
        }
    }

    public String getStatusMessage() {
        return statusMessage;
    }
}