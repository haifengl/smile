/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 */
package smile.studio;

import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StatusBar}.
 */
class StatusBarTest {

    @Test
    void initialStatusIsReady() {
        StatusBar statusBar = new StatusBar();
        ResourceBundle bundle = ResourceBundle.getBundle(StatusBar.class.getName(), Locale.getDefault());
        assertEquals(bundle.getString("Ready"), statusBar.getStatus());
    }

    @Test
    void setStatusUpdatesStatusMessage() {
        StatusBar statusBar = new StatusBar();
        statusBar.setStatus("Test status message");
        assertEquals("Test status message", statusBar.getStatus());
    }

    @Test
    void setStatusClearsStatusMessage() {
        StatusBar statusBar = new StatusBar();
        statusBar.setStatus("Something");
        assertEquals("Something", statusBar.getStatus());
        statusBar.setStatus("");
        assertEquals("", statusBar.getStatus());
    }
}
