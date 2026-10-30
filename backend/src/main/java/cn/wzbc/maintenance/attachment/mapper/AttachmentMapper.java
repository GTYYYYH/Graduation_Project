package cn.wzbc.maintenance.attachment.mapper;
import cn.wzbc.maintenance.attachment.entity.AttachmentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface AttachmentMapper extends BaseMapper<AttachmentEntity> {
    @Select("SELECT * FROM attachment_file WHERE id=#{id} FOR UPDATE") AttachmentEntity lock(long id);
    @Select("SELECT * FROM attachment_file WHERE uploader_id=#{actor} AND upload_operation_id=#{operation}")
    AttachmentEntity operation(@Param("actor") long actor,@Param("operation") String operation);
}
