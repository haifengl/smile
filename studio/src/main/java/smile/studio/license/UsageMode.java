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
package smile.studio.license;

/**
 * Mutually exclusive SMILE Agent Studio usage modes.
 *
 * @author Haifeng Li
 */
public enum UsageMode {
    /** First run: user has not yet attested to a usage mode and no key is pre-deployed. */
    UNSET,
    /** Option 1: Personal, Academic, or Non-Production Evaluation under BSL 1.1. */
    EVALUATION,
    /** Option 2 (Trial): 30-day Commercial Production Trial while procurement is in flight. */
    COMMERCIAL_TRIAL,
    /** Option 2 (Activated): Commercial Production Use backed by a verified Ed25519 key. */
    COMMERCIAL_LICENSED
}
