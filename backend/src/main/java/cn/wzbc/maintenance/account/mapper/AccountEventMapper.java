package cn.wzbc.maintenance.account.mapper;

import cn.wzbc.maintenance.account.entity.AccountEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface AccountEventMapper extends BaseMapper<AccountEventEntity> {
    @Select("SELECT * FROM account_event WHERE actor_user_id=#{actor} AND operation_type=#{type} AND operation_id=#{operation}")
    AccountEventEntity operation(@Param("actor") long actor,@Param("type") String type,@Param("operation") String operation);
}
