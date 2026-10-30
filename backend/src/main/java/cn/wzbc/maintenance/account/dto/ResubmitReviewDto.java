package cn.wzbc.maintenance.account.dto;

import jakarta.validation.constraints.*;
public record ResubmitReviewDto(@NotBlank @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") String operationId,
        @NotNull @Min(0) Integer version,@NotNull @Min(1) Integer attempt,@NotNull @Min(0) Integer reviewVersion,
        @NotBlank String name,@NotBlank @Pattern(regexp="1[3-9][0-9]{9}") String phone) {}
