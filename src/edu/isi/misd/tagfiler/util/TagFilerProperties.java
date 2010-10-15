package edu.isi.misd.tagfiler.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Responsible for reading properties from a properties file.
 * 
 * @author David Smith
 * 
 */
public class TagFilerProperties {

    private static final Properties properties = new Properties();

    private static final String PROPERTIES_FILE = "tagfiler.properties";

    // reads the property file once
    static {
        try {
            final InputStream stream = TagFilerProperties.class
                    .getResourceAsStream(PROPERTIES_FILE);
            properties.load(stream);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Set a property that might come from the applet parameters
     * 
     * @param propertyName
     *            name of the property
     * @param propertyValue
     *            value of the property
     */
    public static void setProperty(String propertyName, String propertyValue) {
        assert (propertyName != null && propertyValue != null);
        properties.put(propertyName, propertyValue);
    }

    /**
     * 
     * @param propertyName
     *            name of the property
     * @return the value of the property in string format
     */
    public static String getProperty(String propertyName) {
        assert (propertyName != null);
        return properties.getProperty(propertyName);
    }

    /**
     * 
     * @param propertyName
     *            name of the property
     * @param args
     *            arguments passed to the value
     * @return the value of the property with the arguments embedded
     */
    public static String getProperty(String propertyName, String[] args) {
        assert (propertyName != null);
        assert (args != null);

        String value = getProperty(propertyName);
        for (int i = 0; i < args.length; i++) {
            value = value.replaceFirst("%s", args[i]);
        }
        return value;
    }
}
