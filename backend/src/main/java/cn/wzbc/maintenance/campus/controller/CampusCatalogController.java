package cn.wzbc.maintenance.campus.controller;

import cn.wzbc.maintenance.campus.service.CampusCatalogService;
import cn.wzbc.maintenance.campus.vo.CatalogVo;
import cn.wzbc.maintenance.common.ApiResponse;
import cn.wzbc.maintenance.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/campus")
public class CampusCatalogController {
    private final CampusCatalogService service;
    public CampusCatalogController(CampusCatalogService service) { this.service = service; }
    @GetMapping("/catalog") public ApiResponse<CatalogVo> catalog(@AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
        return ApiResponse.success(service.catalog(user), request);
    }
}
