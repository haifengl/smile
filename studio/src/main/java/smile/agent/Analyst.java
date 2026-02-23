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
package smile.agent;

import java.nio.file.Path;
import java.util.function.Supplier;
import smile.llm.client.LLM;

/**
 * The data analyst agent.
 *
 * @author Haifeng Li
 */
public class Analyst extends Agent {
    /**
     * Constructor.
     * @param llm the supplier of LLM service.
     * @param path the directory path for agent context.
     */
    public Analyst(Supplier<LLM> llm, Path path) {
        super(llm, new Context(path),
              new Context(System.getProperty("user.home") + "/.smile/agents/data-analyst"),
              new Context(System.getProperty("smile.home") + "/agents/data-analyst"));
    }
}
