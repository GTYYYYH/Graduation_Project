package cn.wzbc.maintenance.account.dto;
import jakarta.validation.constraints.*;
public record UpdateProfileDto(@NotBlank String operationId,@NotNull @Min(0) Integer version,@NotBlank @Size(max=200) String name,@NotBlank @Pattern(regexp="1[3-9][0-9]{9}") String phone) {}
