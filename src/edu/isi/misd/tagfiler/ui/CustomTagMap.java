package edu.isi.misd.tagfiler.ui;

import java.awt.Component;
import java.util.Set;

/**
 * Map that contains the custom tags to use in the file upload. This map
 * contains a collection of tag names that are predefined, along with a
 * user-editable component that contains the value to use for each tag.
 * 
 * @author David Smith
 * 
 */
public interface CustomTagMap {

    /**
     * Sets the value of the tag's component
     * 
     * @param key
     *            name of the tag
     * @param value
     *            the tag's new value
     */
    public void setValue(String key, String value);
    
    /**
     * 
     * @param key
     *            name of the tag
     * @return the value of the tag
     */
    public String getValue(String key);

    /**
     * 
     * @return a set of all the tag names defined.
     */
    public Set<String> getTagNames();

    /**
     * Clears the map of the tags.
     */
    public void clearValues();
}
