package cn.fish.initDB.service;

import cn.fish.initDB.entity.*;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse loginByCaptcha(CaptchaLoginRequest request);

    LoginResponse refreshToken(String refreshToken);

    void logout(String accessToken);
}
