package com.estapar.garage.shared.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** SHA-256 → lowercase hex. Pure and stateless; the idempotency fingerprint (ADR-004). */
@Component
public class FingerprintHasher {

    public String hash(String canonicalForm) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalForm.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS to be present on every JVM — unreachable.
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
