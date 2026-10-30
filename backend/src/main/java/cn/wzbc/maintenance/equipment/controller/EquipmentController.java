package cn.wzbc.maintenance.equipment.controller;
import cn.wzbc.maintenance.equipment.dto.*;
import cn.wzbc.maintenance.equipment.vo.EquipmentVo;
import cn.wzbc.maintenance.equipment.service.*;
import cn.wzbc.maintenance.workorder.vo.WorkOrderDetailVo.SummaryVo;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static cn.wzbc.maintenance.common.OperationResultService.*;
@RestController @RequestMapping("/api/equipment")
public class EquipmentController {
    private final EquipmentService service; private final EquipmentQueryService query;
    public EquipmentController(EquipmentService service,EquipmentQueryService query) { this.service=service; this.query=query; }
    @GetMapping public ApiResponse<PageResult<EquipmentVo>> list(@AuthenticationPrincipal CurrentUser actor,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) String buildingId,@RequestParam(required=false) String floorId,@RequestParam(required=false) String locationId,@RequestParam(required=false) Boolean enabled,@RequestParam(required=false) String facilityType,HttpServletRequest request) { return ApiResponse.success(service.list(actor,page,pageSize,keyword,buildingId,floorId,locationId,enabled,facilityType),request); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<OperationResultService.Receipt> create(@AuthenticationPrincipal CurrentUser actor,@Valid @RequestBody EquipmentSaveDto dto,HttpServletRequest request) { return ApiResponse.success(service.create(actor,dto),request); }
    @GetMapping("/{id}") public ApiResponse<EquipmentVo.DetailVo> detail(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,HttpServletRequest request) { return ApiResponse.success(query.currentAndHistory(id(id),new EquipmentQueryService.EquipmentHistoryQuery(page,pageSize,keyword,status),actor),request); }
    @GetMapping("/{id}/work-orders") public ApiResponse<PageResult<SummaryVo>> history(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,HttpServletRequest request) { return ApiResponse.success(query.currentAndHistory(id(id),new EquipmentQueryService.EquipmentHistoryQuery(page,pageSize,keyword,status),actor).history(),request); }
    @PatchMapping("/{id}") public ApiResponse<OperationResultService.Receipt> update(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@Valid @RequestBody EquipmentSaveDto dto,HttpServletRequest request) { return ApiResponse.success(service.update(actor,id(id),dto),request); }
    @PostMapping("/{id}/enable") public ApiResponse<OperationResultService.Receipt> enable(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@Valid @RequestBody EquipmentStatusDto dto,HttpServletRequest request) { return ApiResponse.success(service.status(actor,id(id),dto,true),request); }
    @PostMapping("/{id}/disable") public ApiResponse<OperationResultService.Receipt> disable(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@Valid @RequestBody EquipmentStatusDto dto,HttpServletRequest request) { return ApiResponse.success(service.status(actor,id(id),dto,false),request); }
}
