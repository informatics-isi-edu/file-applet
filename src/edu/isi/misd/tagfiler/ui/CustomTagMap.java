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
