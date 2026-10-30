package cn.wzbc.maintenance.equipment.dto;
import jakarta.validation.constraints.NotBlank;
public record EquipmentStatusDto(@NotBlank String operationId,Integer version) {}
