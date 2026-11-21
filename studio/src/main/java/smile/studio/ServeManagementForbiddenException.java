/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio;

import java.io.IOException;

/**
 * Raised when SMILE Serve rejects a model-management request because it did not
 * originate from localhost.
 *
 * <p>The management endpoints ({@code POST /v1/models/load},
 * {@code /v1/smile/{id}/reload}, {@code /v1/smile/{id}/unload}) are annotated {@code @LocalhostOnly}
 * and answer {@code 403 Forbidden} to every non-loopback caller. Studio can
 * therefore attach to a remote engine to inspect it, but cannot load, reload, or
 * unload models in it. This exception lets the UI say so explicitly instead of
 * reporting a generic transport failure.
 *
 * @author Haifeng Li
 */
public class ServeManagementForbiddenException extends IOException {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     *
     * @param message the detail message.
     */
    public ServeManagementForbiddenException(String message) {
        super(message);
    }
}
