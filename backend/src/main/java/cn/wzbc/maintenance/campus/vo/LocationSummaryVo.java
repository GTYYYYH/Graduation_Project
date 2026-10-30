package cn.wzbc.maintenance.campus.vo;

import java.util.List;

public record LocationSummaryVo(String locationId,String category,StageCounts counts,long visibleTotal,String queriedAt) {
    public record StageCounts(long pendingAccept,long reassignPending,long inProgress,long rework,long pendingAcceptance,long needsSupplement,long pendingReview) {
        public long total() { return pendingAccept+reassignPending+inProgress+rework+pendingAcceptance+needsSupplement+pendingReview; }
        public String category() {
            if(pendingAccept+reassignPending+inProgress+rework>0) return "FAULT_PROCESSING";
            if(pendingAcceptance>0) return "PENDING_ACCEPTANCE";
            if(needsSupplement>0) return "NEEDS_SUPPLEMENT";
            return pendingReview>0?"PENDING_REVIEW":"NONE";
        }
    }
    public record CampusSummariesVo(String configVersion,List<LocationSummaryVo> locations,String queriedAt) {}
}
