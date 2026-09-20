/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.TextPart;

/**
 * Phase 2 media materialization unit tests (no GenAI natives).
 *
 * @author Haifeng Li
 */
public class GenAiMediaMaterializerTest {

    @Test
    public void materializesDataUrlToTempFile() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        Message[] in = {
                new Message(Role.user, new TextPart("see"), new ImageUrlPart(dataUrl))
        };
        Message[] out = GenAiMediaMaterializer.materialize(in);
        assertEquals(1, out.length);
        assertInstanceOf(ImageUrlPart.class, out[0].parts().get(1));
        String path = ((ImageUrlPart) out[0].parts().get(1)).url();
        assertTrue(Files.isRegularFile(Path.of(path)));
        assertArrayEquals(png, Files.readAllBytes(Path.of(path)));
    }

    @Test
    public void leavesLocalPathUnchanged() throws Exception {
        Path tmp = Files.createTempFile("smile-img-", ".png");
        tmp.toFile().deleteOnExit();
        Message[] in = {
                new Message(Role.user, new ImageUrlPart(tmp.toString()))
        };
        Message[] out = GenAiMediaMaterializer.materialize(in);
        assertEquals(tmp.toString(), ((ImageUrlPart) out[0].parts().getFirst()).url());
    }
}
