package cn.wzbc.maintenance.repairrequest.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record SupplementRequestDto(String locationId,@NotBlank String equipmentName,String equipmentCategory,
    @NotBlank String addressText,@NotBlank String description,@NotBlank String contactName,@NotBlank @Pattern(regexp="^1[3-9][0-9]{9}$") String contactPhone,
    @NotNull @Size(max=5) List<@NotBlank String> attachmentIds,@NotBlank String operationId,@NotNull @Min(0) Integer version) {}
