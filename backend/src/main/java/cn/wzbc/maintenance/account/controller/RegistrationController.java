package cn.wzbc.maintenance.account.controller;

import cn.wzbc.maintenance.account.dto.RegisterDto;
import cn.wzbc.maintenance.account.service.RegistrationService;
import cn.wzbc.maintenance.account.vo.RegistrationVo;
import cn.wzbc.maintenance.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {
    private final RegistrationService service;
    public RegistrationController(RegistrationService service) { this.service=service; }
    @PostMapping("/register") public ResponseEntity<ApiResponse<RegistrationVo>> register(@Valid @RequestBody RegisterDto dto,HttpServletRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(service.register(dto),request));
    }
}
