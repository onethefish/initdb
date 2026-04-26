package cn.fish.initDB.service.impl;

import cn.fish.initDB.entity.LoginResponse;
import cn.fish.initDB.service.TokenService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
public class TokenServiceImpl implements TokenService {

    private final StringRedisTemplate redisTemplate;

    @Value("${security.jwt.secret:initdb-ai-jwt-secret-key-must-be-at-least-256-bits-long}")
    private String jwtSecret;

    @Value("${security.jwt.access-token-expiration:7200}")
    private long accessTokenExpiration;

    @Value("${security.jwt.refresh-token-expiration:604800}")
    private long refreshTokenExpiration;

    @Value("${security.jwt.issuer:initdb-ai}")
    private String issuer;

    private static final String TOKEN_PREFIX = "token:";

    public TokenServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateAccessToken(Long userId, String username) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "access")
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration * 1000))
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public String generateRefreshToken(Long userId, String username) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "refresh")
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration * 1000))
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public LoginResponse generateTokenResponse(Long userId, String username, String nickname, String avatar) {
        String accessToken = generateAccessToken(userId, username);
        String refreshToken = generateRefreshToken(userId, username);

        storeToken(accessToken, userId);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration)
                .userId(userId)
                .username(username)
                .nickname(nickname)
                .avatar(avatar)
                .build();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return isTokenStored(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    @Override
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("username", String.class);
    }

    @Override
    public void invalidateToken(String token) {
        String key = TOKEN_PREFIX + token;
        redisTemplate.delete(key);
    }

    @Override
    public void storeToken(String accessToken, Long userId) {
        String key = TOKEN_PREFIX + accessToken;
        redisTemplate.opsForValue().set(key, String.valueOf(userId), accessTokenExpiration, TimeUnit.SECONDS);
    }

    @Override
    public boolean isTokenStored(String accessToken) {
        String key = TOKEN_PREFIX + accessToken;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
