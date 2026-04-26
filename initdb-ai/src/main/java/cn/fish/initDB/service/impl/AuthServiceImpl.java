package cn.fish.initDB.service.impl;

import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.initDB.entity.*;
import cn.fish.initDB.repository.CaptchaRepository;
import cn.fish.initDB.service.AuthService;
import cn.fish.initDB.service.CaptchaService;
import cn.fish.initDB.service.TokenService;
import cn.fish.initDB.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final TokenService tokenService;
    private final CaptchaService captchaService;
    private final PasswordEncoder passwordEncoder;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    public AuthServiceImpl(UserService userService, TokenService tokenService,
                           CaptchaService captchaService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.captchaService = captchaService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        SysUser user = userService.findByUsername(request.getUsername())
                .orElseThrow(() -> new CommonException("用户名或密码错误"));

        if (user.getStatus() == 0) {
            throw new CommonException("账号已被禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CommonException("用户名或密码错误");
        }

        userService.updateLastLoginTime(user.getId());

        return tokenService.generateTokenResponse(
                user.getId(), user.getUsername(), user.getNickname(), user.getAvatar()
        );
    }

    @Override
    @Transactional
    public LoginResponse loginByCaptcha(CaptchaLoginRequest request) {
        boolean verified = captchaService.verifyCaptcha(request.getTarget(), request.getType(), request.getCaptcha());
        if (!verified) {
            throw new CommonException("验证码错误或已过期");
        }

        SysUser user;
        if (isEmail(request.getTarget())) {
            user = userService.findByEmail(request.getTarget()).orElseGet(() -> {
                SysUser newUser = SysUser.builder()
                        .email(request.getTarget())
                        .username("user_" + System.currentTimeMillis())
                        .nickname("新用户")
                        .status(1)
                        .build();
                return userService.save(newUser);
            });
        } else if (isPhone(request.getTarget())) {
            user = userService.findByPhone(request.getTarget()).orElseGet(() -> {
                SysUser newUser = SysUser.builder()
                        .phone(request.getTarget())
                        .username("user_" + System.currentTimeMillis())
                        .nickname("新用户")
                        .status(1)
                        .build();
                return userService.save(newUser);
            });
        } else {
            throw new CommonException("请输入正确的手机号或邮箱");
        }

        if (user.getStatus() == 0) {
            throw new CommonException("账号已被禁用");
        }

        userService.updateLastLoginTime(user.getId());

        return tokenService.generateTokenResponse(
                user.getId(), user.getUsername(), user.getNickname(), user.getAvatar()
        );
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        if (!tokenService.validateToken(refreshToken)) {
            throw new CommonException("刷新令牌无效或已过期");
        }

        Long userId = tokenService.getUserIdFromToken(refreshToken);
        SysUser user = userService.findById(userId)
                .orElseThrow(() -> new CommonException("用户不存在"));

        if (user.getStatus() == 0) {
            throw new CommonException("账号已被禁用");
        }

        return tokenService.generateTokenResponse(
                user.getId(), user.getUsername(), user.getNickname(), user.getAvatar()
        );
    }

    @Override
    public void logout(String accessToken) {
        tokenService.invalidateToken(accessToken);
    }

    private boolean isEmail(String target) {
        return EMAIL_PATTERN.matcher(target).matches();
    }

    private boolean isPhone(String target) {
        return PHONE_PATTERN.matcher(target).matches();
    }
}
