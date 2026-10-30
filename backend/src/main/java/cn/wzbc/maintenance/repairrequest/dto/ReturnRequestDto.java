package cn.wzbc.maintenance.repairrequest.dto;
import jakarta.validation.constraints.*;
public record ReturnRequestDto(@NotBlank String operationId,@NotNull @Min(0) Integer version,@NotBlank String reason) {}
