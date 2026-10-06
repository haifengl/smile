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
package smile.studio.plugin;

/**
 * What happened to one component of a plugin during translation. Every component
 * the plugin declares produces exactly one disposition, so the UI can show the
 * user an honest account of what will and will not work — nothing is dropped
 * silently (ADR-005).
 *
 * @param kind the component kind, e.g. {@code agents}, {@code skills},
 *             {@code commands}, {@code mcpServers}, {@code hooks}.
 * @param name the component's local name within the plugin.
 * @param disposition what the translator did with it.
 * @param reason a short human-readable explanation, shown in the Errors/Warnings tab.
 *
 * @author Haifeng Li
 */
public record ComponentDisposition(String kind, String name, Disposition disposition, String reason) {

    /** The outcome of translating one component. */
    public enum Disposition {
        /** Copied as-is; the Claude shape already matches the ioa shape. */
        KEPT,
        /** Reshaped into an ioa-native form. */
        CONVERTED,
        /** No ioa equivalent; not installed. */
        DROPPED,
        /** Installed but limited, with the limitation stated in the reason. */
        DEGRADED
    }

    /**
     * A component copied unchanged.
     * @param kind the component kind.
     * @param name the component name.
     * @param reason why it was kept.
     * @return the disposition.
     */
    public static ComponentDisposition kept(String kind, String name, String reason) {
        return new ComponentDisposition(kind, name, Disposition.KEPT, reason);
    }

    /**
     * A component reshaped for ioa.
     * @param kind the component kind.
     * @param name the component name.
     * @param reason how it was converted.
     * @return the disposition.
     */
    public static ComponentDisposition converted(String kind, String name, String reason) {
        return new ComponentDisposition(kind, name, Disposition.CONVERTED, reason);
    }

    /**
     * A component with no ioa equivalent.
     * @param kind the component kind.
     * @param name the component name.
     * @param reason why it was dropped.
     * @return the disposition.
     */
    public static ComponentDisposition dropped(String kind, String name, String reason) {
        return new ComponentDisposition(kind, name, Disposition.DROPPED, reason);
    }

    /**
     * A component installed with a limitation.
     * @param kind the component kind.
     * @param name the component name.
     * @param reason the limitation.
     * @return the disposition.
     */
    public static ComponentDisposition degraded(String kind, String name, String reason) {
        return new ComponentDisposition(kind, name, Disposition.DEGRADED, reason);
    }

    /** @return true when the component has no ioa equivalent. */
    public boolean isDropped() {
        return disposition == Disposition.DROPPED;
    }

    /** @return true when the component is installed with a limitation. */
    public boolean isDegraded() {
        return disposition == Disposition.DEGRADED;
    }
}
