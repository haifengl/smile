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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.swing;

import java.awt.Color;
import java.util.AbstractMap;
import java.util.Locale;
import java.util.Map;

/**
 * Color palette generator.
 *
 * @author Haifeng Li
 */
public class Palette {
    /** Private constructor. */
    private Palette() {

    }

    /**
     * Creates a color specified with an HTML or CSS attribute string.
     *
     * @param colorString the name or numeric representation of the color
     *                    in one of the supported formats.
     * @return the color.
     */
    public static Color web(String colorString) {
        return web(colorString, 1.0f);
    }

    /**
     * Creates a color specified with an HTML or CSS attribute string.
     *
     * @param colorString the name or numeric representation of the color
     *                    in one of the supported formats
     * @param opacity the opacity component in range from 0.0 (transparent)
     *                to 1.0 (opaque)
     * @return the color.
     */
    public static Color web(String colorString, float opacity) {
        if (colorString.isEmpty()) {
            throw new IllegalArgumentException("Invalid color specification");
        }

        if (opacity < 0.0f || opacity > 1.0f) {
            throw new IllegalArgumentException("Invalid opacity: " + opacity);
        }

        String color = colorString.toLowerCase(Locale.ROOT);

        if (color.startsWith("#")) {
            color = color.substring(1);
        } else if (color.startsWith("0x")) {
            color = color.substring(2);
        } else if (color.startsWith("rgb")) {
            if (color.startsWith("(", 3)) {
                return parseRGBColor(color, 4, false, opacity);
            } else if (color.startsWith("a(", 3)) {
                return parseRGBColor(color, 5, true, opacity);
            }
        } else if (color.startsWith("hsl")) {
            if (color.startsWith("(", 3)) {
                return parseHSLColor(color, 4, false, opacity);
            } else if (color.startsWith("a(", 3)) {
                return parseHSLColor(color, 5, true, opacity);
            }
        } else {
            Color namedColor = NAMED_COLORS.get(color);
            if (namedColor != null) {
                return namedColor;
            }
        }

        int len = color.length();

        try {
            if (len == 3 || len == 4) {
                int red = Integer.parseInt(color.substring(0, 1), 16);
                int green = Integer.parseInt(color.substring(1, 2), 16);
                int blue = Integer.parseInt(color.substring(2, 3), 16);
                float alpha = opacity;
                if (len == 4) {
                    alpha = opacity * Integer.parseInt(color.substring(3, 4), 16) / 15.0f;
                }
                return new Color(red / 15.0f, green / 15.0f, blue / 15.0f, alpha);
            } else if (len == 6 || len == 8) {
                int red = Integer.parseInt(color.substring(0, 2), 16);
                int green = Integer.parseInt(color.substring(2, 4), 16);
                int blue = Integer.parseInt(color.substring(4, 6), 16);
                float alpha = opacity;
                if (len == 8) {
                    alpha = opacity * Integer.parseInt(color.substring(6, 8), 16) / 255.0f;
                }
                return new Color(red / 255.0f, green / 255.0f, blue / 255.0f, alpha);
            }
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid color specification: " + colorString, nfe);
        }

        throw new IllegalArgumentException("Invalid color specification: " + colorString);
    }

    private static Color parseRGBColor(String color, int roff, boolean hasAlpha, float a) {
        try {
            int rend = color.indexOf(',', roff);
            int gend = rend < 0 ? -1 : color.indexOf(',', rend+1);
            int bend = gend < 0 ? -1 : color.indexOf(hasAlpha ? ',' : ')', gend+1);
            int aend = hasAlpha ? (bend < 0 ? -1 : color.indexOf(')', bend+1)) : bend;
            if (aend >= 0) {
                float r = parseComponent(color, roff, rend, PARSE_COMPONENT);
                float g = parseComponent(color, rend+1, gend, PARSE_COMPONENT);
                float b = parseComponent(color, gend+1, bend, PARSE_COMPONENT);
                if (hasAlpha) {
                    a *= parseComponent(color, bend+1, aend, PARSE_ALPHA);
                }
                return new Color(r, g, b, a);
            }
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid color specification: " + color, nfe);
        }

        throw new IllegalArgumentException("Invalid color specification: " + color);
    }

    private static Color parseHSLColor(String color, int hoff, boolean hasAlpha, float a) {
        try {
            int hend = color.indexOf(',', hoff);
            int send = hend < 0 ? -1 : color.indexOf(',', hend+1);
            int lend = send < 0 ? -1 : color.indexOf(hasAlpha ? ',' : ')', send+1);
            int aend = hasAlpha ? (lend < 0 ? -1 : color.indexOf(')', lend+1)) : lend;
            if (aend >= 0) {
                float h = parseComponent(color, hoff, hend, PARSE_ANGLE);
                float s = parseComponent(color, hend+1, send, PARSE_PERCENT);
                float l = parseComponent(color, send+1, lend, PARSE_PERCENT);
                if (hasAlpha) {
                    a *= parseComponent(color, lend+1, aend, PARSE_ALPHA);
                }
                return hsb(h, s, l, a);
            }
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid color specification: " + color, nfe);
        }

        throw new IllegalArgumentException("Invalid color specification: " + color);
    }

    private static final int PARSE_COMPONENT = 0; // percent, or clamped to [0,255] => [0,1]
    private static final int PARSE_PERCENT = 1; // clamped to [0,100]% => [0,1]
    private static final int PARSE_ANGLE = 2; // clamped to [0,360]
    private static final int PARSE_ALPHA = 3; // clamped to [0.0,1.0]
    private static float parseComponent(String color, int off, int end, int type) {
        color = color.substring(off, end).trim();
        if (color.endsWith("%")) {
            if (type > PARSE_PERCENT) {
                throw new IllegalArgumentException("Invalid color specification");
            }
            type = PARSE_PERCENT;
            color = color.substring(0, color.length()-1).trim();
        } else if (type == PARSE_PERCENT) {
            throw new IllegalArgumentException("Invalid color specification");
        }

        float c = ((type == PARSE_COMPONENT)
                ? Integer.parseInt(color)
                : Float.parseFloat(color));
        return switch (type) {
            case PARSE_ALPHA -> (c < 0.0f) ? 0.0f : Math.min(c, 1.0f);
            case PARSE_PERCENT -> (c <= 0.0f) ? 0.0f : ((c >= 100.0f) ? 1.0f : (c / 100.0f));
            case PARSE_COMPONENT -> (c <= 0.0f) ? 0.0f : ((c >= 255.0f) ? 1.0f : (c / 255.0f));
            case PARSE_ANGLE -> ((c < 0.0f)
                    ? ((c % 360.0f) + 360.0f)
                    : ((c > 360.0f) ? (c % 360.0f) : c));
            default -> throw new IllegalArgumentException("Invalid color specification");
        };

    }

