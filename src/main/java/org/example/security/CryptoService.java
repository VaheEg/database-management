package org.example.security;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Symmetric encryption for the one secret this application stores on a user's
 * behalf: the password of an external database.
 *
 * <p>Those passwords cannot be hashed like a login password — the backend has to
 * present the original value to the other database — so they are encrypted with
 * AES-GCM instead. GCM is authenticated, so a tampered-with ciphertext fails to
 * decrypt rather than yielding garbage. Each value gets a fresh random IV, which
 * is prepended to the ciphertext and stored with it; the whole thing is Base64 so
 * it fits in an ordinary varchar column.
 */
@Service
public class CryptoService {

    private static final int MIN_SECRET_LENGTH = 32;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(CryptoProperties properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.crypto.secret must be set and at least " + MIN_SECRET_LENGTH + " characters long");
        }
        this.key = deriveKey(secret);
    }

    /** @return Base64 of {@code iv || ciphertext}, safe to persist */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt value", ex);
        }
    }

    /**
     * Reverses {@link #encrypt(String)}.
     *
     * @throws IllegalStateException if the value was not produced by this service, or
     *                               was encrypted under a different {@code app.crypto.secret}
     */
    public String decrypt(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalStateException("Stored value is too short to be a valid ciphertext");
            }
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, combined, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] plaintext = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Failed to decrypt stored value — was it encrypted with a different app.crypto.secret?", ex);
        }
    }

    /** SHA-256 turns a human-typed secret of any length into the 256 bits AES wants. */
    private static SecretKey deriveKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
