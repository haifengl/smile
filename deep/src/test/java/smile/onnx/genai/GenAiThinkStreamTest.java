/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.onnx.genai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import smile.llm.GenerationListener;

/**
 * Phase 3 thinking-span stream attribution (no natives).
 *
 * @author Haifeng Li
 */
public class GenAiThinkStreamTest {

    @Test
    public void stripsThinkAndEmitsVisible() {
        List<String> visible = new ArrayList<>();
        GenerationListener listener = new GenerationListener() {
            @Override
            public void onText(String chunk) {
                visible.add(chunk);
            }
        };

        GenAiChatModel.ThinkStream stream = new GenAiChatModel.ThinkStream();
        int thinking = 0;
        int generated = 0;
        if (stream.feed("<think>", listener)) {
            thinking++;
        } else {
            generated++;
        }
        if (stream.feed("reason", listener)) {
            thinking++;
        } else {
            generated++;
        }
        if (stream.feed("</think>", listener)) {
            thinking++;
        } else {
            generated++;
        }
        if (stream.feed("answer", listener)) {
            thinking++;
        } else {
            generated++;
        }
        stream.flush(listener);

        assertEquals("answer", String.join("", visible));
        assertTrue(thinking >= 1);
        assertTrue(generated >= 1);
    }
}