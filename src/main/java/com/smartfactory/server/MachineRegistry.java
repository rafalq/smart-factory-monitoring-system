package com.smartfactory.server;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Singleton registry that holds all simulated factory machines.
 * Provides a central store of MachineData instances accessible
 * by all gRPC services.
 */
public class MachineRegistry {

    private static MachineRegistry instance;
    private final Map<String, MachineData> machines;

    /**
     * Private constructor - initialises registry with default machines.
     */
    private MachineRegistry() {
        machines = new LinkedHashMap<>();
        initialiseDefaultMachines();
    }

    /**
     * Returns the singleton instance of MachineRegistry.
     *
     * @return the single MachineRegistry instance
     */
    public static synchronized MachineRegistry getInstance() {
        if (instance == null) {
            instance = new MachineRegistry();
        }
        return instance;
    }

    /**
     * Populates the registry with a set of default factory machines.
     */
    private void initialiseDefaultMachines() {
        addMachine(new MachineData("M001", "Hydraulic Press A"));
        addMachine(new MachineData("M002", "Conveyor Belt B"));
        addMachine(new MachineData("M003", "Welding Robot C"));
        addMachine(new MachineData("M004", "CNC Milling Machine D"));
        addMachine(new MachineData("M005", "Assembly Robot E"));
    }

    /**
     * Adds a machine to the registry.
     *
     * @param machine the MachineData instance to add
     */
    public void addMachine(MachineData machine) {
        machines.put(machine.getMachineId(), machine);
    }

    /**
     * Retrieves a machine by its ID.
     *
     * @param machineId the unique machine identifier
     * @return the MachineData instance, or null if not found
     */
    public MachineData getMachine(String machineId) {
        return machines.get(machineId);
    }

    /**
     * Returns all machines in the registry.
     *
     * @return collection of all MachineData instances
     */
    public Collection<MachineData> getAllMachines() {
        return machines.values();
    }

    /**
     * Checks whether a machine with the given ID exists.
     *
     * @param machineId the unique machine identifier
     * @return true if machine exists in registry
     */
    public boolean machineExists(String machineId) {
        return machines.containsKey(machineId);
    }
}