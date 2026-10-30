package cn.wzbc.maintenance.account.dto;

import cn.wzbc.maintenance.common.BusinessException;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;

public record RegisterDto(@NotBlank String role, @NotBlank String loginName, @NotBlank String name,
        @NotBlank String phone, @NotBlank String password, @NotBlank String confirmPassword) {
    public void validate() {
        if (!java.util.Set.of("REPORTER","TECHNICIAN").contains(role)
                || !loginName.matches("[\\p{IsHan}A-Za-z0-9_]+") || !length(loginName,2,30)
                || !length(name.trim(),1,100) || !phone.matches("1[3-9][0-9]{9}")
                || !length(password,8,32) || !password.matches("(?s)(?=.*[A-Za-z])(?=.*[0-9]).*")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","请检查角色、账号、姓名、手机号和密码格式");
        }
        if (!password.equals(confirmPassword)) throw new BusinessException(HttpStatus.BAD_REQUEST,"PASSWORD_CONFIRM_MISMATCH","两次密码输入不一致");
    }
    private static boolean length(String value,int min,int max) { int n=value.codePointCount(0,value.length()); return n>=min && n<=max; }
}
