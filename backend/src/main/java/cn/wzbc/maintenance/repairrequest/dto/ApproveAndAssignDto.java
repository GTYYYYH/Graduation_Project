package cn.wzbc.maintenance.repairrequest.dto;
import jakarta.validation.constraints.*;
public record ApproveAndAssignDto(String equipmentId,@NotBlank String technicianId,@NotNull Boolean confirmPreviouslyRefused,@NotNull @Min(0) Integer version,@NotBlank String operationId) {}
