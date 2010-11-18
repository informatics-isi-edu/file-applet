package edu.isi.misd.tagfiler.client;

import java.applet.Applet;
import java.io.File;
import java.io.InputStream;

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
     * @return the HTTP Response
     */
    public ClientURLResponse getDataSet(String url, String cookie);
    
    /**
     * Get the value of a tag
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse getTagValue(String url, String cookie);
    
    /**
     * Get the values of a dataset tags
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse getTagsValues(String url, String cookie);
    
    /**
     * Get the content of a file to be downloaded
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse downloadFile(String url, String cookie);
    
    /**
     * Get the content of a file to be downloaded
     * 
     * @param url
     *            the query url
     * @param length
     *            the number of bytes to read
     * @param first
     *            the first byte to read
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse downloadFile(String url,  long length, long first, String cookie);
    
    /**
     * Execute a login request.
     * If success, it will get a cookie
     * 
     * @param url
     *            the query url
     * @param user
     *            the userid
     * @param password
     *            the password
     * @return the HTTP Response
     */
    public ClientURLResponse login(String url, String user, String password);
    
    /**
     * Verify that we have a valid control number
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse verifyValidControlNumber(String url, String cookie);
    
    /**
     * Get the length of a file to be downloaded
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse getFileLength(String url, String cookie);
    
    /**
     * Get a new control number
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse getTransmitNumber(String url, String cookie);
    
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
     * @return the HTTP Response
     */
    public ClientURLResponse postFileData(String url, String datasetURLBody, String cookie);
    
    /**
     * Uploads a set of given files with a specified dataset name.
     * 
     * @param url
     *            the query url
     * @param file
     *            the file to be abloaded
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse postFile(String url, File file, String cookie);
    
    /**
     * Uploads a file block.
     * 
     * @param url
     *            the query url
     * @param inputStream
     *            the InputStream where to read from
     * @param length
     *            the number of bytes to read
     * @param first
     *            the first byte to read
     * @param fileLength
     *            the file length
     * @param cookie
     *            the cookie to be set in the request
     * @return the HTTP Response
     */
    public ClientURLResponse postFile(String url, InputStream inputStream, long length, long first, long fileLength, String cookie);
    
    /**
     * Set the chunked encoding size
     * 
     */
    public void setChunkedEncodingSize(int size);
    
}
