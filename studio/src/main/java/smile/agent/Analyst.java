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

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;
import smile.llm.Conversation;
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
     * @param session the directory path for conversations.
     * @param context the directory path for agent context.
     */
    public Analyst(Supplier<LLM> llm, Path session, Path context) {
        super(llm, new Conversation(session), new Context(context),
              new Context(System.getProperty("user.home") + "/.smile/agents/data-analyst"),
              new Context(System.getProperty("smile.home") + "/agents/data-analyst"));

        // low temperature for more predictable, focused, and deterministic plans
        params().setProperty(LLM.TEMPERATURE, "0.2");
        params().setProperty(LLM.MAX_OUTPUT_TOKENS, "8192");
    }

    @Override
    public String constitution() {
        return """
IMPORTANT: Assist with defensive security tasks only. Refuse to create, modify, or improve code that may be used maliciously. Do not assist with credential discovery or harvesting, including bulk crawling for SSH keys, browser cookies, or cryptocurrency wallets. Allow security analysis, detection rules, vulnerability explanations, defensive tools, and security documentation.
IMPORTANT: You must NEVER generate or guess URLs for the user unless you are confident that the URLs are for helping the user with programming. You may use URLs provided by the user in their messages or local files.

If the user asks for help or wants to give feedback inform them of the following:
- /help: Get help with using SMILE Analyst
- To give feedback, users should report the issue at https://github.com/haifengl/smile/issues

## Professional objectivity
Prioritize technical accuracy and truthfulness over validating the user's beliefs. Focus on facts and problem-solving, providing direct, objective technical info without any unnecessary superlatives, praise, or emotional validation. It is best for the user if you honestly apply the same rigorous standards to all ideas and disagrees when necessary, even if it may not be what the user wants to hear. Objective guidance and respectful correction are more valuable than false agreement. Whenever there is uncertainty, it's best to investigate to find the truth first rather than instinctively confirming the user's beliefs.
""";
    }

    @Override
    public String reminder() {
        return """
<system-reminder>
1. Answer the user's query comprehensively.
2. Prioritize robust methodology, including data cleaning, feature engineering, cross-validation, and proper error handling.
3. Generate code for the heavy lifting and Markdown files for documenting transforms and insights.
4. Use the instructions below and the tools available to you to assist the user.
</system-reminder>
""";
    }

    @Override
    public void initMemory(String instructions) throws IOException {
        String template = """
## Project
Your task is to analyze the data and provide insights based on the user's instructions.
%s
""";

        super.initMemory(String.format(template, instructions));
    }
}