    /**
     * Creates a color based on HSV/HSB model.
     * @param hue the hue, in degrees
     * @param saturation the saturation, {@code 0.0 to 1.0}
     * @param brightness the brightness, {@code 0.0 to 1.0}
     * @param opacity the opacity, {@code 0.0 to 1.0}
     * @return the color palette.
     */
    public static Color hsb(float hue, float saturation, float brightness, float opacity) {
        float r = 0.0f;
        float g = 0.0f;
        float b = 0.0f;

        if (saturation == 0) {
            // this color in on the black white center line <=> h = UNDEFINED
            // Achromatic color, there is no hue
            r = brightness;
            g = brightness;
            b = brightness;
        } else {
            if (hue == 1.0f) {
                hue = 0.0f;
            }

            // h is now in [0,6)
            hue *= 6;

            int i = (int) Math.floor(hue);
            float f = hue - i; //f is fractional part of h
            float p = brightness * (1 - saturation);
            float q = brightness * (1 - (saturation * f));
            float t = brightness * (1 - (saturation * (1 - f)));

            switch (i) {
                case 0:
                    r = brightness;
                    g = t;
                    b = p;
                    break;

                case 1:
                    r = q;
                    g = brightness;
                    b = p;
                    break;

                case 2:
                    r = p;
                    g = brightness;
                    b = t;
                    break;

                case 3:
                    r = p;
                    g = q;
                    b = brightness;
                    break;

                case 4:
                    r = t;
                    g = p;
                    b = brightness;
                    break;

                case 5:
                    r = brightness;
                    g = p;
                    b = q;
                    break;
            }
        }

        return new Color(r, g, b, opacity);
    }

    /**
     * Generates terrain color palette.
     * @param n the number of colors in the palette.
     * @return the color palette.
     */
    public static Color[] terrain(int n) {
        return terrain(n, 1.0f);
    }

    /**
     * Generates terrain color palette.
     * @param n the number of colors in the palette.
     * @param alpha the parameter in [0,1] for transparency.
     * @return the color palette.
     */
    public static Color[] terrain(int n, float alpha) {
        int k = n / 2;
        float[] H = {4 / 12f, 2 / 12f, 0 / 12f};
        float[] S = {1f, 1f, 0f};
        float[] V = {0.65f, 0.9f, 0.95f};

        Color[] palette = new Color[n];

        float h = H[0];
        float hw = (H[1] - H[0]) / (k - 1);

        float s = S[0];
        float sw = (S[1] - S[0]) / (k - 1);

        float v = V[0];
        float vw = (V[1] - V[0]) / (k - 1);

        for (int i = 0; i <
                k; i++) {
            palette[i] = hsb(h, s, v, alpha);
            h +=
                    hw;
            s +=
                    sw;
            v +=
                    vw;
        }

        h = H[1];
        hw = (H[2] - H[1]) / (n - k);

        s = S[1];
        sw = (S[2] - S[1]) / (n - k);

        v = V[1];
        vw = (V[2] - V[1]) / (n - k);

        for (int i = k; i < n; i++) {
            h += hw;
            s += sw;
            v += vw;
            palette[i] = hsb(h, s, v, alpha);
        }

        return palette;
    }

    /**
     * Generates topo color palette.
     * @param n the number of colors in the palette.
     * @return the color palette.
     */
    public static Color[] topo(int n) {
        return topo(n, 1.0f);
    }

    /**
     * Generates topo color palette.
     * @param n the number of colors in the palette.
     * @param alpha the parameter in [0,1] for transparency.
     * @return the color palette.
     */
    public static Color[] topo(int n, float alpha) {
        int j = n / 3;
        int k = n / 3;
        int i = n - j - k;

        Color[] palette = new Color[n];

        float h = 43 / 60.0f;
        float hw = (31 / 60.0f - h) / (i - 1);
        int l = 0;
        for (; l < i; l++) {
            palette[l] = hsb(h, 1.0f, 1.0f, alpha);
            h += hw;
        }

        h = 23 / 60.0f;
        hw = (11 / 60.0f - h) / (j - 1);
        for (; l < i + j; l++) {
            palette[l] = hsb(h, 1.0f, 1.0f, alpha);
            h += hw;
        }

        h = 10 / 60.0f;
        hw = (6 / 60.0f - h) / (k - 1);
        float s = 1.0f;
        float sw = (0.3f - s) / (k - 1);
        for (; l < n; l++) {
            palette[l] = hsb(h, s, 1.0f, alpha);
            h += hw;
            s += sw;
        }

        return palette;
    }

    /**
     * Generates jet color palette.
     * @param n the number of colors in the palette.
     * @return the color palette.
     */
    public static Color[] jet(int n) {
        return jet(n, 1.0f);
    }

    /**
     * Generates jet color palette.
     * @param n the number of colors in the palette.
     * @param alpha the parameter in [0,1] for transparency.
     * @return the color palette.
     */
    public static Color[] jet(int n, float alpha) {
        int m = (int) Math.ceil(n / 4.0);

        float[] u = new float[3 * m];
        for (int i = 0; i < u.length; i++) {
            if (i == 0) {
                u[i] = 0.0f;
            } else if (i <= m) {
                u[i] = i / (float) m;
            } else if (i <= 2 * m - 1) {
                u[i] = 1.0f;
            } else {
                u[i] = (3 * m - i) / (float) m;
            }

        }

        int m2 = m / 2 + m % 2;
        int mod = n % 4;
        int[] r = new int[n];
        int[] g = new int[n];
        int[] b = new int[n];
        for (int i = 0; i < u.length - 1; i++) {
            if (m2 - mod + i < n) {
                g[m2 - mod + i] = i + 1;
            }
            if (m2 - mod + i + m < n) {
                r[m2 - mod + i + m] = i + 1;
            }
            if (i > 0 && m2 - mod + i < u.length) {
                b[i] = m2 - mod + i;
            }
        }

        Color[] palette = new Color[n];
        for (int i = 0; i < n; i++) {
            palette[i] = new Color(u[r[i]], u[g[i]], u[b[i]], alpha);
        }

        return palette;
    }

    /**
     * Generates red-green color palette.
     * @param n the number of colors in the palette.
     * @return the color palette.
     */
    public static Color[] redgreen(int n) {
        return redgreen(n, 1.0f);
    }

    /**
     * Generates red-green color palette.
     * @param n the number of colors in the palette.
     * @param alpha the parameter in [0,1] for transparency.
     * @return the color palette.
     */
    public static Color[] redgreen(int n, float alpha) {
        Color[] palette = new Color[n];
        for (int i = 0; i < n; i++) {
            palette[i] = new Color((float) Math.sqrt((i + 1.0f) / n), (float) Math.sqrt(1 - (i + 1.0f) / n), 0.0f, alpha);
        }

        return palette;
    }

    /**
     * Generates red-blue color palette.
     * @param n the number of colors in the palette.
     * @return the color palette.
     */
    public static Color[] redblue(int n) {
        return redblue(n, 1.0f);
    }

    /**
     * Generates red-blue color palette.
     * @param n the number of colors in the palette.
     * @param alpha the parameter in [0,1] for transparency.
     * @return the color palette.
     */
    public static Color[] redblue(int n, float alpha) {
        Color[] palette = new Color[n];
        for (int i = 0; i < n; i++) {
            palette[i] = new Color((float) Math.sqrt((i + 1.0f) / n), 0.0f, (float) Math.sqrt(1 - (i + 1.0f) / n), alpha);
        }

        return palette;
    }

    /**
     * Generates heat color palette.
     * @param n the number of colors in the palette.
     * @return the color palette.
     */
    public static Color[] heat(int n) {
        return heat(n, 1.0f);
    }

