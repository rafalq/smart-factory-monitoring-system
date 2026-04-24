# Smart Factory Monitoring System

A distributed Java application simulating a smart factory environment, 
developed as part of the Distributed Systems module 
(Higher Diploma in Science in Computing).

## UN Sustainable Development Goal
**SDG 9 – Industry, Innovation and Infrastructure**

## Project Overview
This system simulates an intelligent factory environment consisting 
of three smart services that communicate via gRPC, register and 
discover each other using JmDNS (mDNS), and are controlled through 
a Java Swing GUI client.

## Services
- **Machine Health Monitor** – monitors temperature, vibration and 
  machine status in real time
- **Production Line Controller** – manages production line operations 
  and monitors output
- **Alert and Maintenance Service** – handles fault detection and 
  maintenance reporting

## Technologies
- Java
- gRPC (all four communication types)
- Protocol Buffers (protobuf)
- JmDNS (mDNS Naming Service)
- Java Swing (GUI)
- Maven

## gRPC Communication Types Used
| Type | Service | Method |
|---|---|---|
| Unary | MachineHealthMonitor | checkMachineStatus |
| Unary | ProductionLineController | startStopMachine |
| Unary | AlertMaintenanceService | raiseAlert |
| Server-side Streaming | MachineHealthMonitor | streamTemperature |
| Server-side Streaming | AlertMaintenanceService | streamAlerts |
| Client-side Streaming | MachineHealthMonitor | reportSensorData |
| Client-side Streaming | AlertMaintenanceService | submitMaintenanceReport |
| Bidirectional Streaming | ProductionLineController | monitorProduction |

## Project Structure
```
smart-factory-monitoring-system/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/smartfactory/
│   │   │       ├── server/
│   │   │       ├── client/
│   │   │       ├── gui/
│   │   │       └── naming/
│   │   └── proto/
│   └── test/
│       └── java/
├── pom.xml
└── README.md
```

## Setup and Running
*Instructions will be added as the project develops.*
