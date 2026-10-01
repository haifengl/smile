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
package smile.studio.cli;

/**
 * The type of Intents.
 *
 * @author Haifeng Li
 */
public enum IntentType {
    /**
     * Raw, unformatted text that is not evaluated by the analyst.
     */
    Raw("Raw", ""),
    /**
     * Commands initiated by typing a forward slash (/), that allow
     * users to quickly execute actions.
     */
    Command("Slash Command", "/"),
    /**
     * Shell commands.
     */
    Shell("Shell", "!"),
    /**
     * Text in Markdown format, providing explanations,
     * documentation, or narrative content.
     */
    Markdown("Markdown", "#"),
    /**
     * Instructions in natural language for LLM agents to execute.
     */
    Instructions("Instructions", ">");

    /** The description. */
    private final String description;
    /** The legend. */
    private final String legend;

    /**
     * Constructor.
     * @param description the description.
     * @param legend the legend.
     */
    IntentType(String description, String legend) {
        this.description = description;
        this.legend = legend;
    }

    /**
     * Returns the legend.
     * @return the legend.
     */
    public String legend() {
        return legend;
    }

    @Override
    public String toString() {
        return description;
    }
}
