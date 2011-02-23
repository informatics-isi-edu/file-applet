package edu.isi.misd.tagfiler.ui;

/* 
 * Copyright 2010 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        if (key == null) throw new IllegalArgumentException(key);
        synchronized (tagMap) {
            tagMap.put(key, value);
        }
    }

    /**
     * returns the value of the tag
     */
    public String getValue(String key) {
        if (key == null) throw new IllegalArgumentException(key);

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
