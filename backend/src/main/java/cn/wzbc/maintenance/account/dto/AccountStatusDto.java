package cn.wzbc.maintenance.account.dto;
import jakarta.validation.constraints.*;
public record AccountStatusDto(@NotBlank String operationId,@NotNull @Min(0) Integer version) {}
