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
package smile.studio.workspace;

/**
 * The Agent Hub service node shown under the Kernel Explorer's Services node.
 *
 * @param host       the bind or target host.
 * @param port       the HTTP port.
 * @param adminToken the admin bearer token for {@code /v1/admin/*} endpoints.
 *
 * @author Haifeng Li
 */
public record HubService(String host, int port, String adminToken) {
}
