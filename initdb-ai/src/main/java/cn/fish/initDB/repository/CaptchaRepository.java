package cn.fish.initDB.repository;

import cn.fish.initDB.entity.SysCaptcha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CaptchaRepository extends JpaRepository<SysCaptcha, Long> {

    Optional<SysCaptcha> findByTargetAndTypeAndStatusAndExpireTimeAfter(
            String target, String type, Integer status, LocalDateTime now);

    @Modifying
    @Query("UPDATE SysCaptcha c SET c.status = 1 WHERE c.target = :target AND c.type = :type AND c.captcha = :captcha")
    int markUsed(String target, String type, String captcha);

    @Modifying
    @Query("DELETE FROM SysCaptcha c WHERE c.expireTime < :now")
    int deleteExpired(LocalDateTime now);
}
