package cn.wzbc.maintenance.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> tooLarge(Exception error,HttpServletRequest request) {
        return ResponseEntity.status(413).body(ApiResponse.error("ATTACHMENT_TOO_LARGE","图片或上传请求超过大小限制",request));
    }
    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.bind.MissingRequestHeaderException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<ApiResponse<Void>> missingUpload(Exception error,HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_FAILED","上传或请求缺少必填字段",request));
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> business(BusinessException error, HttpServletRequest request) {
        return ResponseEntity.status(error.status()).body(ApiResponse.error(error.code(), error.getMessage(), request));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        var errors = error.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiResponse.FieldError(field.getField(), "INVALID", field.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(new ApiResponse<>("VALIDATION_FAILED", "请检查输入内容", null, ApiResponse.requestId(request), errors));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Void>> invalidInput(Exception error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_FAILED", "请求格式不正确", request));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception error, HttpServletRequest request) {
        LOG.error("requestId={} exceptionType={}", ApiResponse.requestId(request), error.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(ApiResponse.error("INTERNAL_ERROR", "服务暂时不可用，请保留请求编号", request));
    }
}
