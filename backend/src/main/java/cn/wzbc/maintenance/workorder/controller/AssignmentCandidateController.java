package cn.wzbc.maintenance.workorder.controller;

import cn.wzbc.maintenance.common.ApiResponse;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.workorder.service.AssignmentRecommendationService;
import cn.wzbc.maintenance.workorder.vo.AssignmentCandidateVo.AssignmentCandidatesVo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static cn.wzbc.maintenance.common.OperationResultService.*;

@RestController
public class AssignmentCandidateController {
    private final AssignmentRecommendationService service;
    public AssignmentCandidateController(AssignmentRecommendationService service) { this.service=service; }
    @GetMapping("/api/work-orders/assignment-candidates")
    public ApiResponse<AssignmentCandidatesVo> candidates(@AuthenticationPrincipal CurrentUser actor,@RequestParam(required=false) String context,
            @RequestParam(required=false) String requestId,@RequestParam(required=false) String workOrderId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,HttpServletRequest request) {
        if(request.getParameterMap().entrySet().stream().anyMatch(p->!Set.of("context","requestId","workOrderId","page","pageSize","keyword").contains(p.getKey()) || p.getValue().length!=1))throw error(400,"INVALID_QUERY","查询字段不合法");
        return ApiResponse.success(service.candidates(actor,context,requestId,workOrderId,page,pageSize,keyword),request);
    }
}
