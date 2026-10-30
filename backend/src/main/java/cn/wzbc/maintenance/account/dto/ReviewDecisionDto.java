package cn.wzbc.maintenance.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record ReviewDecisionDto(@NotBlank @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") String operationId,
        @NotNull @Min(0) Integer version,@NotNull @Min(1) Integer attempt,@NotNull @Min(0) Integer reviewVersion,
        @NotNull Boolean passed,@JsonProperty(required=true) String reason) {}
