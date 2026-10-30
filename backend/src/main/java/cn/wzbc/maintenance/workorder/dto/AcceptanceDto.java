package cn.wzbc.maintenance.workorder.dto;
public record AcceptanceDto(Integer version,Integer round,Boolean passed,String reason,String operationId) {}
