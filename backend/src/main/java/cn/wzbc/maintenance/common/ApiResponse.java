package cn.wzbc.maintenance.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;

public record ApiResponse<T>(String code, String message, T data, String requestId,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldError> errors) {
    public record FieldError(String field, String code, String message) {}
    public static final String REQUEST_ID_ATTRIBUTE = "campus.requestId";

    public static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (id == null) {
            id = UUID.randomUUID().toString();
            request.setAttribute(REQUEST_ID_ATTRIBUTE, id);
        }
        return id.toString();
    }

    public static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return new ApiResponse<>("OK", "成功", data, requestId(request), null);
    }
    public static ApiResponse<Void> error(String code, String message, HttpServletRequest request) {
        return new ApiResponse<>(code, message, null, requestId(request), null);
    }
}