    /**
     * Generates heat color palette.
     * @param n the number of colors in the palette.
     * @param alpha the parameter in [0,1] for transparency.
     * @return the color palette.
     */
    public static Color[] heat(int n, float alpha) {
        int j = n / 4;
        int k = n - j;
        float h = 1.0f / 6;

        Color[] c = rainbow(k, 0, h, alpha);

        Color[] palette = new Color[n];
        System.arraycopy(c, 0, palette, 0, k);

        float s = 1 - 1.0f / (2 * j);
        float end = 1.0f / (2 * j);
        float w = (end - s) / (j - 1);

        for (int i = k; i < n; i++) {
            palette[i] = hsb(h, s, 1.0f, alpha);
            s += w;
        }

        return palette;
    }

    /**
     * Generates rainbow color palette.
     * @param n the number of colors in the palette.
     * @return the color palette.
     */
    public static Color[] rainbow(int n) {
        return rainbow(n, 1.0f);
    }

    /**
     * Generates rainbow color palette.
     * @param n the number of colors in the palette.
     * @param alpha the parameter in [0,1] for transparency.
     * @return the color palette.
     */
    public static Color[] rainbow(int n, float alpha) {
        return rainbow(n, 0.0f, (float) (n - 1) / n, alpha);
    }

    /**
     * Generate rainbow color palette.
     * @param n the number of colors in the palette.
     * @param start the start of h in the HSV color model.
     * @param end the start of h in the HSV color model.
     * @param alpha the parameter in [0,1] for transparency.
     * @return the color palette.
     */
    public static Color[] rainbow(int n, float start, float end, float alpha) {
        return rainbow(n, start, end, 1.0f, 1.0f, alpha);
    }

    /**
     * Generates rainbow color palette.
     * @param n the number of colors in the palette.
     * @param start the start of h in the HSV color model.
     * @param end the start of h in the HSV color model.
     * @param s the s in the HSV color model.
     * @param v the v in the HSV color model.
     * @param alpha the parameter in [0,1] for transparency.
     * @return the color palette.
     */
    public static Color[] rainbow(int n, float start, float end, float s, float v, float alpha) {
        Color[] palette = new Color[n];
        float h = start;
        float w = (end - start) / (n - 1);
        for (int i = 0; i < n; i++) {
            palette[i] = hsb(h, s, v, alpha);
            h += w;
        }

        return palette;
    }

    /**
     * A fully transparent color with an ARGB value of #00000000.
     */
    public static final Color TRANSPARENT = new Color(0f, 0f, 0f, 0f);

