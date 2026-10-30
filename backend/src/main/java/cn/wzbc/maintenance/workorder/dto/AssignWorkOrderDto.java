package cn.wzbc.maintenance.workorder.dto;
public record AssignWorkOrderDto(Integer version,String technicianId,String reason,Boolean confirmPreviouslyRefused,String operationId) {}
