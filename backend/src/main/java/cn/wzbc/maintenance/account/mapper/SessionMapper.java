package cn.wzbc.maintenance.account.mapper;

import cn.wzbc.maintenance.account.entity.SessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import org.apache.ibatis.annotations.*;
@Mapper
public interface SessionMapper extends BaseMapper<SessionEntity> {
    @Select("SELECT * FROM login_session WHERE token_digest=#{digest}") SessionEntity byDigest(byte[] digest);
    @Update("UPDATE login_session SET revoked_at=#{now},revoke_reason='LOGOUT' WHERE id=#{id} AND revoked_at IS NULL")
    int revokeCurrent(@Param("id") long id,@Param("now") Instant now);
    @Update("UPDATE login_session SET revoked_at=#{now},revoke_reason=#{reason} WHERE user_id=#{id} AND revoked_at IS NULL")
    int revokeAll(@Param("id") long id,@Param("now") Instant now,@Param("reason") String reason);
}
