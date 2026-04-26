package cn.fish.initDB.service;

public interface CaptchaService {

    void sendCaptcha(String target, String type);

    boolean verifyCaptcha(String target, String type, String captcha);

    void cleanExpiredCaptcha();
}
