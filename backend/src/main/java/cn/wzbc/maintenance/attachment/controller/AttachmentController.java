package cn.wzbc.maintenance.attachment.controller;
import cn.wzbc.maintenance.attachment.service.AttachmentService;
import cn.wzbc.maintenance.attachment.vo.AttachmentVo;
import cn.wzbc.maintenance.common.ApiResponse;
import cn.wzbc.maintenance.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
public class AttachmentController {
    private final AttachmentService service;
    public AttachmentController(AttachmentService service) { this.service=service; }
    @PostMapping(value="/api/attachments/temp",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AttachmentVo>> upload(@AuthenticationPrincipal CurrentUser actor,@RequestParam Map<String,String> fields,@RequestPart("file") MultipartFile file,HttpServletRequest request) throws IOException {
        if(!Set.of("operationId","purpose","batchId","repairRequestId","workOrderId","round").containsAll(fields.keySet()) || request.getParameterMap().values().stream().anyMatch(v -> v.length!=1)) throw AttachmentService.error(400,"VALIDATION_FAILED","不接受未定义或重复的上传字段");
        if(file.getSize()>5_000_000) throw AttachmentService.error(413,"ATTACHMENT_TOO_LARGE","单张图片不能超过5,000,000字节");
        Integer round=null; try { if(fields.containsKey("round")) round=Integer.valueOf(fields.get("round")); } catch(NumberFormatException failure) { throw AttachmentService.error(400,"VALIDATION_FAILED","轮次不合法"); }
        var input=new AttachmentService.Upload(fields.get("operationId"),fields.get("purpose"),fields.get("batchId"),optionalId(fields.get("repairRequestId")),optionalId(fields.get("workOrderId")),round);
        return ResponseEntity.status(201).body(ApiResponse.success(service.upload(actor,input,file.getOriginalFilename(),file.getContentType(),file.getBytes()),request));
    }
    @GetMapping("/api/attachments/{id}/content") public ResponseEntity<byte[]> content(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id) {
        var file=service.content(actor,id(id)); return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType())).header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").body(file.bytes());
    }
    @DeleteMapping("/api/attachments/{id}/temp") public ApiResponse<Void> cancel(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@RequestHeader("X-Operation-Id") String operation,HttpServletRequest request) { service.cancel(actor,id(id),operation); return ApiResponse.success(null,request); }
    private static Long optionalId(String v) { return v==null?null:id(v); }
    public static long id(String v) { try { if(v==null || !v.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException(); return Long.parseLong(v); } catch(NumberFormatException failure) { throw AttachmentService.error(400,"VALIDATION_FAILED","ID不合法"); } }
}
