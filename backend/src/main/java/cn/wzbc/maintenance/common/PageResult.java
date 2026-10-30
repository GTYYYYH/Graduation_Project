package cn.wzbc.maintenance.common;

import java.util.List;

public record PageResult<T>(List<T> records, long total, int page, int pageSize) {
    public PageResult {
        if (total < 0 || page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数超出允许范围");
        }
        records = List.copyOf(records);
    }
}
