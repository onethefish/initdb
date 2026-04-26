package cn.fish.initDB.service;

import cn.fish.initDB.entity.LoginResponse;

public interface TokenService {

    String generateAccessToken(Long userId, String username);

    String generateRefreshToken(Long userId, String username);

    LoginResponse generateTokenResponse(Long userId, String username, String nickname, String avatar);

    boolean validateToken(String token);

    Long getUserIdFromToken(String token);

    String getUsernameFromToken(String token);

    void invalidateToken(String token);

    void storeToken(String accessToken, Long userId);

    boolean isTokenStored(String accessToken);
}
