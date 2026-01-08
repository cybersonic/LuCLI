package org.lucee.lucli.secrets;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction for secret storage backends.
 * Implementations must ensure values are stored encrypted at rest
 * and never expose secrets via toString/logging.
 */
public interface SecretStore {

    record SecretMetadata(String name, String description, long createdAt, long updatedAt) {}

    void put(String name, char[] value, String description) throws SecretStoreException;

    Optional<char[]> get(String name) throws SecretStoreException;

    void delete(String name) throws SecretStoreException;

    List<SecretMetadata> list() throws SecretStoreException;
}
