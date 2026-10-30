package cn.wzbc.maintenance.account.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record LoginDto(@NotBlank @Size(max=60) String loginName,@NotBlank @Size(max=64) String password) {}
