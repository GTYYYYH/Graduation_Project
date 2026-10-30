package cn.wzbc.maintenance.equipment.dto;
import jakarta.validation.constraints.NotBlank;
public record EquipmentSaveDto(@NotBlank String operationId,Integer version,@NotBlank String name,@NotBlank String locationId,@NotBlank String addressText,@NotBlank String facilityType,String reason) {}
