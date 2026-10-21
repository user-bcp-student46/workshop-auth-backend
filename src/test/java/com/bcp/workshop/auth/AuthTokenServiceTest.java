package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.keys.HmacKey;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AuthTokenServiceTest {
    private static final String SECRET = "synthetic-test-secret-with-at-least-32-bytes";
    private static final Instant ISSUED_AT = Instant.parse("2026-10-21T12:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void issuesSignedJwtWithRequiredClaimsAndOneHourLifetime() throws Exception {
        AuthTokenService service = serviceAt(ISSUED_AT);
        String token = service.issue("student", "STUDENT", "angular-workshop");

        JsonWebSignature parsed = parse(token);
        assertEquals(AlgorithmIdentifiers.HMAC_SHA256, parsed.getAlgorithmHeaderValue());
        assertEquals("JWT", parsed.getHeader("typ"));
        assertFalse(parsed.getPayload().contains(SECRET));
        assertFalse(parsed.getPayload().toLowerCase().contains("password"));

        AuthTokenService.VerifiedToken verified = assertInstanceOf(
                AuthTokenService.VerifiedToken.class,
                service.verify(token));
        AuthTokenService.TokenClaims claims = verified.claims();
        assertEquals("student", claims.subject());
        assertEquals("angular-workshop", claims.client());
        assertEquals("STUDENT", claims.role());
        assertEquals(ISSUED_AT.getEpochSecond(), claims.issuedAt());
        assertEquals(3600, claims.expiresAt() - claims.issuedAt());
    }

    @Test
    void rejectsTokensSignedWithAnotherSecret() {
        String token = serviceAt(ISSUED_AT).issue("student", "STUDENT", "angular-workshop");

        assertRejected(serviceWithSecret("another-synthetic-test-secret-with-at-least-32-bytes", ISSUED_AT).verify(token),
                AuthTokenService.RejectionReason.SIGNATURE);
    }

    @Test
    void rejectsAlteredPayloadAndSignature() throws Exception {
        String token = serviceAt(ISSUED_AT).issue("student", "STUDENT", "angular-workshop");
        String[] parts = token.split("\\.");
        String alteredPayload = encodeUrl("{\"sub\":\"trainer\",\"client\":\"angular-workshop\",\"iat\":"
                + ISSUED_AT.getEpochSecond() + ",\"exp\":" + (ISSUED_AT.getEpochSecond() + 3600)
                + ",\"role\":\"TRAINER\"}");
        assertRejected(serviceAt(ISSUED_AT).verify(parts[0] + "." + alteredPayload + "." + parts[2]),
                AuthTokenService.RejectionReason.SIGNATURE);

        char firstCharacter = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        String alteredSignature = firstCharacter + parts[2].substring(1);
        assertRejected(serviceAt(ISSUED_AT).verify(parts[0] + "." + parts[1] + "." + alteredSignature),
                AuthTokenService.RejectionReason.SIGNATURE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub", "client", "iat", "exp", "role"})
    void rejectsEachMissingRequiredClaim(String missingClaim) throws Exception {
        Map<String, Object> claims = validClaims();
        claims.remove(missingClaim);

        assertRejected(serviceAt(ISSUED_AT).verify(signClaims(claims)), AuthTokenService.RejectionReason.CLAIMS);
    }

    @ParameterizedTest
    @MethodSource("invalidClaimTypes")
    void rejectsInvalidClaimTypes(String claimName, Object invalidValue) throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put(claimName, invalidValue);

        assertRejected(serviceAt(ISSUED_AT).verify(signClaims(claims)), AuthTokenService.RejectionReason.CLAIMS);
    }

    private static Stream<Arguments> invalidClaimTypes() {
        return Stream.of(
                arguments("sub", 17),
                arguments("client", true),
                arguments("iat", "now"),
                arguments("exp", "later"),
                arguments("role", 1)
        );
    }

    @Test
    void acceptsUntilExpiryAndRejectsAtAndAfterExpiry() throws Exception {
        String token = signClaims(validClaims());

        assertInstanceOf(AuthTokenService.VerifiedToken.class,
                serviceAt(ISSUED_AT.plusSeconds(3599)).verify(token));
        assertRejected(serviceAt(ISSUED_AT.plusSeconds(3600)).verify(token), AuthTokenService.RejectionReason.EXPIRED);
        assertRejected(serviceAt(ISSUED_AT.plusSeconds(3601)).verify(token), AuthTokenService.RejectionReason.EXPIRED);
    }

    @Test
    void rejectsLegacyMalformedUnsupportedAndNonCanonicalTokens() throws Exception {
        AuthTokenService service = serviceAt(ISSUED_AT);
        String legacyToken = LegacyTokenUtil.create("student", "STUDENT");
        assertRejected(service.verify(legacyToken), AuthTokenService.RejectionReason.FORMAT);
        assertRejected(service.verify("not-a-jwt"), AuthTokenService.RejectionReason.FORMAT);

        String unsigned = encodeUrl("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "."
            + encodeUrl(JSON.writeValueAsString(validClaims())) + "." + encodeUrl("signature");
        assertRejected(service.verify(unsigned), AuthTokenService.RejectionReason.FORMAT);

        String unsupportedAlgorithm = encodeUrl("{\"alg\":\"HS384\",\"typ\":\"JWT\"}") + "."
            + encodeUrl(JSON.writeValueAsString(validClaims())) + "." + encodeUrl("signature");
        assertRejected(service.verify(unsupportedAlgorithm), AuthTokenService.RejectionReason.FORMAT);

        String validToken = service.issue("student", "STUDENT", "angular-workshop");
        String[] parts = validToken.split("\\.");
        assertRejected(service.verify(parts[0] + "=." + parts[1] + "." + parts[2]),
                AuthTokenService.RejectionReason.FORMAT);
    }

    private static AuthTokenService serviceAt(Instant instant) {
        return serviceWithSecret(SECRET, instant);
    }

    private static AuthTokenService serviceWithSecret(String secret, Instant instant) {
        return new AuthTokenService(secret, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static Map<String, Object> validClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "student");
        claims.put("client", "angular-workshop");
        claims.put("iat", ISSUED_AT.getEpochSecond());
        claims.put("exp", ISSUED_AT.getEpochSecond() + 3600);
        claims.put("role", "STUDENT");
        return claims;
    }

    private static String signClaims(Map<String, Object> claims) throws Exception {
        return signClaims(claims, AlgorithmIdentifiers.HMAC_SHA256);
    }

    private static String signClaims(Map<String, Object> claims, String algorithm) throws Exception {
        JsonWebSignature signature = new JsonWebSignature();
        signature.setPayload(JSON.writeValueAsString(claims));
        signature.setAlgorithmHeaderValue(algorithm);
        signature.setHeader("typ", "JWT");
        signature.setKey(new HmacKey(SECRET.getBytes(StandardCharsets.UTF_8)));
        return signature.getCompactSerialization();
    }

    private static JsonWebSignature parse(String token) throws JoseException {
        JsonWebSignature signature = new JsonWebSignature();
        signature.setCompactSerialization(token);
        signature.setKey(new HmacKey(SECRET.getBytes(StandardCharsets.UTF_8)));
        return signature;
    }

    private static String encodeUrl(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertRejected(AuthTokenService.VerificationResult result,
                                       AuthTokenService.RejectionReason reason) {
        AuthTokenService.RejectedToken rejected = assertInstanceOf(AuthTokenService.RejectedToken.class, result);
        assertEquals(reason, rejected.reason());
    }
}