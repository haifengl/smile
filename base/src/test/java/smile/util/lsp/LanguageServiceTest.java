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
package smile.util.lsp;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for {@link LanguageService}.
 *
 * @author Haifeng Li
 */
public class LanguageServiceTest {

    @AfterEach
    public void tearDown() {
        LanguageService.closeAll();
    }

    @Test
    public void testRegistryAndCloseAll() {
        // Given
        var python = new LanguageService(List.of("ty", "server"), Path.of("."));
        var java = new LanguageService(List.of("jdtls"), Path.of("."));
        LanguageService.put("python", python);
        LanguageService.put("java", java);

        // When
        assertSame(python, LanguageService.get("python"));
        assertSame(java, LanguageService.get("java"));
        LanguageService.closeAll();
        // Second call must be an idempotent no-op.
        LanguageService.closeAll();

        // Then
        assertNull(LanguageService.get("python"));
        assertNull(LanguageService.get("java"));
    }
}
