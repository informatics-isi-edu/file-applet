package edu.isi.misd.tagfiler.client;

import java.applet.Applet;
import java.io.File;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.config.ApacheHttpClientConfig;
import com.sun.jersey.client.apache.config.DefaultApacheHttpClientConfig;

import edu.isi.misd.tagfiler.util.ClientUtils;

/**
 * Client class for using Jersey API to handle Web Services
 * 
 * @author Serban Voinea
 * 
 */
public class JerseyClient implements ClientURL {

    // client used to connect with the tagfiler server
    private final Client client;
    
    // the response received by the client
    private ClientResponse response;
    
    public JerseyClient() {
        final ApacheHttpClientConfig config = new DefaultApacheHttpClientConfig();
        config.getProperties().put(
                ApacheHttpClientConfig.PROPERTY_HANDLE_COOKIES, true);

        client = ApacheHttpClient.create(config);
    	
    }
    
    /**
     * Get the list of the file names to be downloaded.
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     */
    public void getDataSet(String url, Cookie cookie) {
        response = client.resource(url)
	        .accept("text/uri-list")
	        .type(MediaType.APPLICATION_OCTET_STREAM).cookie(cookie)
	        .get(ClientResponse.class);
    }
    
    /**
     * Get the value of a tag
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     */
    public void getTagValue(String url, Cookie cookie) {
        response = client.resource(url)
	        .type(MediaType.APPLICATION_OCTET_STREAM).cookie(cookie)
	        .get(ClientResponse.class);
    }
    
    /**
     * Get the content of a file to be downloaded
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     */
    public void downloadFile(String url, Cookie cookie) {
	    response = client.resource(url)
	        .type(MediaType.APPLICATION_OCTET_STREAM).cookie(cookie)
	        .get(ClientResponse.class);
    }
    
    /**
     * Verify that we have a valid control number
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     */
    public void verifyValidControlNumber(String url, Cookie cookie) {
        response = client
        .resource(url)
        		.accept("text/uri-list")
        .type(MediaType.APPLICATION_OCTET_STREAM).cookie(cookie)
        .head();
    }
    
    /**
     * Get a new control number
     * 
     * @param url
     *            the query url
     * @param cookie
     *            the cookie to be set in the request
     */
    public void getTransmitNumber(String url, Cookie cookie) {
    	response = client.resource(url)
	        .type(MediaType.APPLICATION_OCTET_STREAM).cookie(cookie)
	        .post(ClientResponse.class, "");
    }
    
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
    public WebResource createWebResource(String u, Cookie cookie) {
        assert (u != null);

        return client.resource(u);
    }

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
    public javax.ws.rs.core.Cookie updateSessionCookie(Applet applet, Cookie cookie) {
        Iterator<NewCookie> cookies = response.getCookies().iterator();
        while (cookies.hasNext()) {
            javax.ws.rs.core.Cookie candidate = cookies.next().toCookie();
            if (candidate.getName().equals("webauthn")) {
                // this is a new session cookie, so save it for further REST
                // calls
                ClientUtils.setCookieInBrowser(applet, cookie);
                return candidate;
            }
        }
        return cookie;
    }
    
    /**
     * Retrieves a given cookie from a client response
     * 
     * @param cookieName
     *            name of the cookie
     * @return the new cookie of the same name, or null if it wasn't found
     */
    public Cookie getCookieFromClientResponse(String cookieName) {
        List<NewCookie> cookies = response.getCookies();
        for (NewCookie cookie : cookies) {
            if (cookie.getName().equals(cookieName)) {
                return cookie;
            }
        }
        return null;
    }

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
    public boolean checkResponseHeaderPattern(String headerName, String expectedPattern) {
        assert (response != null);
        assert (headerName != null && headerName.length() != 0);
        assert (expectedPattern != null);

        boolean matches = false;

        MultivaluedMap<String, String> map = response.getHeaders();
        String headerValue = map.getFirst(headerName);
        System.out.println("checkResponseHeaderPattern: headerName="
                + headerName + ", headerValue=" + headerValue
                + ", expectedPattern=" + expectedPattern);
        if (headerValue != null && headerValue.matches(expectedPattern)) {
            matches = true;
        }
        return matches;
    }

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
    public void postFileData(String url, String datasetURLBody, Cookie cookie) {
        response = client.resource(url)
        .type(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        .cookie(cookie).post(ClientResponse.class, datasetURLBody);
    }

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
    public void postFile(String url, File file, Cookie cookie) {
        response = client.resource(url)
        .type(MediaType.APPLICATION_OCTET_STREAM)
        .cookie(cookie).put(ClientResponse.class, file);
    }

    /**
     * Return the status code
     * 
     */
	public int getStatus() {
    	return response.getStatus();
    }

    /**
     * Return the body as a string
     * 
     */
    public String getEntityString() {
    	return response.getEntity(String.class);
    }

    /**
     * Return the location as a string
     * 
     */
    public String getLocationString() {
    	return response.getLocation().toString();
    }

    /**
     * Return the InputStream from where the body can be read
     * 
     */
    public InputStream getEntityInputStream() {
    	return response.getEntityInputStream();
    }

    /**
     * Set the chunked encoding size
     * 
     */
    public void setChunkedEncodingSize(int size) {
	    client.setChunkedEncodingSize(size);
    }

    /**
     * Release the responses
     * 
     */
    public void close() {
    	if (response != null) {
            response.close();
        }
    }


}
