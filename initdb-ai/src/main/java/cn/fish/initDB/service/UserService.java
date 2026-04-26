package cn.fish.initDB.service;

import cn.fish.initDB.entity.SysUser;

import java.util.Optional;

public interface UserService {

    Optional<SysUser> findById(Long id);

    Optional<SysUser> findByUsername(String username);

    Optional<SysUser> findByPhone(String phone);

    Optional<SysUser> findByEmail(String email);

    SysUser save(SysUser user);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    void updateLastLoginTime(Long userId);
}
