/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.tool;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import smile.llm.Conversation;

/**
 * Tools enable models to interact with external systems,
 * such as querying databases, calling APIs, or performing
 * computations.
 *
 * @author Haifeng Li
 */
public interface Tool {
    /**
     * Executes the tool.
     * @param conversation the conversation session that the tool calling belongs to.
     * @return the result of tool execution.
     */
    String run(Conversation conversation, Consumer<String> statusUpdate);

    /**
     * The specification of built-in tools. 
     *
     * @param clazz the class of tool.
     * @param methods the methods of tool.
     * @author Haifeng Li
     */
    record Spec(Class<? extends Tool> clazz, List<Method> methods) { }

    /**
     * Returns the specifications of basic tools for file operations, shell, skill, planning.
     * @return the specifications of basic tools for file operations, shell, skill, planning.
     */
    static List<Spec> basics() {
        return List.of(Read.spec(), Write.spec(), Edit.spec(), Append.spec(),
                Glob.spec(), Grep.spec(), Bash.spec(), KillShell.spec(),
                LoadSkill.spec(), SlashCommand.spec(), ExitPlanMode.spec());
    }

    /**
     * Returns the specifications of web tools.
     * @return the specifications of web tools.
     */
    static List<Spec> web() {
        return List.of(WebFetch.spec(), WebSearch.spec());
    }

    /** Returns the current working directory of the conversation. */
    static Path cwd(Conversation conversation) {
        return conversation.path().resolve("../..")
                    .normalize().toAbsolutePath();
    }
}
