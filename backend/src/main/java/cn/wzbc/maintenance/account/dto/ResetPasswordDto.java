package cn.wzbc.maintenance.account.dto;
import jakarta.validation.constraints.*;
public record ResetPasswordDto(@NotBlank String operationId,@NotNull @Min(0) Integer version) {}
