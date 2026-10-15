package com.company.erp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates access tokens. Claims embed userId, role and
 * branchId so the JwtAuthFilter can build a UserPrincipal per-request
 * without a database round trip for authorization decisions (a DB lookup
 * is still done for freshness where it matters, e.g. active/disabled
 * checks - see JwtAuthFilter).
 */
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long accessTokenMinutes;

    public JwtTokenProvider(
            @Value("${erp.jwt.secret}") String secret,
            @Value("${erp.jwt.access-token-minutes}") long accessTokenMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenMinutes, ChronoUnit.MINUTES);

        var builder = Jwts.builder()
                .subject(principal.getUserId().toString())
                .claim("email", principal.getUsername())
                .claim("role", principal.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));

        if (principal.getBranchId() != null) {
            builder.claim("branchId", principal.getBranchId().toString());
        }

        return builder.signWith(signingKey).compact();
    }

    public Claims parseAndValidate(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }
}
