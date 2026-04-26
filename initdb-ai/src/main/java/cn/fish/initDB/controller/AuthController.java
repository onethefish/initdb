package cn.fish.initDB.controller;

import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.initDB.entity.*;
import cn.fish.initDB.service.AuthService;
import cn.fish.initDB.service.CaptchaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, CaptchaService captchaService) {
        this.authService = authService;
        this.captchaService = captchaService;
    }

    @PostMapping("/login")
    public ResponseResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseResult.success(response);
    }

    @PostMapping("/login/captcha")
    public ResponseResult<LoginResponse> loginByCaptcha(@Valid @RequestBody CaptchaLoginRequest request) {
        LoginResponse response = authService.loginByCaptcha(request);
        return ResponseResult.success(response);
    }

    @PostMapping("/captcha/send")
    public ResponseResult<Void> sendCaptcha(@Valid @RequestBody SendCaptchaRequest request) {
        captchaService.sendCaptcha(request.getTarget(), request.getType());
        return ResponseResult.success(null, "验证码发送成功");
    }

    @PostMapping("/token/refresh")
    public ResponseResult<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refreshToken(request.getRefreshToken());
        return ResponseResult.success(response);
    }

    @PostMapping("/logout")
    public ResponseResult<Void> logout(@RequestHeader("Authorization") String authorization) {
        String token = authorization.replace("Bearer ", "");
        authService.logout(token);
        return ResponseResult.success(null, "退出成功");
    }
}
