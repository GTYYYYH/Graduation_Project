package cn.wzbc.maintenance.repairrequest.dto;
import jakarta.validation.constraints.*;
public record DuplicateCloseDto(@NotBlank String equipmentId,@NotBlank String reason,@NotNull @Min(0) Integer version,@NotBlank String operationId) {}
