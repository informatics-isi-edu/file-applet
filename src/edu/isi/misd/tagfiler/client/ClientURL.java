package edu.isi.misd.tagfiler.client;

import java.applet.Applet;
import java.io.File;
import java.io.InputStream;

import com.sun.jersey.api.client.WebResource;

/**
 * Client Interface to handle Web Services
 * 
 * @author Serban Voinea
 * 
 */
public interface ClientURL {
    /**
     * Get the list of the file names to be downloaded.
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     */
    public void getDataSet(String url, String cookie);
    
    /**
     * Get the value of a tag
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     */
    public void getTagValue(String url, String cookie);
    
    /**
     * Get the content of a file to be downloaded
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     */
    public void downloadFile(String url, String cookie);
    
    /**
     * Verify that we have a valid control number
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     */
    public void verifyValidControlNumber(String url, String cookie);
    
    /**
     * checks a particular header in the response to see if it matches an
     * expected regular expression pattern
     * 
     * @param response
     *            the client response
     * @param headerName
     *            name of the header to check
     * @param expectedPattern
     *            regular expression pattern to check
     * @return true if the header exists and matches the regular expression
     */
    public boolean checkResponseHeaderPattern(String headerName, String expectedPattern);
    
    /**
     * Get a new control number
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     */
    public void getTransmitNumber(String url, String cookie);
    
    /**
     * Creates a web resource instance contaiing a cookie
     * 
     * @param client
     *            the Jersey client
     * @param u
     *            the string of the resource
     * @param cookie
     *            the cookie to attach to the request, or null if none
     * @return a web resource for the string with the cookie attached
     */
    public WebResource createWebResource(String u, String cookie);
    
    /**
     * Checks for and saves updated session cookie
     * 
     * @param response
     *            the Jersey response
     * @param applet
     *            the applet
     * @param cookie
     *            the current cookie
     * @return the curernt cookie or a new replacement
     */
    public String updateSessionCookie(Applet applet, String cookie);
    
    /**
     * Retrieves a given cookie from a client response
     * 
     * @param cookieName
     *            name of the cookie
     * @return the new cookie of the same name, or null if it wasn't found
     */
    public String getCookieFromClientResponse(String cookieName);
    
    /**
     * Uploads a set of given files with a specified dataset name.
     * 
     * @param url
     *            the query url
     * @param datasetURLBody
     *            the body of the dataset
     * @param cookie
     *            the cookie to be set in the request
     */
    public void postFileData(String url, String datasetURLBody, String cookie);
    
    /**
     * Uploads a set of given files with a specified dataset name.
     * 
     * @param url
     *            the query url
     * @param file
     *            the file to be abloaded
     * @param cookie
     *            the cookie to be set in the request
     */
    public void postFile(String url, File file, String cookie);
    
    /**
     * Return the status code
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
     * Set the chunked encoding size
     * 
     */
    public void setChunkedEncodingSize(int size);
    
    /**
     * Release the responses
     * 
     */
    public void close();

}
