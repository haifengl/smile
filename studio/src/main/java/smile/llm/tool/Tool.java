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
     * Returns the tool specifications for file operations.
     * @return the tool specifications for file operations.
     */
    static List<Spec> file() {
        return List.of(Read.spec(), Write.spec(), Edit.spec(), Append.spec(),
                ApplyPatch.spec(), Glob.spec(), Grep.spec());
    }

    /**
     * Returns the tool specifications for shell operations.
     * @return the tool specifications for shell operations.
     */
    static List<Spec> shell() {
        return List.of(Bash.spec(), KillShell.spec());
    }

    /**
     * Returns the tool specifications for planning operations.
     * @return the tool specifications for planning operations.
     */
    static List<Spec> planning() {
        return List.of(LoadSkill.spec(), SlashCommand.spec(), ExitPlanMode.spec());
    }

    /**
     * Returns the tool specifications for web operations.
     * @return the tool specifications for web operations.
     */
    static List<Spec> web() {
        return List.of(WebFetch.spec(), WebSearch.spec());
    }
}
