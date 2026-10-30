package cn.wzbc.maintenance.account.mapper;

import cn.wzbc.maintenance.account.entity.RegistrationApplicationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RegistrationApplicationMapper extends BaseMapper<RegistrationApplicationEntity> {
    @Select("SELECT * FROM registration_review WHERE user_id=#{id} ORDER BY attempt DESC,id DESC") List<RegistrationApplicationEntity> history(long id);
    @Select("SELECT * FROM registration_review WHERE user_id=#{id} AND attempt=#{attempt} FOR UPDATE")
    RegistrationApplicationEntity lockCurrent(@Param("id") long id,@Param("attempt") int attempt);
}