    /**
     * The color alice blue with an RGB value of #F0F8FF
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#F0F8FF;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color ALICE_BLUE = new Color(0.9411765f, 0.972549f, 1.0f);

    /**
     * The color antique white with an RGB value of #FAEBD7
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FAEBD7;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color ANTIQUE_WHITE = new Color(0.98039216f, 0.92156863f, 0.84313726f);

    /**
     * The color aqua with an RGB value of #00FFFF
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#00FFFF;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color AQUA = new Color(0.0f, 1.0f, 1.0f);

    /**
     * The color aquamarine with an RGB value of #7FFFD4
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#7FFFD4;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color AQUAMARINE = new Color(0.49803922f, 1.0f, 0.83137256f);

    /**
     * The color azure with an RGB value of #F0FFFF
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#F0FFFF;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color AZURE = new Color(0.9411765f, 1.0f, 1.0f);

    /**
     * The color beige with an RGB value of #F5F5DC
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#F5F5DC;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color BEIGE = new Color(0.9607843f, 0.9607843f, 0.8627451f);

    /**
     * The color bisque with an RGB value of #FFE4C4
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFE4C4;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color BISQUE = new Color(1.0f, 0.89411765f, 0.76862746f);

    /**
     * The color black with an RGB value of #000000
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#000000;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color BLACK = Color.BLACK;

    /**
     * The color blanched almond with an RGB value of #FFEBCD
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFEBCD;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color BLANCHED_ALMOND = new Color(1.0f, 0.92156863f, 0.8039216f);

    /**
     * The color blue with an RGB value of #0000FF
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#0000FF;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color BLUE = Color.BLUE;

    /**
     * The color blue violet with an RGB value of #8A2BE2
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#8A2BE2;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color BLUE_VIOLET = new Color(0.5411765f, 0.16862746f, 0.8862745f);

    /**
     * The color brown with an RGB value of #A52A2A
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#A52A2A;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color BROWN = new Color(0.64705884f, 0.16470589f, 0.16470589f);

    /**
     * The color burly wood with an RGB value of #DEB887
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#DEB887;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color BURLYWOOD = new Color(0.87058824f, 0.72156864f, 0.5294118f);

    /**
     * The color cadet blue with an RGB value of #5F9EA0
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#5F9EA0;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color CADET_BLUE = new Color(0.37254903f, 0.61960787f, 0.627451f);

    /**
     * The color chartreuse with an RGB value of #7FFF00
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#7FFF00;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color CHARTREUSE = new Color(0.49803922f, 1.0f, 0.0f);

    /**
     * The color chocolate with an RGB value of #D2691E
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#D2691E;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color CHOCOLATE = new Color(0.8235294f, 0.4117647f, 0.11764706f);

    /**
     * The color coral with an RGB value of #FF7F50
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FF7F50;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color CORAL = new Color(1.0f, 0.49803922f, 0.3137255f);

    /**
     * The color cornflower blue with an RGB value of #6495ED
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#6495ED;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color CORNFLOWER_BLUE = new Color(0.39215687f, 0.58431375f, 0.92941177f);

    /**
     * The color cornsilk with an RGB value of #FFF8DC
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFF8DC;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color CORNSILK = new Color(1.0f, 0.972549f, 0.8627451f);

    /**
     * The color crimson with an RGB value of #DC143C
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#DC143C;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color CRIMSON = new Color(0.8627451f, 0.078431375f, 0.23529412f);

    /**
     * The color cyan with an RGB value of #00FFFF
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#00FFFF;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color CYAN = Color.CYAN;

    /**
     * The color dark blue with an RGB value of #00008B
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#00008B;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_BLUE = new Color(0.0f, 0.0f, 0.54509807f);

    /**
     * The color dark cyan with an RGB value of #008B8B
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#008B8B;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_CYAN = new Color(0.0f, 0.54509807f, 0.54509807f);

    /**
     * The color dark goldenrod with an RGB value of #B8860B
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#B8860B;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_GOLDENROD = new Color(0.72156864f, 0.5254902f, 0.043137256f);

    /**
     * The color dark gray with an RGB value of #A9A9A9
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#A9A9A9;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_GRAY = Color.DARK_GRAY;

    /**
     * The color dark green with an RGB value of #006400
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#006400;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_GREEN = new Color(0.0f, 0.39215687f, 0.0f);

    /**
     * The color dark grey with an RGB value of #A9A9A9
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#A9A9A9;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_GREY = DARK_GRAY;

    /**
     * The color dark khaki with an RGB value of #BDB76B
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#BDB76B;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_KHAKI = new Color(0.7411765f, 0.7176471f, 0.41960785f);

    /**
     * The color dark magenta with an RGB value of #8B008B
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#8B008B;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_MAGENTA = new Color(0.54509807f, 0.0f, 0.54509807f);

    /**
     * The color dark olive green with an RGB value of #556B2F
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#556B2F;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_OLIVE_GREEN = new Color(0.33333334f, 0.41960785f, 0.18431373f);

    /**
     * The color dark orange with an RGB value of #FF8C00
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FF8C00;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_ORANGE = new Color(1.0f, 0.54901963f, 0.0f);

    /**
     * The color dark orchid with an RGB value of #9932CC
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#9932CC;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_ORCHID = new Color(0.6f, 0.19607843f, 0.8f);

    /**
     * The color dark red with an RGB value of #8B0000
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#8B0000;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_RED = new Color(0.54509807f, 0.0f, 0.0f);

    /**
     * The color dark salmon with an RGB value of #E9967A
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#E9967A;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_SALMON = new Color(0.9137255f, 0.5882353f, 0.47843137f);

    /**
     * The color dark sea green with an RGB value of #8FBC8F
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#8FBC8F;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_SEAGREEN = new Color(0.56078434f, 0.7372549f, 0.56078434f);

    /**
     * The color dark slate blue with an RGB value of #483D8B
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#483D8B;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_SLATE_BLUE = new Color(0.28235295f, 0.23921569f, 0.54509807f);

    /**
     * The color dark slate gray with an RGB value of #2F4F4F
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#2F4F4F;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_SLATE_GRAY = new Color(0.18431373f, 0.30980393f, 0.30980393f);

    /**
     * The color dark slate grey with an RGB value of #2F4F4F
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#2F4F4F;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_SLATE_GREY = DARK_SLATE_GRAY;

    /**
     * The color dark turquoise with an RGB value of #00CED1
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#00CED1;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_TURQUOISE = new Color(0.0f, 0.80784315f, 0.81960785f);

    /**
     * The color dark violet with an RGB value of #9400D3
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#9400D3;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DARK_VIOLET = new Color(0.5803922f, 0.0f, 0.827451f);

    /**
     * The color deep pink with an RGB value of #FF1493
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FF1493;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DEEP_PINK = new Color(1.0f, 0.078431375f, 0.5764706f);

    /**
     * The color deep sky blue with an RGB value of #00BFFF
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#00BFFF;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DEEP_SKYBLUE = new Color(0.0f, 0.7490196f, 1.0f);

    /**
     * The color dim gray with an RGB value of #696969
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#696969;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DIM_GRAY = new Color(0.4117647f, 0.4117647f, 0.4117647f);

    /**
     * The color dim grey with an RGB value of #696969
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#696969;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DIM_GREY = DIM_GRAY;

    /**
     * The color dodger blue with an RGB value of #1E90FF
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#1E90FF;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color DODGER_BLUE = new Color(0.11764706f, 0.5647059f, 1.0f);

    /**
     * The color firebrick with an RGB value of #B22222
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#B22222;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color FIREBRICK = new Color(0.69803923f, 0.13333334f, 0.13333334f);

    /**
     * The color floral white with an RGB value of #FFFAF0
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFFAF0;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color FLORAL_WHITE = new Color(1.0f, 0.98039216f, 0.9411765f);

    /**
     * The color forest green with an RGB value of #228B22
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#228B22;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color FOREST_GREEN = new Color(0.13333334f, 0.54509807f, 0.13333334f);

    /**
     * The color fuchsia with an RGB value of #FF00FF
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FF00FF;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color FUCHSIA = new Color(1.0f, 0.0f, 1.0f);

    /**
     * The color gainsboro with an RGB value of #DCDCDC
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#DCDCDC;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color GAINSBORO = new Color(0.8627451f, 0.8627451f, 0.8627451f);

    /**
     * The color ghost white with an RGB value of #F8F8FF
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#F8F8FF;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color GHOST_WHITE = new Color(0.972549f, 0.972549f, 1.0f);

    /**
     * The color gold with an RGB value of #FFD700
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFD700;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color GOLD = new Color(1.0f, 0.84313726f, 0.0f);

    /**
     * The color goldenrod with an RGB value of #DAA520
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#DAA520;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color GOLDENROD = new Color(0.85490197f, 0.64705884f, 0.1254902f);

    /**
     * The color gray with an RGB value of #808080
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#808080;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color GRAY = new Color(0.5019608f, 0.5019608f, 0.5019608f);

    /**
     * The color green with an RGB value of #008000
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#008000;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color GREEN = Color.GREEN;

    /**
     * The color green yellow with an RGB value of #ADFF2F
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#ADFF2F;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color GREEN_YELLOW = new Color(0.6784314f, 1.0f, 0.18431373f);

    /**
     * The color grey with an RGB value of #808080
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#808080;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color GREY = GRAY;

    /**
     * The color honeydew with an RGB value of #F0FFF0
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#F0FFF0;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color HONEYDEW = new Color(0.9411765f, 1.0f, 0.9411765f);

    /**
     * The color hot pink with an RGB value of #FF69B4
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FF69B4;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color HOT_PINK = new Color(1.0f, 0.4117647f, 0.7058824f);

    /**
     * The color indian red with an RGB value of #CD5C5C
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#CD5C5C;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color INDIAN_RED = new Color(0.8039216f, 0.36078432f, 0.36078432f);

    /**
     * The color indigo with an RGB value of #4B0082
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#4B0082;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color INDIGO = new Color(0.29411766f, 0.0f, 0.50980395f);

    /**
     * The color ivory with an RGB value of #FFFFF0
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFFFF0;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color IVORY = new Color(1.0f, 1.0f, 0.9411765f);

    /**
     * The color khaki with an RGB value of #F0E68C
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#F0E68C;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color KHAKI = new Color(0.9411765f, 0.9019608f, 0.54901963f);

    /**
     * The color lavender with an RGB value of #E6E6FA
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#E6E6FA;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LAVENDER = new Color(0.9019608f, 0.9019608f, 0.98039216f);

    /**
     * The color lavender blush with an RGB value of #FFF0F5
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFF0F5;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LAVENDER_BLUSH = new Color(1.0f, 0.9411765f, 0.9607843f);

    /**
     * The color lawn green with an RGB value of #7CFC00
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#7CFC00;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LAWN_GREEN = new Color(0.4862745f, 0.9882353f, 0.0f);

    /**
     * The color lemon chiffon with an RGB value of #FFFACD
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFFACD;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LEMON_CHIFFON = new Color(1.0f, 0.98039216f, 0.8039216f);

    /**
     * The color light blue with an RGB value of #ADD8E6
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#ADD8E6;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_BLUE = new Color(0.6784314f, 0.84705883f, 0.9019608f);

    /**
     * The color light coral with an RGB value of #F08080
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#F08080;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_CORAL = new Color(0.9411765f, 0.5019608f, 0.5019608f);

    /**
     * The color light cyan with an RGB value of #E0FFFF
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#E0FFFF;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_CYAN = new Color(0.8784314f, 1.0f, 1.0f);

    /**
     * The color light goldenrod yellow with an RGB value of #FAFAD2
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FAFAD2;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_GOLDENROD_YELLOW = new Color(0.98039216f, 0.98039216f, 0.8235294f);

    /**
     * The color light gray with an RGB value of #D3D3D3
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#D3D3D3;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_GRAY = Color.LIGHT_GRAY;

    /**
     * The color light green with an RGB value of #90EE90
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#90EE90;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_GREEN = new Color(0.5647059f, 0.93333334f, 0.5647059f);

    /**
     * The color light grey with an RGB value of #D3D3D3
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#D3D3D3;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_GREY = LIGHT_GRAY;

    /**
     * The color light pink with an RGB value of #FFB6C1
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFB6C1;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_PINK = new Color(1.0f, 0.7137255f, 0.75686276f);

    /**
     * The color light salmon with an RGB value of #FFA07A
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFA07A;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_SALMON = new Color(1.0f, 0.627451f, 0.47843137f);

    /**
     * The color light sea green with an RGB value of #20B2AA
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#20B2AA;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_SEAGREEN = new Color(0.1254902f, 0.69803923f, 0.6666667f);

    /**
     * The color light sky blue with an RGB value of #87CEFA
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#87CEFA;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_SKYBLUE = new Color(0.5294118f, 0.80784315f, 0.98039216f);

    /**
     * The color light slate gray with an RGB value of #778899
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#778899;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_SLATE_GRAY = new Color(0.46666667f, 0.53333336f, 0.6f);

    /**
     * The color light slate grey with an RGB value of #778899
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#778899;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_SLATE_GREY = LIGHT_SLATE_GRAY;

    /**
     * The color light steel blue with an RGB value of #B0C4DE
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#B0C4DE;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_STEEL_BLUE = new Color(0.6901961f, 0.76862746f, 0.87058824f);

    /**
     * The color light yellow with an RGB value of #FFFFE0
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFFFE0;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIGHT_YELLOW = new Color(1.0f, 1.0f, 0.8784314f);

    /**
     * The color lime with an RGB value of #00FF00
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#00FF00;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIME = new Color(0.0f, 1.0f, 0.0f);

    /**
     * The color lime green with an RGB value of #32CD32
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#32CD32;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LIME_GREEN = new Color(0.19607843f, 0.8039216f, 0.19607843f);

    /**
     * The color linen with an RGB value of #FAF0E6
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FAF0E6;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color LINEN = new Color(0.98039216f, 0.9411765f, 0.9019608f);

    /**
     * The color magenta with an RGB value of #FF00FF
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FF00FF;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MAGENTA = Color.MAGENTA;

    /**
     * The color maroon with an RGB value of #800000
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#800000;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MAROON = new Color(0.5019608f, 0.0f, 0.0f);

    /**
     * The color medium aquamarine with an RGB value of #66CDAA
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#66CDAA;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MEDIUM_AQUAMARINE = new Color(0.4f, 0.8039216f, 0.6666667f);

    /**
     * The color medium blue with an RGB value of #0000CD
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#0000CD;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MEDIUM_BLUE = new Color(0.0f, 0.0f, 0.8039216f);

    /**
     * The color medium orchid with an RGB value of #BA55D3
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#BA55D3;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MEDIUM_ORCHID = new Color(0.7294118f, 0.33333334f, 0.827451f);

    /**
     * The color medium purple with an RGB value of #9370DB
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#9370DB;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MEDIUM_PURPLE = new Color(0.5764706f, 0.4392157f, 0.85882354f);

    /**
     * The color medium sea green with an RGB value of #3CB371
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#3CB371;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MEDIUM_SEAGREEN = new Color(0.23529412f, 0.7019608f, 0.44313726f);

    /**
     * The color medium slate blue with an RGB value of #7B68EE
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#7B68EE;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MEDIUM_SLATE_BLUE = new Color(0.48235294f, 0.40784314f, 0.93333334f);

    /**
     * The color medium spring green with an RGB value of #00FA9A
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#00FA9A;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MEDIUM_SPRING_GREEN = new Color(0.0f, 0.98039216f, 0.6039216f);

    /**
     * The color medium turquoise with an RGB value of #48D1CC
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#48D1CC;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MEDIUM_TURQUOISE = new Color(0.28235295f, 0.81960785f, 0.8f);

    /**
     * The color medium violet red with an RGB value of #C71585
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#C71585;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MEDIUM_VIOLET_RED = new Color(0.78039217f, 0.08235294f, 0.52156866f);

    /**
     * The color midnight blue with an RGB value of #191970
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#191970;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MIDNIGHT_BLUE = new Color(0.09803922f, 0.09803922f, 0.4392157f);

    /**
     * The color mint cream with an RGB value of #F5FFFA
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#F5FFFA;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MINT_CREAM = new Color(0.9607843f, 1.0f, 0.98039216f);

    /**
     * The color misty rose with an RGB value of #FFE4E1
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFE4E1;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MISTY_ROSE = new Color(1.0f, 0.89411765f, 0.88235295f);

    /**
     * The color moccasin with an RGB value of #FFE4B5
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFE4B5;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color MOCCASIN = new Color(1.0f, 0.89411765f, 0.70980394f);

    /**
     * The color navajo white with an RGB value of #FFDEAD
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFDEAD;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color NAVAJO_WHITE = new Color(1.0f, 0.87058824f, 0.6784314f);

    /**
     * The color navy with an RGB value of #000080
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#000080;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color NAVY = new Color(0.0f, 0.0f, 0.5019608f);

    /**
     * The color old lace with an RGB value of #FDF5E6
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FDF5E6;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color OLD_LACE = new Color(0.99215686f, 0.9607843f, 0.9019608f);

    /**
     * The color olive with an RGB value of #808000
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#808000;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color OLIVE = new Color(0.5019608f, 0.5019608f, 0.0f);

    /**
     * The color olive drab with an RGB value of #6B8E23
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#6B8E23;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color OLIVE_DRAB = new Color(0.41960785f, 0.5568628f, 0.13725491f);

    /**
     * The color orange with an RGB value of #FFA500
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFA500;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color ORANGE = Color.ORANGE;

    /**
     * The color orange red with an RGB value of #FF4500
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FF4500;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color ORANGE_RED = new Color(1.0f, 0.27058825f, 0.0f);

    /**
     * The color orchid with an RGB value of #DA70D6
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#DA70D6;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color ORCHID = new Color(0.85490197f, 0.4392157f, 0.8392157f);

    /**
     * The color pale goldenrod with an RGB value of #EEE8AA
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#EEE8AA;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color PALE_GOLDENROD = new Color(0.93333334f, 0.9098039f, 0.6666667f);

    /**
     * The color pale green with an RGB value of #98FB98
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#98FB98;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color PALE_GREEN = new Color(0.59607846f, 0.9843137f, 0.59607846f);

    /**
     * The color pale turquoise with an RGB value of #AFEEEE
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#AFEEEE;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color PALE_TURQUOISE = new Color(0.6862745f, 0.93333334f, 0.93333334f);

    /**
     * The color pale violet red with an RGB value of #DB7093
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#DB7093;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color PALE_VIOLET_RED = new Color(0.85882354f, 0.4392157f, 0.5764706f);

    /**
     * The color papaya whip with an RGB value of #FFEFD5
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFEFD5;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color PAPAYA_WHIP = new Color(1.0f, 0.9372549f, 0.8352941f);

    /**
     * The color peach puff with an RGB value of #FFDAB9
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFDAB9;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color PEACH_PUFF = new Color(1.0f, 0.85490197f, 0.7254902f);

    /**
     * The color peru with an RGB value of #CD853F
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#CD853F;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color PERU = new Color(0.8039216f, 0.52156866f, 0.24705882f);

    /**
     * The color pink with an RGB value of #FFC0CB
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFC0CB;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color PINK = Color.PINK;

    /**
     * The color plum with an RGB value of #DDA0DD
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#DDA0DD;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color PLUM = new Color(0.8666667f, 0.627451f, 0.8666667f);

    /**
     * The color powder blue with an RGB value of #B0E0E6
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#B0E0E6;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color POWDER_BLUE = new Color(0.6901961f, 0.8784314f, 0.9019608f);

    /**
     * The color purple with an RGB value of #800080
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#800080;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color PURPLE = new Color(0.5019608f, 0.0f, 0.5019608f);

    /**
     * The color red with an RGB value of #FF0000
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FF0000;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color RED = Color.RED;

    /**
     * The color rosy brown with an RGB value of #BC8F8F
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#BC8F8F;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color ROSY_BROWN = new Color(0.7372549f, 0.56078434f, 0.56078434f);

    /**
     * The color royal blue with an RGB value of #4169E1
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#4169E1;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color ROYAL_BLUE = new Color(0.25490198f, 0.4117647f, 0.88235295f);

    /**
     * The color saddle brown with an RGB value of #8B4513
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#8B4513;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color SADDLE_BROWN = new Color(0.54509807f, 0.27058825f, 0.07450981f);

    /**
     * The color salmon with an RGB value of #FA8072
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FA8072;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color SALMON = new Color(0.98039216f, 0.5019608f, 0.44705883f);

    /**
     * The color sandy brown with an RGB value of #F4A460
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#F4A460;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color SANDY_BROWN = new Color(0.95686275f, 0.6431373f, 0.3764706f);

    /**
     * The color sea green with an RGB value of #2E8B57
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#2E8B57;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color SEAGREEN = new Color(0.18039216f, 0.54509807f, 0.34117648f);

    /**
     * The color seashell with an RGB value of #FFF5EE
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFF5EE;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color SEASHELL = new Color(1.0f, 0.9607843f, 0.93333334f);

    /**
     * The color sienna with an RGB value of #A0522D
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#A0522D;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color SIENNA = new Color(0.627451f, 0.32156864f, 0.1764706f);

    /**
     * The color silver with an RGB value of #C0C0C0
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#C0C0C0;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color SILVER = new Color(0.7529412f, 0.7529412f, 0.7529412f);

    /**
     * The color sky blue with an RGB value of #87CEEB
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#87CEEB;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color SKY_BLUE = new Color(0.5294118f, 0.80784315f, 0.92156863f);

    /**
     * The color slate blue with an RGB value of #6A5ACD
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#6A5ACD;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color SLATE_BLUE = new Color(0.41568628f, 0.3529412f, 0.8039216f);

    /**
     * The color slate gray with an RGB value of #708090
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#708090;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color SLATE_GRAY = new Color(0.4392157f, 0.5019608f, 0.5647059f);

    /**
     * The color slate grey with an RGB value of #708090
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#708090;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color SLATE_GREY = SLATE_GRAY;

    /**
     * The color snow with an RGB value of #FFFAFA
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFFAFA;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color SNOW = new Color(1.0f, 0.98039216f, 0.98039216f);

    /**
     * The color spring green with an RGB value of #00FF7F
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#00FF7F;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color SPRING_GREEN = new Color(0.0f, 1.0f, 0.49803922f);

    /**
     * The color steel blue with an RGB value of #4682B4
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#4682B4;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color STEEL_BLUE = new Color(0.27450982f, 0.50980395f, 0.7058824f);

    /**
     * The color tan with an RGB value of #D2B48C
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#D2B48C;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color TAN = new Color(0.8235294f, 0.7058824f, 0.54901963f);

    /**
     * The color teal with an RGB value of #008080
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#008080;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color TEAL = new Color(0.0f, 0.5019608f, 0.5019608f);

    /**
     * The color thistle with an RGB value of #D8BFD8
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#D8BFD8;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color THISTLE = new Color(0.84705883f, 0.7490196f, 0.84705883f);

    /**
     * The color tomato with an RGB value of #FF6347
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FF6347;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color TOMATO = new Color(1.0f, 0.3882353f, 0.2784314f);

    /**
     * The color turquoise with an RGB value of #40E0D0
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#40E0D0;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color TURQUOISE = new Color(0.2509804f, 0.8784314f, 0.8156863f);

    /**
     * The color violet with an RGB value of #EE82EE
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#EE82EE;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color VIOLET = new Color(0.93333334f, 0.50980395f, 0.93333334f);

    /**
     * The color wheat with an RGB value of #F5DEB3
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#F5DEB3;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color WHEAT = new Color(0.9607843f, 0.87058824f, 0.7019608f);

    /**
     * The color white with an RGB value of #FFFFFF
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFFFFF;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color WHITE = Color.WHITE;

    /**
     * The color white smoke with an RGB value of #F5F5F5
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#F5F5F5;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color WHITE_SMOKE = new Color(0.9607843f, 0.9607843f, 0.9607843f);

    /**
     * The color yellow with an RGB value of #FFFF00
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#FFFF00;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color YELLOW = Color.YELLOW;

    /**
     * The color yellow green with an RGB value of #9ACD32
     * <div style="border:1px solid black;width:40px;height:20px;background-color:#9ACD32;float:right;margin: 0 10px 0 0"></div>
     */
    public static final Color YELLOW_GREEN = new Color(0.6039216f, 0.8039216f, 0.19607843f);

