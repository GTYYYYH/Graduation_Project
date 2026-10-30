package cn.wzbc.maintenance.attachment;
/** Formal target only. Caller supplies the server-generated submission/round identity. */
public record AttachmentTarget(String kind,long id,Integer round) {}
