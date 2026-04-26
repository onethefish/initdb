package cn.fish.initDB.service.impl;

import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.initDB.entity.SysCaptcha;
import cn.fish.initDB.repository.CaptchaRepository;
import cn.fish.initDB.service.CaptchaService;
import cn.hutool.core.util.RandomUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CaptchaServiceImpl implements CaptchaService {

    private final CaptchaRepository captchaRepository;

    @Value("${security.captcha.expiration:300}")
    private int captchaExpiration;

    @Value("${security.captcha.length:6}")
    private int captchaLength;

    public CaptchaServiceImpl(CaptchaRepository captchaRepository) {
        this.captchaRepository = captchaRepository;
    }

    @Override
    @Transactional
    public void sendCaptcha(String target, String type) {
        String captcha = RandomUtil.randomNumbers(captchaLength);

        SysCaptcha sysCaptcha = SysCaptcha.builder()
                .target(target)
                .captcha(captcha)
                .type(type)
                .status(0)
                .expireTime(LocalDateTime.now().plusSeconds(captchaExpiration))
                .build();

        captchaRepository.save(sysCaptcha);

        // TODO: 集成短信/邮件服务发送验证码
        // 开发环境打印到日志
        System.out.println("验证码发送成功 - 目标: " + target + ", 验证码: " + captcha + ", 类型: " + type);
    }

    @Override
    @Transactional
    public boolean verifyCaptcha(String target, String type, String captcha) {
        return captchaRepository
                .findByTargetAndTypeAndStatusAndExpireTimeAfter(target, type, 0, LocalDateTime.now())
                .filter(c -> c.getCaptcha().equals(captcha))
                .map(c -> {
                    captchaRepository.markUsed(target, type, captcha);
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public void cleanExpiredCaptcha() {
        captchaRepository.deleteExpired(LocalDateTime.now());
    }
}
