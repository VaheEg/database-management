package org.example.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guarantees the stored external-database passwords rely on.
 */
class CryptoServiceTest {

    private static final String SECRET = "unit-test-crypto-secret-at-least-32-chars";

    private final CryptoService cryptoService = serviceWithSecret(SECRET);

    @Test
    void encryptThenDecryptReturnsTheOriginal() {
        String plaintext = "p@ssw0rd for the external db";

        String encrypted = cryptoService.encrypt(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(cryptoService.decrypt(encrypted)).isEqualTo(plaintext);
    }

    /** A fresh IV per value: the same password must not produce the same ciphertext twice. */
    @Test
    void encryptingTheSameValueTwiceGivesDifferentCiphertext() {
        String first = cryptoService.encrypt("same-password");
        String second = cryptoService.encrypt("same-password");

        assertThat(first).isNotEqualTo(second);
        assertThat(cryptoService.decrypt(first)).isEqualTo(cryptoService.decrypt(second));
    }

    @Test
    void aTamperedCiphertextIsRejectedRatherThanDecryptedToGarbage() {
        String encrypted = cryptoService.encrypt("do-not-touch");
        String tampered = encrypted.substring(0, encrypted.length() - 3)
                + (encrypted.endsWith("AAA") ? "BBB" : "AAA");

        assertThatThrownBy(() -> cryptoService.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherSecretCannotReadWhatThisOneWrote() {
        String encrypted = cryptoService.encrypt("owned-by-the-first-key");

        CryptoService other = serviceWithSecret("a-different-crypto-secret-of-32+-chars");

        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTooShortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> serviceWithSecret("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }

    private static CryptoService serviceWithSecret(String secret) {
        CryptoProperties properties = new CryptoProperties();
        properties.setSecret(secret);
        return new CryptoService(properties);
    }
}
