package cn.wzbc.maintenance.workorder.dto;
import java.util.List;
public record SubmitRepairResultDto(Integer version,Integer round,String result,List<String> attachmentIds,String operationId) {}