    /**
     * Returns a color from Palette.
     * @param index the color index.
     * @return the color.
     */
    public static Color get(int index) {
        return COLORS[index % COLORS.length];
    }

    /** Named colors except white colors. */
    public static final Color[] COLORS = {
            RED,
            BLUE,
            GREEN,
            MAGENTA,
            CYAN,
            PURPLE,
            ORANGE,
            PINK,
            YELLOW,
            BROWN,
            SALMON,
            TURQUOISE,
            PLUM,
            AQUA,
            BISQUE,
            BLUE_VIOLET,
            BURLYWOOD,
            CADET_BLUE,
            CHOCOLATE,
            CORAL,
            CORNFLOWER_BLUE,
            CRIMSON,
            VIOLET,
            GOLDENROD,
            KHAKI,
            ORCHID,
            AQUAMARINE,
            ALICE_BLUE,
            AZURE,
            BEIGE,
            BLANCHED_ALMOND,
            CORNSILK,
            DODGER_BLUE,
            FIREBRICK,
            FOREST_GREEN,
            FUCHSIA,
            GAINSBORO,
            GREEN_YELLOW,
            HONEYDEW,
            HOT_PINK,
            INDIAN_RED,
            INDIGO,
            LAVENDER,
            LAVENDER_BLUSH,
            LAWN_GREEN,
            LEMON_CHIFFON,
            LIME,
            LIME_GREEN,
            LINEN,
            MAROON,
            MIDNIGHT_BLUE,
            MINT_CREAM,
            MISTY_ROSE,
            MOCCASIN,
            NAVY,
            OLD_LACE,
            OLIVE,
            OLIVE_DRAB,
            ORANGE_RED,
            PAPAYA_WHIP,
            PEACH_PUFF,
            PERU,
            POWDER_BLUE,
            ROSY_BROWN,
            ROYAL_BLUE,
            SADDLE_BROWN,
            SANDY_BROWN,
            SEAGREEN,
            SEASHELL,
            SIENNA,
            SKY_BLUE,
            SLATE_GRAY,
            SLATE_BLUE,
            SPRING_GREEN,
            STEEL_BLUE,
            TAN,
            TEAL,
            THISTLE,
            TOMATO,
            WHEAT,
            YELLOW_GREEN,
            GOLD,
            DARK_BLUE,
            DARK_CYAN,
            DARK_GOLDENROD,
            DARK_GRAY,
            DARK_GREEN,
            DARK_KHAKI,
            DARK_MAGENTA,
            DARK_OLIVE_GREEN,
            DARK_ORANGE,
            DARK_ORCHID,
            DARK_RED,
            DARK_SALMON,
            DARK_SEAGREEN,
            DARK_SLATE_BLUE,
            DARK_SLATE_GRAY,
            DARK_TURQUOISE,
            DARK_VIOLET,
            DEEP_PINK,
            DEEP_SKYBLUE,
            DIM_GRAY,
            MEDIUM_AQUAMARINE,
            MEDIUM_BLUE,
            MEDIUM_ORCHID,
            MEDIUM_PURPLE,
            MEDIUM_SEAGREEN,
            MEDIUM_SLATE_BLUE,
            MEDIUM_SPRING_GREEN,
            MEDIUM_TURQUOISE,
            MEDIUM_VIOLET_RED,
            LIGHT_BLUE,
            LIGHT_CORAL,
            LIGHT_CYAN,
            LIGHT_GOLDENROD_YELLOW,
            LIGHT_GRAY,
            LIGHT_GREEN,
            LIGHT_PINK,
            LIGHT_SALMON,
            LIGHT_SEAGREEN,
            LIGHT_SKYBLUE,
            LIGHT_SLATE_GRAY,
            LIGHT_STEEL_BLUE,
            LIGHT_YELLOW,
            PALE_GOLDENROD,
            PALE_GREEN,
            PALE_TURQUOISE,
            PALE_VIOLET_RED,
            BLACK
    };

