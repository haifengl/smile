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
package smile.studio.kernel;

/**
 * Navigation behavior post running a cell.
 *
 * @author Haifeng Li
 */
public enum PostRunNavigation {
    /** Stays the focus on the cell. */
    STAY,
    /** Moves the focus to the next cell. Creates a new cell below if it doesn't exist. */
    NEXT_OR_NEW,
    /** Inserts a new cell below. */
    INSERT_BELOW
}
