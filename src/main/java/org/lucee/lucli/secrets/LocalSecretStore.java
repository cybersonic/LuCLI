package org.lucee.lucli.secrets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Local encrypted secret store backed by a single JSON file under LuCLI home.
 *
 * Layout (JSON):
 * {
 *   "salt": "base64...",
 *   "secrets": {
 *      "name": {
 *        "nonce": "base64...",
 *        "ciphertext": "base64...",
 *        "description": "...",
 *        "createdAt": 123,
 *        "updatedAt": 456
 *      }
 *   }
 * }
 */
public class LocalSecretStore implements SecretStore {

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BITS = 256;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int PBKDF2_ITERATIONS = 120_000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Path storeFile;
    private final char[] passphrase;
    private final SecureRandom secureRandom = new SecureRandom();

    private byte[] salt;
    private SecretKeySpec key;
    private Map<String, Entry> secrets = new HashMap<>();

    private static class Entry {
        public String nonce;
        public String ciphertext;
        public String description;
        public long createdAt;
        public long updatedAt;
    }

    private static class StoreLayout {
        public String salt;
        public Map<String, Entry> secrets = new HashMap<>();
    }

    public LocalSecretStore(Path storeFile, char[] passphrase) throws SecretStoreException {
        this.storeFile = storeFile;
        this.passphrase = passphrase != null ? passphrase.clone() : new char[0];
        initialize();
    }

    private void initialize() throws SecretStoreException {
        try {
            if (Files.exists(storeFile)) {
                loadExistingStore();
            } else {
                createNewStore();
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new SecretStoreException("Failed to initialize local secret store", e);
        }
    }

    private void createNewStore() throws IOException, GeneralSecurityException {
        this.salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(this.salt);
        this.key = deriveKey(passphrase, salt);
        this.secrets = new HashMap<>();
        persist();
        applySecurePermissions();
    }

    private void loadExistingStore() throws IOException, GeneralSecurityException {
        byte[] bytes = Files.readAllBytes(storeFile);
        if (bytes.length == 0) {
            throw new IOException("Secret store file is empty: " + storeFile);
        }
        StoreLayout layout = OBJECT_MAPPER.readValue(bytes, StoreLayout.class);
        if (layout.salt == null || layout.salt.isEmpty()) {
            throw new IOException("Secret store is missing salt");
        }
        this.salt = Base64.getDecoder().decode(layout.salt);
        this.key = deriveKey(passphrase, salt);
        this.secrets = layout.secrets != null ? layout.secrets : new HashMap<>();
    }

    private void persist() throws IOException {
        StoreLayout layout = new StoreLayout();
        layout.salt = Base64.getEncoder().encodeToString(salt);
        layout.secrets = secrets;
        byte[] json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(layout);
        Files.createDirectories(storeFile.getParent());
        Files.write(storeFile, json);
    }

    private void applySecurePermissions() {
        try {
            // Only attempt POSIX permissions on supported file systems
            var perms = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(storeFile, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Best-effort; on non-POSIX (e.g. Windows) we just skip this.
        }
    }

    private SecretKeySpec deriveKey(char[] passphrase, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } finally {
            spec.clearPassword();
        }
    }

    @Override
    public synchronized void put(String name, char[] value, String description) throws SecretStoreException {
        long now = System.currentTimeMillis();
        try {
            byte[] plaintext = new String(value).getBytes(StandardCharsets.UTF_8);
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] ciphertext = cipher.doFinal(plaintext);

            Entry entry = secrets.getOrDefault(name, new Entry());
            if (entry.createdAt == 0L) {
                entry.createdAt = now;
            }
            entry.updatedAt = now;
            entry.description = description;
            entry.nonce = Base64.getEncoder().encodeToString(nonce);
            entry.ciphertext = Base64.getEncoder().encodeToString(ciphertext);

            secrets.put(name, entry);
            persist();
        } catch (GeneralSecurityException | IOException e) {
            throw new SecretStoreException("Failed to store secret", e);
        }
    }

    @Override
    public synchronized Optional<char[]> get(String name) throws SecretStoreException {
        Entry entry = secrets.get(name);
        if (entry == null) {
            return Optional.empty();
        }
        try {
            byte[] nonce = Base64.getDecoder().decode(entry.nonce);
            byte[] ciphertext = Base64.getDecoder().decode(entry.ciphertext);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] plaintext = cipher.doFinal(ciphertext);
            char[] chars = new String(plaintext, StandardCharsets.UTF_8).toCharArray();
            return Optional.of(chars);
        } catch (GeneralSecurityException e) {
            throw new SecretStoreException("Failed to decrypt secret; passphrase may be incorrect", e);
        }
    }

    @Override
    public synchronized void delete(String name) throws SecretStoreException {
        if (secrets.remove(name) != null) {
            try {
                persist();
            } catch (IOException e) {
                throw new SecretStoreException("Failed to persist secret removal", e);
            }
        }
    }

    @Override
    public synchronized List<SecretMetadata> list() throws SecretStoreException {
        List<SecretMetadata> result = new ArrayList<>();
        for (Map.Entry<String, Entry> e : secrets.entrySet()) {
            Entry entry = e.getValue();
            result.add(new SecretMetadata(e.getKey(), entry.description, entry.createdAt, entry.updatedAt));
        }
        return result;
    }
}
