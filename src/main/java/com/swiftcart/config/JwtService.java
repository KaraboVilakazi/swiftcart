package com.swiftcart.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Stateless JWT utility.
 *
 * Tokens contain the user's email (subject) and role as a claim.
 * The secret is base64-encoded in config so it can be set via
 * environment variable without quoting issues.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties props;

    // ------------------------------------------------------------------ //
    // Token generation                                                     //
    // ------------------------------------------------------------------ //

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        // Embed roles so downstream services can authorise without a DB lookup
        extraClaims.put("roles", userDetails.getAuthorities()
                .stream()
                .map(a -> a.getAuthority())
                .toList());

        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + props.getExpirationMs()))
                .signWith(signingKey())
                .compact();
    }

    // ------------------------------------------------------------------ //
    // Token validation                                                     //
    // ------------------------------------------------------------------ //

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // ------------------------------------------------------------------ //
    // Internal helpers                                                     //
    // ------------------------------------------------------------------ //

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private SecretKey signingKey() {
        byte[] keyBytes = Base64.getDecoder().decode(props.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
