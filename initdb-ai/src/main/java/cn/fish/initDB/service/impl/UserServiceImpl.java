package cn.fish.initDB.service.impl;

import cn.fish.initDB.entity.SysUser;
import cn.fish.initDB.repository.UserRepository;
import cn.fish.initDB.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<SysUser> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public Optional<SysUser> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public Optional<SysUser> findByPhone(String phone) {
        return userRepository.findByPhone(phone);
    }

    @Override
    public Optional<SysUser> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    @Transactional
    public SysUser save(SysUser user) {
        return userRepository.save(user);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByPhone(String phone) {
        return userRepository.existsByPhone(phone);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public void updateLastLoginTime(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLoginTime(LocalDateTime.now());
            userRepository.save(user);
        });
    }
}
