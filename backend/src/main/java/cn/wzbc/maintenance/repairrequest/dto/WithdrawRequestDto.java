package cn.wzbc.maintenance.repairrequest.dto;
import jakarta.validation.constraints.*;
public record WithdrawRequestDto(@NotBlank String operationId,@NotNull @Min(0) Integer version) {}
