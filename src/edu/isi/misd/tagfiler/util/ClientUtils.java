package edu.isi.misd.tagfiler.util;

import java.applet.Applet;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;

import netscape.javascript.JSObject;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.config.ApacheHttpClientConfig;
import com.sun.jersey.client.apache.config.DefaultApacheHttpClientConfig;

import edu.isi.misd.tagfiler.client.ClientURL;
import edu.isi.misd.tagfiler.client.JerseyClient;

/**
 * Various helper utilities for Jersey Client related operations.
 * 
 * @author David Smith
 * 
 */
public class ClientUtils {

    private static final String DOCUMENT_MEMBER_NAME = "document";

    private static final String COOKIE_MEMBER_NAME = "cookie";

    public static final String LOCATION_HEADER_NAME = "Location";

    /**
     * Creates a {@link com.sun.jersey.api.client.Client} instance with the
     * necessary configuration for the TagFiler server.
     * 
     * @return a new instance of a Jersey client
     */
    public static ClientURL getClientURL() {

        return new JerseyClient();
    }

    /**
     * Retrieves a cookie from the client browser
     * 
     * @param applet
     *            the applet that is embedded in the browser
     * @param cookieName
     *            name of the cookie to retrieve
     * @return the cookie if it was found, or null if it doesn't exist
     */
    public static Cookie getCookieFromBrowser(Applet applet, String cookieName) {
        assert (applet != null);
        assert (cookieName != null && cookieName.length() > 0);

        Cookie cookieObj = null;
        String cookie = null;

        // this will retrieve all the cookies, or possibly null if none exist
        Object jsObject = ((JSObject) ((JSObject) JSObject.getWindow(applet))
                .getMember(DOCUMENT_MEMBER_NAME)).getMember(COOKIE_MEMBER_NAME);
        if (jsObject instanceof JSObject) {
            // this typing seems to work on Firefox on Fedora...
            cookie = ((JSObject) jsObject).toString();
        } else {
            // this typing seems to work on Firefox on Fedora...
            cookie = (String) jsObject;
        }

        if (cookie != null && cookieName.length() > 0) {
            final String search = cookieName + "=";
            int offset = cookie.indexOf(search);
            if (offset >= 0) {
                offset += search.length();
                int end = cookie.indexOf(";", offset);
                if (end < 0) {
                    end = cookie.length();
                }
                cookie = cookie.substring(offset, end);
                try {
                    cookieObj = new Cookie(cookieName, cookie);
                } catch (IllegalArgumentException e) {
                    // badly formatted cookie -- print error but continue so
                    // that we get a new cookie on the next auth
                    e.printStackTrace();
                }
            }
        }

        return cookieObj;
    }

    /**
     * Sets a cookie on the browser
     * 
     * @param applet
     *            the applet that is embedded in the browser
     * @param cookie
     *            the cookie to set
     */
    public static void setCookieInBrowser(Applet applet, Cookie cookie) {
        final String cookieStr = cookie.toString();

        // TODO: I think this will wipe out any other cookies for this domain.
        // Perhaps we should append/replace.
	String jstext = "setCookie(\"" + cookie.getName() + "\", \"" + cookie.getValue() + "\")";
        JSObject.getWindow(applet).eval(jstext);
    }

}
