package edu.isi.misd.tagfiler.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of {@link edu.isi.misd.tagfiler.ui.CustomTagMap}.
 * 
 * @author David Smith
 * 
 */
public class CustomTagMapImplementation implements CustomTagMap {

    private final Map<String, String> tagMap = new LinkedHashMap<String, String>();

    /**
     * sets the value of the tag
     */
    public void setValue(String key, String value) {
        assert (key != null);
        synchronized (tagMap) {
            tagMap.put(key, value);
        }
    }

    /**
     * returns the value of the tag
     */
    public String getValue(String key) {
        assert (key != null);

        final String field;
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
                tagMap.put(key, "");
            }
        }
    }
}
