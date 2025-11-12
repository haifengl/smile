package smile.studio.view;

import java.awt.Font;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.util.FontUtils;

/**
 * The monospace font for view components.
 */
public class Monospace {
    /**
     * The shared font for consistency.
     */
    public static Font font = FontUtils.getCompositeFont(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 14);
}
