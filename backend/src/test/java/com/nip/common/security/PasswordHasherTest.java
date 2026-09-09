package com.nip.common.security;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher(600_000);

    @Test
    void hashesVerifyUnicodeAndUseDistinctSalts() {
        String first = hasher.hash("päss秘密");
        String second = hasher.hash("päss秘密");
        assertNotEquals(first, second);
        assertTrue(hasher.verify("päss秘密", first).matches());
        assertFalse(hasher.verify("wrong", first).matches());
    }

    @Test
    void verifiesIndependentPbkdf2VectorAndMarksWeakerHash() {
        // Python hashlib.pbkdf2_hmac('sha256', b'password', b'0123456789abcdef', 1, dklen=32).
        String encoded = "pbkdf2_sha256$1$1$MDEyMzQ1Njc4OWFiY2RlZg$751fat1KXRn0p_yStI8jUeqVu5d2QsAHHtTkAQpCy2w";
        PasswordHasher.Verification result = hasher.verify("password", encoded);
        assertTrue(result.matches());
        assertTrue(result.needsUpgrade());
    }

    @Test
    void verifiesLegacyMd5AndRequestsUpgradeOnlyForMatchingPassword() {
        String legacy = "5f4dcc3b5aa765d61d8327deb882cf99";
        PasswordHasher.Verification result = hasher.verify("password", legacy);
        assertTrue(result.matches());
        assertTrue(result.needsUpgrade());
        assertFalse(hasher.verify("wrong-password", legacy).matches());
        assertFalse(hasher.verify("wrong-password", legacy).needsUpgrade());
        assertFalse(hasher.verify("password", legacy.substring(0, 31)).matches());
    }

    @Test
    void rejectsCorruptAndOverlargeMetadata() {
        assertFalse(hasher.verify("password", "pbkdf2_sha256$1$2000001$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").matches());
        assertFalse(hasher.verify("password", "pbkdf2_sha256$1$not-a-number$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").matches());
        assertFalse(hasher.verify("password", "pbkdf2_sha256$2$600000$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").matches());
        assertFalse(hasher.verify("password", "pbkdf2_sha256$1$1$" + "A".repeat(2000) + "$AAAA").matches());
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(""));
    }

}
