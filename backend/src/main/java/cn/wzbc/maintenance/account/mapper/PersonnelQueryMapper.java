package cn.wzbc.maintenance.account.mapper;
import cn.wzbc.maintenance.account.entity.UserEntity;
import java.util.*;
import org.apache.ibatis.annotations.*;
@Mapper
public interface PersonnelQueryMapper {
    class Row extends UserEntity { public long pendingRequests,unfinishedOrders,completedOrders,unfinishedTasks; }
    List<Row> list(@Param("q") Map<String,Object> query);
    long count(@Param("q") Map<String,Object> query);
    Map<String,Object> totals();
    Row person(@Param("id") long id);
}
