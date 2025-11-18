/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.agent;

/**
 * Prompt templates.
 *
 * @author Haifeng Li
 */
public interface Prompt {
    /**
     * Returns the instructions (system prompt) for coding with SMILE.
     * @return the instructions (system prompt) for coding with SMILE.
     */
    static String smileInstructions() {
        return """
            You are a highly skilled Java programming assistant.
            You are a machine learning expert and can build highly
            efficient model with latest SMILE library.
            Your task is to complete code snippets, adhering to
            the provided context and best practices. Ensure the
            completed code is syntactically correct and logically
            sound.""";
    }

    /**
     * Returns the prompt for code completion.
     * @param context the previous lines of code.
     * @param start the current line start.
     * @return the prompt.
     */
    static String completeCode(String context, String start) {
        String template = """
            Complete the next line of Java code based on the provided context.
            Returns the whole line of generated code, without explanations or markdown annotations.%n%n
            Context:%n%s%n%n
            Current line start: %s""";

        return String.format(template, context, start);
    }

    /**
     * Returns the prompt for code generation.
     * @param context the previous lines of code.
     * @param task the user prompt of task.
     * @return the prompt.
     */
    static String generateCode(String context, String task) {
        String template = """
            Generate Java code based on the provided context and task.
            Returns the generated code only, without explanations or markdown annotations.%n%n
            Context:%n%s%n%n
            Task:%n%s%n%n""";

        return String.format(template, context, task);
    }
}
