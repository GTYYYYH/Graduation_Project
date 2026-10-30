package cn.wzbc.maintenance.account.vo;

import cn.wzbc.maintenance.account.entity.*;
import cn.wzbc.maintenance.security.CurrentUser;
import java.util.List;

public record TechnicianReviewVo(AccountProfileVo account,ReviewSubmissionVo current,List<ReviewSubmissionVo> history,List<String> allowedActions,String queriedAt) {
    public record AccountReferenceVo(String id,String name) {}
    public record AccountProfileVo(String id,String loginName,String name,String phone,CurrentUser.Role role,boolean enabled,
            CurrentUser.ReviewStatus reviewStatus,int version,CurrentUserVo.AccountCapabilities capabilities,List<String> allowedActions) {
        public static AccountProfileVo from(UserEntity u,List<String> actions) {
            var c=CurrentUserVo.from(u);
            return new AccountProfileVo(c.id(),c.loginName(),c.name(),c.phone(),c.role(),c.enabled(),c.reviewStatus(),c.version(),c.capabilities(),actions);
        }
    }
    public record ReviewSubmissionVo(String id,int attempt,String nameSnapshot,String phoneSnapshot,String submittedAt,
            CurrentUser.ReviewStatus status,int version,AccountReferenceVo reviewedBy,String reviewedAt,String reason) {}
    public record MutationReceiptVo(String operationId,String resourceType,String resourceId,Integer resultVersion,Integer resultRound,
            String committedAt,String relatedResourceType,String relatedResourceId) {
        public static MutationReceiptVo from(AccountEventEntity event) {
            return new MutationReceiptVo(event.operationId,"ACCOUNT",event.resultResourceId.toString(),event.resultVersion,null,
                    CurrentUserVo.instant(event.occurredAt),"REGISTRATION_REVIEW",event.reviewId.toString());
        }
    }
    public record PendingReviewVo(String id,String loginName,String name,String phone,boolean enabled,int reviewAttempt,String submittedAt) {}
}
