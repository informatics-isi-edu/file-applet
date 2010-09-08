package edu.isi.misd.tagfiler.ui;

import java.awt.Component;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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

    private final Map<String, String> tagTypeMap = new LinkedHashMap<String, String>();

    private final List<String> requiredTags;

    /**
     * Default constructor.
     */
    public CustomTagMapImplementation() {
        final String customVariableStr = TagFilerProperties
                .getProperty("tagfiler.tag.userdefined");
        final String customVariableTypesStr = TagFilerProperties
        		.getProperty("tagfiler.tag.typestr");
        final String customRequiredStr = TagFilerProperties
        		.getProperty("tagfiler.tag.required");
        final String[] customVariables = customVariableStr.split(",");
        final String[] customTypes = customVariableTypesStr.split(",");
        final String[] customRequired = customRequiredStr.split(",");
        requiredTags = Arrays.asList(customRequired);

        synchronized (tagMap) {

            for (int i = 0; i < customVariables.length; i++) {
                tagMap.put(customVariables[i], new JTextField());
                tagTypeMap.put(customVariables[i], customTypes[i]);
            }
        }
    }
    
    /**
     * Validates a tag/value pair.
     * Throw an exception if values are not defined for required tags or if a date is not in the MM-dd-yyyy format
     * @param name
     *            the tag name.
     * @param value
     *            the tag value.
     */
    public boolean validate(String name, String value) throws Exception {
    	assert(value != null && name != null && tagMap.get(name) != null);
    	
    	if (requiredTags.contains(name) && value.length() == 0) {
    		throw new Exception("Tag \"" + name + "\" is required.");
    	}
    	
    	if (value.length() > 0 && tagTypeMap.get(name).equals("date")) {
    		try {
    			(new SimpleDateFormat("MM-dd-yyyy")).parse(value);
    		}
    		catch (Throwable e) {
    			throw new Exception("Bad value for tag \"" + name + "\".");
    		}
    	}
    	
    	return true;
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
