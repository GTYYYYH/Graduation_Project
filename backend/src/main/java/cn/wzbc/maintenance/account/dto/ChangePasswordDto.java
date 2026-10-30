package cn.wzbc.maintenance.account.dto;
import jakarta.validation.constraints.*;
public record ChangePasswordDto(@NotBlank String operationId,@NotNull @Min(0) Integer version,@NotBlank @Size(max=64) String currentPassword,@NotBlank @Size(min=8,max=64) @Pattern(regexp="(?s)(?=.*[A-Za-z])(?=.*[0-9]).*") String newPassword,@NotBlank @Size(max=64) String confirmNewPassword) {}
