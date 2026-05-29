package ai.jarvis.security.jwt;

import ai.jarvis.config.JarvisProperties;
import ai.jarvis.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final JarvisProperties properties;

    public JwtService(JarvisProperties properties) {
        this.properties = properties;
        this.secretKey = buildSecretKey(
                properties.security().jwt().secret()
        );
        log.info("JwtService initialized successfully");
    }

    /**
     * Builds a SecretKey that is always >= 256 bits.
     * Uses SHA-256 to derive a 32-byte (256-bit) key
     * from the configured secret, regardless of its length.
     */
    private SecretKey buildSecretKey(String secret) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(
                    secret.getBytes(StandardCharsets.UTF_8)
            );
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java
            throw new IllegalStateException(
                    "SHA-256 algorithm not available", e
            );
        }
    }

    // ── Generate Access Token ─────────────────────

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(
                properties.security().jwt().accessTokenExpiry()
        );

        return Jwts.builder()
                .subject(user.id().toString())
                .claim("username", user.username())
                .claim("role", user.role().name())
                .claim("type", "access")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .issuer(properties.security().jwt().issuer())
                .signWith(secretKey)
                .compact();
    }

    // ── Generate Refresh Token ────────────────────

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(
                properties.security().jwt().refreshTokenExpiry()
        );

        return Jwts.builder()
                .subject(user.id().toString())
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .issuer(properties.security().jwt().issuer())
                .signWith(secretKey)
                .compact();
    }

    // ── Validate Token ────────────────────────────

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    // ── Extract Claims ────────────────────────────

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return getClaims(token).getSubject();
    }

    public String extractUsername(String token) {
        return getClaims(token)
                .get("username", String.class);
    }

    public String extractRole(String token) {
        return getClaims(token)
                .get("role", String.class);
    }

    public String extractTokenType(String token) {
        return getClaims(token)
                .get("type", String.class);
    }

    public boolean isTokenExpired(String token) {
        try {
            return getClaims(token)
                    .getExpiration()
                    .before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }

    public long getAccessTokenExpirySeconds() {
        return properties.security().jwt()
                .accessTokenExpiry().getSeconds();
    }
}