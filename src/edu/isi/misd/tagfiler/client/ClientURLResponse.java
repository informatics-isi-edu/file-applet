package edu.isi.misd.tagfiler.client;

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

import java.io.InputStream;

/**
 * @author Serban Voinea
 *
 */
public interface ClientURLResponse {

    /**
     * checks a particular header in the response to see if it matches an
     * expected regular expression pattern
     * 
     * @param headerName
     *            name of the header to check
     * @param expectedPattern
     *            regular expression pattern to check
     * @return true if the header exists and matches the regular expression
     */
    public boolean checkResponseHeaderPattern(String headerName, String expectedPattern);
    
    /**
     * Return the HTTP status code
     * 
     */
    public int getStatus();

    /**
     * Return the body as a string
     * 
     */
    public String getEntityString();

    /**
     * Return the location as a string
     * 
     */
    public String getLocationString();
    
    /**
     * Return the InputStream from where the body can be read
     * 
     */
    public InputStream getEntityInputStream();

    /**
     * Return the error message of an HTTP Response
     * 
     */
    public String getErrorMessage();
    
    /**
     * Get the response size
     * 
     */
    public long getResponseSize();
    
    /**
     * Release the response
     * 
     */
    public void release();

}
