/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.chat.blob;

/**
 * Factory for a {@link BlobStore} backend. Providers are CDI beans: the
 * built-in {@code local} provider lives in {@code serve}; optional backends
 * (e.g. {@code s3}) ship in plugin jars that are added to the classpath at
 * build time. The backend is selected at runtime by {@code smile.blob.storage}.
 *
 * @author Haifeng Li
 */
public interface BlobStoreProvider {
    /**
     * @return backend name matched (case-insensitively) against
     *         {@code smile.blob.storage}.
     */
    String name();

    /**
     * Creates the store from the provider's own configuration.
     *
     * @return a new store; if it implements {@link AutoCloseable} it is closed
     *         when the application shuts down.
     */
    BlobStore create();
}
