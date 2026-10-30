package cn.wzbc.maintenance.maintenance;

public interface LocalMaintenanceCommand {
    String name();
    MaintenanceResult execute();
    record Counts(int buildings, int floors, int locations, int reportable, int equipment) {}
    record MaintenanceResult(String command, boolean changed, String resourceId, String configVersion, Counts counts, String message) {}
}
