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
package smile.studio.notebook;

/**
 * The type of notebook cells.
 *
 * @author Haifeng Li
 */
public enum CellType {
    /**
     * Source code.
     */
    Code("code"),
    /**
     * Narrative text or documentation in Markdown format.
     */
    Markdown("markdown"),
    /**
     * Raw text is not evaluated or rendered by the kernel.
     */
    Raw("raw");

    /** The cell type identifier. */
    private final String value;

    /**
     * Constructor.
     * @param value the cell type identifier.
     */
    CellType(String value) {
        this.value = value;
    }

    /**
     * Returns the cell type identifier.
     * @return the cell type identifier.
     */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
