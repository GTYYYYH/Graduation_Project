package cn.wzbc.maintenance.attachment.vo;
import cn.wzbc.maintenance.attachment.entity.AttachmentEntity;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
public record AttachmentVo(String id,String originalName,String mediaType,long byteSize,String purpose,String state,String uploadedAt,String expiresAt,String contentUrl) {
    public static AttachmentVo from(AttachmentEntity e) { return new AttachmentVo(e.id.toString(),e.originalName,e.mediaType,e.byteSize,e.purpose,e.state,CurrentUserVo.instant(e.uploadedAt),e.state.equals("BOUND")?null:CurrentUserVo.instant(e.expiresAt),"/api/attachments/"+e.id+"/content"); }
}
