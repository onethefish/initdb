package cn.fish.initDB.repository;

import cn.fish.initDB.entity.SysUserOauth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserOauthRepository extends JpaRepository<SysUserOauth, Long> {

    Optional<SysUserOauth> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<SysUserOauth> findByUserIdAndProvider(Long userId, String provider);

    void deleteByUserIdAndProvider(Long userId, String provider);
}
