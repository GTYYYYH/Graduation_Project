package cn.wzbc.maintenance.account.vo;
import cn.wzbc.maintenance.common.OperationResultService.Receipt;
public record PasswordResetResultVo(String kind,Receipt receipt,AccountReference account,String loginName,String newPassword) {
    public record AccountReference(String id,String name) {}
}
