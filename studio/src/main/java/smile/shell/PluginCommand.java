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
package smile.shell;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import smile.studio.plugin.PluginService;

/**
 * Manages plugins and marketplaces from the command line, mirroring
 * {@code claude plugin}. The work is delegated to {@link PluginService}, the same
 * facade the Studio panel uses, so the two never diverge.
 *
 * <pre>
 * smile plugin marketplace add owner/repo
 * smile plugin install commit-commands@claude-plugins-official --scope project
 * smile plugin list
 * smile plugin mcp my-plugin@my-marketplace db on
 * </pre>
 *
 * @author Haifeng Li
 */
@Command(name = "smile plugin", versionProvider = VersionProvider.class,
        description = "Manage plugins and marketplaces.",
        mixinStandardHelpOptions = true)
public class PluginCommand implements Callable<Integer> {
    /** The plugin subcommand and its arguments. */
    @Parameters(arity = "0..*", paramLabel = "<command> [args]",
            description = "The plugin subcommand, e.g. 'list', 'install', 'marketplace add'.")
    private String[] args;

    /** The install scope for install/enable/disable. */
    @Option(names = {"--scope"}, paramLabel = "<scope>",
            description = "Install scope: user (default), project, or local.")
    private String scope;

    @Override
    public Integer call() {
        List<String> all = new ArrayList<>();
        if (args != null) {
            all.addAll(List.of(args));
        }
        if (scope != null) {
            all.add("--scope");
            all.add(scope);
        }

        var service = new PluginService(Path.of(System.getProperty("user.dir")));
        String output = service.execute(all);
        System.out.println(output);
        return output.startsWith("Error") ? 1 : 0;
    }
}
