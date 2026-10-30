package cn.wzbc.maintenance.account.service;

import cn.wzbc.maintenance.account.dto.RegisterDto;
import cn.wzbc.maintenance.account.entity.*;
import cn.wzbc.maintenance.account.mapper.*;
import cn.wzbc.maintenance.account.vo.RegistrationVo;
import cn.wzbc.maintenance.common.BusinessException;
import cn.wzbc.maintenance.security.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final UserMapper users;
    private final RegistrationApplicationMapper reviews;
    private final PasswordSupport passwords;
    private final Clock clock;
    public RegistrationService(UserMapper users,RegistrationApplicationMapper reviews,PasswordSupport passwords,Clock clock) {
        this.users=users; this.reviews=reviews; this.passwords=passwords; this.clock=clock;
    }
    @Transactional
    public RegistrationVo register(RegisterDto dto) {
        dto.validate();
        if (users.byLoginName(dto.loginName())!=null) throw duplicate("LOGIN_NAME_EXISTS");
        if (users.selectCount(new QueryWrapper<UserEntity>().eq("phone",dto.phone()))>0) throw duplicate("PHONE_EXISTS");
        var now=clock.instant().truncatedTo(ChronoUnit.MILLIS);
        var user=new UserEntity(); user.id=IdWorker.getId(); user.createdAt=now; user.updatedAt=now;
        user.loginName=dto.loginName(); user.name=dto.name().trim(); user.phone=dto.phone(); user.passwordHash=passwords.hash(dto.password());
        user.role=CurrentUser.Role.valueOf(dto.role()); user.enabled=true;
        if (user.role==CurrentUser.Role.TECHNICIAN) { user.reviewStatus=CurrentUser.ReviewStatus.PENDING; user.reviewAttempt=1; }
        try { users.insert(user); }
        catch (DuplicateKeyException error) { throw duplicate(error.getMostSpecificCause().getMessage().contains("uk_user_phone") ? "PHONE_EXISTS" : "LOGIN_NAME_EXISTS"); }
        if (user.role==CurrentUser.Role.TECHNICIAN) {
            var review=new RegistrationApplicationEntity(); review.id=IdWorker.getId(); review.createdAt=now; review.userId=user.id;
            review.attempt=1; review.nameSnapshot=user.name; review.phoneSnapshot=user.phone; review.submittedAt=now; review.status=CurrentUser.ReviewStatus.PENDING;
            reviews.insert(review);
        }
        return new RegistrationVo(user.id.toString(),user.loginName,user.role,user.reviewStatus);
    }
    private static BusinessException duplicate(String code) { return new BusinessException(HttpStatus.CONFLICT,code,code.equals("PHONE_EXISTS") ? "手机号已被占用" : "登录账号已被占用"); }
}
