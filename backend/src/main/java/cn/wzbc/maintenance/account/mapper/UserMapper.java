package cn.wzbc.maintenance.account.mapper;

import cn.wzbc.maintenance.account.entity.UserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
    @Select("SELECT * FROM user_account WHERE provision_key='campus-admin'") UserEntity provisionedAdmin();
    @Select("SELECT * FROM user_account WHERE login_name=#{name}") UserEntity byLoginName(String name);
    @Select("SELECT * FROM user_account WHERE id=#{id} FOR UPDATE") UserEntity lock(long id);
    @Select({"<script>SELECT * FROM user_account WHERE id IN <foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY id FOR UPDATE</script>"})
    List<UserEntity> lockOrdered(@Param("ids") List<Long> ids);
}
