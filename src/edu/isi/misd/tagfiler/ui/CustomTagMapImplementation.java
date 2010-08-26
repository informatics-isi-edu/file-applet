package edu.isi.misd.tagfiler.ui;

import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.JTextField;

import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Implementation of {@link edu.isi.misd.tagfiler.ui.CustomTagMap}.
 * 
 * @author David Smith
 * 
 */
public class CustomTagMapImplementation implements CustomTagMap {

    private final Map<String, JTextField> tagMap = new LinkedHashMap<String, JTextField>();

    /**
     * Default constructor.
     */
    public CustomTagMapImplementation() {
        final String customVariableStr = TagFilerProperties
                .getProperty("tagfiler.tag.userdefined");
        final String[] customVariables = customVariableStr.split(",");

        synchronized (tagMap) {

            for (int i = 0; i < customVariables.length; i++) {
                tagMap.put(customVariables[i], new JTextField());
            }
        }
    }

    /**
     * sets the value of the tag
     */
    public void setValue(String key, String value) {
        assert (key != null);
        final JTextField field;
        synchronized (tagMap) {
            field = tagMap.get(key);
        }

        if (field != null) {
            field.setText(value);
        }
    }

    /**
     * returns the value of the tag
     */
    public String getValue(String key) {
        assert (key != null);

        String value = "";
        final JTextField field;
        synchronized (tagMap) {
            field = tagMap.get(key);
        }
        if (field != null) {
            value = field.getText().trim();
        }
        return value;
    }

    /**
     * returns the component of the tag
     */
    public Component getComponent(String key) {
        assert (key != null);
        final JTextField field;
        synchronized (tagMap) {
            field = tagMap.get(key);
        }

        return field;
    }

    /**
     * returns all the tag names
     */
    public Set<String> getTagNames() {
        final Set<String> keys;
        synchronized (tagMap) {
            keys = tagMap.keySet();
        }
        return keys;
    }

    /**
     * clears the tags
     */
    public void clearValues() {
        synchronized (tagMap) {
            Set<String> keys = tagMap.keySet();
            for (String key : keys) {
                final JTextField field = tagMap.get(key);
                if (field != null) {
                    field.setText("");
                }
            }
        }
    }
}
