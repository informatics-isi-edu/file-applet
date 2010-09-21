package edu.isi.misd.tagfiler.util;

import java.awt.Color;
import java.awt.Font;

/**
 * Utility class for tagfiler property tasks.
 * 
 * @author David Smith
 * 
 */
public final class TagFilerPropertyUtils {
    /**
     * Renders a color object from properties
     * 
     * @param colorPropertyName
     *            name of the property containing the color
     * @return the Color
     */
    public static Color renderColor(String colorPropertyName) {
        assert (colorPropertyName != null && colorPropertyName.length() > 0);
        final String colorStr = TagFilerProperties
                .getProperty(colorPropertyName);
        final String[] pieces = colorStr.split(",");
        final int[] color = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            color[i] = Integer.parseInt(pieces[i].trim());
        }
        return new Color(color[0], color[1], color[2]);
    }

    /**
     * 
     * @param fontNameProperty
     *            property for font name
     * @param fontStyleProperty
     *            property for font style
     * @param fontSizeProperty
     *            property for font size
     * @return a font object with the given name, style, and size
     */
    public static Font renderFont(String fontNameProperty,
            String fontStyleProperty, String fontSizeProperty) {
        assert (fontNameProperty != null && fontNameProperty.length() > 0);
        assert (fontStyleProperty != null && fontStyleProperty.length() > 0);
        assert (fontSizeProperty != null && fontSizeProperty.length() > 0);

        return new Font(TagFilerProperties.getProperty(fontNameProperty),
                Integer.parseInt(TagFilerProperties
                        .getProperty(fontStyleProperty)),
                Integer.parseInt(TagFilerProperties
                        .getProperty(fontSizeProperty)));
    }
}
