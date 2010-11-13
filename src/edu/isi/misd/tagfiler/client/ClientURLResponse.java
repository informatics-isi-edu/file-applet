/**
 * 
 */
package edu.isi.misd.tagfiler.client;

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
