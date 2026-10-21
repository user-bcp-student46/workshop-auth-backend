package com.bcp.workshop.auth;

import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.keys.HmacKey;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.lang.JoseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class AuthTokenService {
    public static final int TOKEN_TTL_SECONDS = 3600;

    private static final String TOKEN_TYPE = "JWT";
    private final HmacKey signingKey;
    private final Clock clock;

    @Inject
    public AuthTokenService(AuthTokenConfiguration configuration) {
        this(configuration.secret(), Clock.systemUTC());
    }

    public AuthTokenService(String secret, Clock clock) {
        if (secret == null) {
            throw new IllegalArgumentException("Token secret is required");
        }
        this.signingKey = new HmacKey(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public String issue(String subject, String role, String client) {
        long issuedAt = clock.instant().getEpochSecond();
        long expiresAt = Math.addExact(issuedAt, TOKEN_TTL_SECONDS);

        JwtClaims claims = new JwtClaims();
        claims.setSubject(subject);
        claims.setClaim("client", client);
        claims.setIssuedAt(NumericDate.fromSeconds(issuedAt));
        claims.setExpirationTime(NumericDate.fromSeconds(expiresAt));
        claims.setClaim("role", role);

        JsonWebSignature signature = new JsonWebSignature();
        signature.setPayload(claims.toJson());
        signature.setAlgorithmHeaderValue(AlgorithmIdentifiers.HMAC_SHA256);
        signature.setHeader("typ", TOKEN_TYPE);
        signature.setKey(signingKey);

        try {
            return signature.getCompactSerialization();
        } catch (JoseException failure) {
            throw new IllegalStateException("Unable to create signed token");
        }
    }

    public VerificationResult verify(String compactToken) {
        String[] parts = compactToken == null ? new String[0] : compactToken.split("\\.", -1);
        if (parts.length != 3 || !isCanonicalBase64Url(parts[0])
                || !isCanonicalBase64Url(parts[1]) || !isCanonicalBase64Url(parts[2])) {
            return new RejectedToken(RejectionReason.FORMAT);
        }

        JsonWebSignature signature = new JsonWebSignature();
        try {
            signature.setCompactSerialization(compactToken);
        } catch (JoseException failure) {
            return new RejectedToken(RejectionReason.FORMAT);
        }

        if (!AlgorithmIdentifiers.HMAC_SHA256.equals(signature.getAlgorithmHeaderValue())
                || !TOKEN_TYPE.equals(signature.getHeader("typ"))) {
            return new RejectedToken(RejectionReason.FORMAT);
        }

        signature.setKey(signingKey);
        try {
            if (!signature.verifySignature()) {
                return new RejectedToken(RejectionReason.SIGNATURE);
            }
        } catch (JoseException failure) {
            return new RejectedToken(RejectionReason.SIGNATURE);
        }

        String payload;
        try {
            payload = signature.getPayload();
        } catch (JoseException failure) {
            return new RejectedToken(RejectionReason.FORMAT);
        }

        JwtClaims claims;
        try {
            claims = JwtClaims.parse(payload);
        } catch (InvalidJwtException failure) {
            return new RejectedToken(RejectionReason.FORMAT);
        }

        TokenClaims tokenClaims = readClaims(claims.getClaimsMap());
        if (tokenClaims == null) {
            return new RejectedToken(RejectionReason.CLAIMS);
        }

        long expectedExpiry;
        try {
            expectedExpiry = Math.addExact(tokenClaims.issuedAt(), TOKEN_TTL_SECONDS);
        } catch (ArithmeticException failure) {
            return new RejectedToken(RejectionReason.CLAIMS);
        }
        if (tokenClaims.expiresAt() != expectedExpiry) {
            return new RejectedToken(RejectionReason.CLAIMS);
        }
        if (clock.instant().getEpochSecond() >= tokenClaims.expiresAt()) {
            return new RejectedToken(RejectionReason.EXPIRED);
        }

        return new VerifiedToken(tokenClaims);
    }

    private static TokenClaims readClaims(Map<String, Object> values) {
        Object subject = values.get("sub");
        Object client = values.get("client");
        Object role = values.get("role");
        Long issuedAt = readIntegerClaim(values.get("iat"));
        Long expiresAt = readIntegerClaim(values.get("exp"));

        if (!(subject instanceof String subjectValue) || subjectValue.isBlank()
                || !(client instanceof String clientValue) || clientValue.isBlank()
                || !(role instanceof String roleValue) || roleValue.isBlank()
                || issuedAt == null || expiresAt == null) {
            return null;
        }

        return new TokenClaims(subjectValue, clientValue, issuedAt, expiresAt, roleValue);
    }

    private static Long readIntegerClaim(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            return null;
        }
    }

    private static boolean isCanonicalBase64Url(String value) {
        if (value.isEmpty()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value);
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    public enum RejectionReason {
        FORMAT,
        SIGNATURE,
        CLAIMS,
        EXPIRED
    }

    public sealed interface VerificationResult permits VerifiedToken, RejectedToken {}

    public record TokenClaims(String subject, String client, long issuedAt, long expiresAt, String role) {}

    public record VerifiedToken(TokenClaims claims) implements VerificationResult {}

    public record RejectedToken(RejectionReason reason) implements VerificationResult {}
}