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
package smile.llm.model.minimax;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in check against a real checkpoint's {@code model.safetensors.index.json} (no weights are
 * read). Set {@code MINIMAX_CHECKPOINT_DIR} to a directory holding the index and {@code config.json}.
 *
 * @author Haifeng Li
 */
public class MiniMaxRealIndexTest {

    @Test
    public void testGivenRealIndexWhenRemappedThenEveryExpectedWeightIsProvided() throws IOException {
        String dir = System.getenv("MINIMAX_CHECKPOINT_DIR");
        assumeTrue(dir != null && new File(dir, "model.safetensors.index.json").exists(),
                "MINIMAX_CHECKPOINT_DIR not set");
        MiniMaxModelArgs args = MiniMaxModelArgs.fromHuggingFace(Path.of(dir, "config.json").toString(), 1, 4096);
        Set<String> provided = new HashSet<>();
        var weightMap = new ObjectMapper().readTree(new File(dir, "model.safetensors.index.json")).get("weight_map");
        weightMap.properties().forEach(e -> {
            String mapped = MiniMaxWeightNames.remap(e.getKey());
            if (mapped != null) provided.add(mapped);
        });
        Set<String> missing = new java.util.TreeSet<>(MiniMaxWeightNames.expectedNames(args));
        missing.removeAll(provided);
        assertTrue(missing.isEmpty(), missing.size() + " missing, e.g. " + missing.stream().limit(5).toList());
        // Everything the remapper accepts must also be something the model knows about
        // (scale sidecars excluded).
        Set<String> unknown = new java.util.TreeSet<>(provided);
        unknown.removeAll(MiniMaxWeightNames.expectedNames(args));
        unknown.removeIf(n -> n.endsWith(".weight_scale_inv"));
        assertTrue(unknown.isEmpty(), "unexpected tensors, e.g. " + unknown.stream().limit(5).toList());
    }
}
