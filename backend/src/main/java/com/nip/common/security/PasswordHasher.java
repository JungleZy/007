package com.nip.common.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Arrays;
import java.util.HexFormat;

/** Password hashing and gradual migration from the historical MD5 format. */
@ApplicationScoped
public class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2_sha256";
    private static final int VERSION = 1;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 32;
    private static final int MIN_POLICY_ITERATIONS = 600_000;
    private static final int MAX_ITERATIONS = 2_000_000;
    private final SecureRandom random;
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    private final int iterations;

    @Inject
    public PasswordHasher(@ConfigProperty(name = "nip.security.password.iterations", defaultValue = "600000") int iterations) {
        if (iterations < MIN_POLICY_ITERATIONS || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("Password iteration policy must be between 600000 and 2000000");
        }
        this.iterations = iterations;
        this.random = new SecureRandom();
    }

    public String hash(String plaintext) {
        requirePassword(plaintext);
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        byte[] derived = derive(plaintext, salt, iterations);
        return PREFIX + "$" + VERSION + "$" + iterations + "$" + BASE64.encodeToString(salt) + "$"
                + BASE64.encodeToString(derived);
    }

    public Verification verify(String plaintext, String stored) {
        if (plaintext == null || plaintext.isEmpty() || stored == null || stored.isEmpty() || stored.length() > 128) {
            return new Verification(false, false);
        }
        if (isLegacyHex(stored)) {
            byte[] expected = decodeHex(stored);
            byte[] actual;
            try {
                actual = MessageDigest.getInstance("MD5").digest(plaintext.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("MD5 is unavailable", impossible);
            }
            boolean matches = MessageDigest.isEqual(actual, expected);
            return new Verification(matches, matches);
        }
        String[] fields = stored.split("\\$", -1);
        if (fields.length != 5 || !PREFIX.equals(fields[0]) || !"1".equals(fields[1])
                || fields[2].length() > 7 || !digits(fields[2]) || fields[3].length() != 22 || fields[4].length() != 43) {
            return new Verification(false, false);
        }
        final int storedIterations;
        try {
            storedIterations = Integer.parseInt(fields[2]);
        } catch (NumberFormatException e) {
            return new Verification(false, false);
        }
        if (storedIterations < 1 || storedIterations > MAX_ITERATIONS) {
            return new Verification(false, false);
        }
        byte[] salt = decodeBase64(fields[3]);
        byte[] expected = decodeBase64(fields[4]);
        if (salt == null || expected == null || salt.length != SALT_LENGTH || expected.length != KEY_LENGTH) {
            return new Verification(false, false);
        }
        byte[] actual = derive(plaintext, salt, storedIterations);
        boolean matches = MessageDigest.isEqual(actual, expected);
        return new Verification(matches, matches && storedIterations < iterations);
    }

    public record Verification(boolean matches, boolean needsUpgrade) {
    }

    private static byte[] derive(String plaintext, byte[] salt, int rounds) {
        char[] password = plaintext.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, KEY_LENGTH * 8);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to derive password hash", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(password, '\0');
        }
    }

    private static void requirePassword(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("Password must not be null or empty");
        }
    }

    private static boolean isLegacyHex(String value) {
        if (value.length() != 32) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    private static byte[] decodeHex(String value) {
        return HexFormat.of().parseHex(value);
    }

    private static boolean digits(String value) {
        if (value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        return true;
    }


    private static byte[] decodeBase64(String value) {
        try {
            if (value.isEmpty() || value.indexOf('=') >= 0) return null;
            return BASE64_DECODER.decode(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