    /** All modern browsers support the following color names. */
    public static final Map<String, Color> NAMED_COLORS = Map.ofEntries(
            new AbstractMap.SimpleEntry<>("aliceblue", ALICE_BLUE),
            new AbstractMap.SimpleEntry<>("antiquewhite", ANTIQUE_WHITE),
            new AbstractMap.SimpleEntry<>("aqua", AQUA),
            new AbstractMap.SimpleEntry<>("aquamarine", AQUAMARINE),
            new AbstractMap.SimpleEntry<>("azure", AZURE),
            new AbstractMap.SimpleEntry<>("beige", BEIGE),
            new AbstractMap.SimpleEntry<>("bisque", BISQUE),
            new AbstractMap.SimpleEntry<>("black", BLACK),
            new AbstractMap.SimpleEntry<>("blanchedalmond", BLANCHED_ALMOND),
            new AbstractMap.SimpleEntry<>("blue", BLUE),
            new AbstractMap.SimpleEntry<>("blueviolet", BLUE_VIOLET),
            new AbstractMap.SimpleEntry<>("brown", BROWN),
            new AbstractMap.SimpleEntry<>("burlywood", BURLYWOOD),
            new AbstractMap.SimpleEntry<>("cadetblue", CADET_BLUE),
            new AbstractMap.SimpleEntry<>("chartreuse", CHARTREUSE),
            new AbstractMap.SimpleEntry<>("chocolate", CHOCOLATE),
            new AbstractMap.SimpleEntry<>("coral", CORAL),
            new AbstractMap.SimpleEntry<>("cornflowerblue", CORNFLOWER_BLUE),
            new AbstractMap.SimpleEntry<>("cornsilk", CORNSILK),
            new AbstractMap.SimpleEntry<>("crimson", CRIMSON),
            new AbstractMap.SimpleEntry<>("cyan", CYAN),
            new AbstractMap.SimpleEntry<>("darkblue", DARK_BLUE),
            new AbstractMap.SimpleEntry<>("darkcyan", DARK_CYAN),
            new AbstractMap.SimpleEntry<>("darkgoldenrod", DARK_GOLDENROD),
            new AbstractMap.SimpleEntry<>("darkgray", DARK_GRAY),
            new AbstractMap.SimpleEntry<>("darkgreen", DARK_GREEN),
            new AbstractMap.SimpleEntry<>("darkgrey", DARK_GREY),
            new AbstractMap.SimpleEntry<>("darkkhaki", DARK_KHAKI),
            new AbstractMap.SimpleEntry<>("darkmagenta", DARK_MAGENTA),
            new AbstractMap.SimpleEntry<>("darkolivegreen", DARK_OLIVE_GREEN),
            new AbstractMap.SimpleEntry<>("darkorange", DARK_ORANGE),
            new AbstractMap.SimpleEntry<>("darkorchid", DARK_ORCHID),
            new AbstractMap.SimpleEntry<>("darkred", DARK_RED),
            new AbstractMap.SimpleEntry<>("darksalmon", DARK_SALMON),
            new AbstractMap.SimpleEntry<>("darkseagreen", DARK_SEAGREEN),
            new AbstractMap.SimpleEntry<>("darkslateblue", DARK_SLATE_BLUE),
            new AbstractMap.SimpleEntry<>("darkslategray", DARK_SLATE_GRAY),
            new AbstractMap.SimpleEntry<>("darkslategrey", DARK_SLATE_GREY),
            new AbstractMap.SimpleEntry<>("darkturquoise", DARK_TURQUOISE),
            new AbstractMap.SimpleEntry<>("darkviolet", DARK_VIOLET),
            new AbstractMap.SimpleEntry<>("deeppink", DEEP_PINK),
            new AbstractMap.SimpleEntry<>("deepskyblue", DEEP_SKYBLUE),
            new AbstractMap.SimpleEntry<>("dimgray", DIM_GRAY),
            new AbstractMap.SimpleEntry<>("dimgrey", DIM_GREY),
            new AbstractMap.SimpleEntry<>("dodgerblue", DODGER_BLUE),
            new AbstractMap.SimpleEntry<>("firebrick", FIREBRICK),
            new AbstractMap.SimpleEntry<>("floralwhite", FLORAL_WHITE),
            new AbstractMap.SimpleEntry<>("forestgreen", FOREST_GREEN),
            new AbstractMap.SimpleEntry<>("fuchsia", FUCHSIA),
            new AbstractMap.SimpleEntry<>("gainsboro", GAINSBORO),
            new AbstractMap.SimpleEntry<>("ghostwhite", GHOST_WHITE),
            new AbstractMap.SimpleEntry<>("gold", GOLD),
            new AbstractMap.SimpleEntry<>("goldenrod", GOLDENROD),
            new AbstractMap.SimpleEntry<>("gray", GRAY),
            new AbstractMap.SimpleEntry<>("green", GREEN),
            new AbstractMap.SimpleEntry<>("greenyellow", GREEN_YELLOW),
            new AbstractMap.SimpleEntry<>("grey", GREY),
            new AbstractMap.SimpleEntry<>("honeydew", HONEYDEW),
            new AbstractMap.SimpleEntry<>("hotpink", HOT_PINK),
            new AbstractMap.SimpleEntry<>("indianred", INDIAN_RED),
            new AbstractMap.SimpleEntry<>("indigo", INDIGO),
            new AbstractMap.SimpleEntry<>("ivory", IVORY),
            new AbstractMap.SimpleEntry<>("khaki", KHAKI),
            new AbstractMap.SimpleEntry<>("lavender", LAVENDER),
            new AbstractMap.SimpleEntry<>("lavenderblush", LAVENDER_BLUSH),
            new AbstractMap.SimpleEntry<>("lawngreen", LAWN_GREEN),
            new AbstractMap.SimpleEntry<>("lemonchiffon", LEMON_CHIFFON),
            new AbstractMap.SimpleEntry<>("lightblue", LIGHT_BLUE),
            new AbstractMap.SimpleEntry<>("lightcoral", LIGHT_CORAL),
            new AbstractMap.SimpleEntry<>("lightcyan", LIGHT_CYAN),
            new AbstractMap.SimpleEntry<>("lightgoldenrodyellow", LIGHT_GOLDENROD_YELLOW),
            new AbstractMap.SimpleEntry<>("lightgray", LIGHT_GRAY),
            new AbstractMap.SimpleEntry<>("lightgreen", LIGHT_GREEN),
            new AbstractMap.SimpleEntry<>("lightgrey", LIGHT_GREY),
            new AbstractMap.SimpleEntry<>("lightpink", LIGHT_PINK),
            new AbstractMap.SimpleEntry<>("lightsalmon", LIGHT_SALMON),
            new AbstractMap.SimpleEntry<>("lightseagreen", LIGHT_SEAGREEN),
            new AbstractMap.SimpleEntry<>("lightskyblue", LIGHT_SKYBLUE),
            new AbstractMap.SimpleEntry<>("lightslategray", LIGHT_SLATE_GRAY),
            new AbstractMap.SimpleEntry<>("lightslategrey", LIGHT_SLATE_GREY),
            new AbstractMap.SimpleEntry<>("lightsteelblue", LIGHT_STEEL_BLUE),
            new AbstractMap.SimpleEntry<>("lightyellow", LIGHT_YELLOW),
            new AbstractMap.SimpleEntry<>("lime", LIME),
            new AbstractMap.SimpleEntry<>("limegreen", LIME_GREEN),
            new AbstractMap.SimpleEntry<>("linen", LINEN),
            new AbstractMap.SimpleEntry<>("magenta", MAGENTA),
            new AbstractMap.SimpleEntry<>("maroon", MAROON),
            new AbstractMap.SimpleEntry<>("mediumaquamarine", MEDIUM_AQUAMARINE),
            new AbstractMap.SimpleEntry<>("mediumblue", MEDIUM_BLUE),
            new AbstractMap.SimpleEntry<>("mediumorchid", MEDIUM_ORCHID),
            new AbstractMap.SimpleEntry<>("mediumpurple", MEDIUM_PURPLE),
            new AbstractMap.SimpleEntry<>("mediumseagreen", MEDIUM_SEAGREEN),
            new AbstractMap.SimpleEntry<>("mediumslateblue", MEDIUM_SLATE_BLUE),
            new AbstractMap.SimpleEntry<>("mediumspringgreen", MEDIUM_SPRING_GREEN),
            new AbstractMap.SimpleEntry<>("mediumturquoise", MEDIUM_TURQUOISE),
            new AbstractMap.SimpleEntry<>("mediumvioletred", MEDIUM_VIOLET_RED),
            new AbstractMap.SimpleEntry<>("midnightblue", MIDNIGHT_BLUE),
            new AbstractMap.SimpleEntry<>("mintcream", MINT_CREAM),
            new AbstractMap.SimpleEntry<>("mistyrose", MISTY_ROSE),
            new AbstractMap.SimpleEntry<>("moccasin", MOCCASIN),
            new AbstractMap.SimpleEntry<>("navajowhite", NAVAJO_WHITE),
            new AbstractMap.SimpleEntry<>("navy", NAVY),
            new AbstractMap.SimpleEntry<>("oldlace", OLD_LACE),
            new AbstractMap.SimpleEntry<>("olive", OLIVE),
            new AbstractMap.SimpleEntry<>("olivedrab", OLIVE_DRAB),
            new AbstractMap.SimpleEntry<>("orange", ORANGE),
            new AbstractMap.SimpleEntry<>("orangered", ORANGE_RED),
            new AbstractMap.SimpleEntry<>("orchid", ORCHID),
            new AbstractMap.SimpleEntry<>("palegoldenrod", PALE_GOLDENROD),
            new AbstractMap.SimpleEntry<>("palegreen", PALE_GREEN),
            new AbstractMap.SimpleEntry<>("paleturquoise", PALE_TURQUOISE),
            new AbstractMap.SimpleEntry<>("palevioletred", PALE_VIOLET_RED),
            new AbstractMap.SimpleEntry<>("papayawhip", PAPAYA_WHIP),
            new AbstractMap.SimpleEntry<>("peachpuff", PEACH_PUFF),
            new AbstractMap.SimpleEntry<>("peru", PERU),
            new AbstractMap.SimpleEntry<>("pink", PINK),
            new AbstractMap.SimpleEntry<>("plum", PLUM),
            new AbstractMap.SimpleEntry<>("powderblue", POWDER_BLUE),
            new AbstractMap.SimpleEntry<>("purple", PURPLE),
            new AbstractMap.SimpleEntry<>("red", RED),
            new AbstractMap.SimpleEntry<>("rosybrown", ROSY_BROWN),
            new AbstractMap.SimpleEntry<>("royalblue", ROYAL_BLUE),
            new AbstractMap.SimpleEntry<>("saddlebrown", SADDLE_BROWN),
            new AbstractMap.SimpleEntry<>("salmon", SALMON),
            new AbstractMap.SimpleEntry<>("sandybrown", SANDY_BROWN),
            new AbstractMap.SimpleEntry<>("seagreen", SEAGREEN),
            new AbstractMap.SimpleEntry<>("seashell", SEASHELL),
            new AbstractMap.SimpleEntry<>("sienna", SIENNA),
            new AbstractMap.SimpleEntry<>("silver", SILVER),
            new AbstractMap.SimpleEntry<>("skyblue", SKY_BLUE),
            new AbstractMap.SimpleEntry<>("slateblue", SLATE_BLUE),
            new AbstractMap.SimpleEntry<>("slategray", SLATE_GRAY),
            new AbstractMap.SimpleEntry<>("slategrey", SLATE_GREY),
            new AbstractMap.SimpleEntry<>("snow", SNOW),
            new AbstractMap.SimpleEntry<>("springgreen", SPRING_GREEN),
            new AbstractMap.SimpleEntry<>("steelblue", STEEL_BLUE),
            new AbstractMap.SimpleEntry<>("tan", TAN),
            new AbstractMap.SimpleEntry<>("teal", TEAL),
            new AbstractMap.SimpleEntry<>("thistle", THISTLE),
            new AbstractMap.SimpleEntry<>("tomato", TOMATO),
            new AbstractMap.SimpleEntry<>("transparent", TRANSPARENT),
            new AbstractMap.SimpleEntry<>("turquoise", TURQUOISE),
            new AbstractMap.SimpleEntry<>("violet", VIOLET),
            new AbstractMap.SimpleEntry<>("wheat", WHEAT),
            new AbstractMap.SimpleEntry<>("white", WHITE),
            new AbstractMap.SimpleEntry<>("whitesmoke", WHITE_SMOKE),
            new AbstractMap.SimpleEntry<>("yellow", YELLOW),
            new AbstractMap.SimpleEntry<>("yellowgreen", YELLOW_GREEN)
    );
}

