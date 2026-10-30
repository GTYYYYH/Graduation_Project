package cn.wzbc.maintenance.workorder.vo;

import cn.wzbc.maintenance.common.PageResult;
import cn.wzbc.maintenance.workorder.service.AssignmentService.RefusalReferenceVo;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AssignmentCandidateVo(String id,String name,boolean enabled,String reviewStatus,
        List<RefusalReferenceVo> refusal,boolean eligible,
        @JsonProperty("W") long W,@JsonProperty("I") long I,@JsonProperty("R") long R,@JsonProperty("A") long A,
        double load,long todayCompleted,String lastAssignedAt,String reason) {
    public record AssignmentCandidatesVo(List<AssignmentCandidateVo> top5,PageResult<AssignmentCandidateVo> candidates,
            long eligibleTotal,long preferredTotal,String queriedAt,String businessDate) {}
}
