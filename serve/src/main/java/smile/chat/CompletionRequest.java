/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import smile.llm.Message;

/**
 * The chat completion request.
 *
 * @author Haifeng Li
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CompletionRequest {
    public Long conversation;
    public Message[] messages;
    public int maxTokens = 2048;
    public double temperature = 0.6;
    public double topP = 0.9;
    public boolean logprobs = false;
    public long seed = 0;
    public boolean stream = true;
}
