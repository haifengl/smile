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
 * SPI for plugins that record tamper-evident security audit trails for multimedia
 * attachment access, uploads, deletions, quota/rate blocks, scanner rejections,
 * and integrity failures.
 *
 * <p>Implementations are discovered via CDI ({@code @All List<BlobAuditListener>})
 * and must be non-blocking without throwing exceptions into callers.
 *
 * @author Haifeng Li
 */
public interface BlobAuditListener {
    /**
     * Invoked when an attachment security or lifecycle event occurs.
     *
     * @param event metadata-only attachment audit event.
     */
    void onBlobEvent(BlobAuditEvent event);
}
