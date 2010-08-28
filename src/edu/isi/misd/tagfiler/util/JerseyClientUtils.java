package edu.isi.misd.tagfiler.util;

import java.applet.Applet;
import java.util.List;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;

import netscape.javascript.JSObject;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.config.ApacheHttpClientConfig;
import com.sun.jersey.client.apache.config.DefaultApacheHttpClientConfig;

/**
 * Various helper utilities for Jersey Client related operations.
 * 
 * @author David Smith
 * 
 */
public class JerseyClientUtils {

    private static final String DOCUMENT_MEMBER_NAME = "document";

    private static final String COOKIE_MEMBER_NAME = "cookie";

    /**
     * Creates a {@link com.sun.jersey.api.client.Client} instance with the
     * necessary configuration for the TagFiler server.
     * 
     * @return a new instance of a Jersey client
     */
    public static Client createClient() {
        // TODO: generate specific configuration for each jersey client
        // that is used
        final ApacheHttpClientConfig config = new DefaultApacheHttpClientConfig();
        config.getProperties().put(
                ApacheHttpClientConfig.PROPERTY_HANDLE_COOKIES, true);

        final Client client = ApacheHttpClient.create(config);

        return client;
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
    public static WebResource createWebResource(Client client, String u,
            Cookie cookie) {
        assert (client != null);
        assert (u != null);
        final WebResource resource = client.resource(u);
        if (cookie != null) {
            resource.cookie(cookie);
        }
        return resource;
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

        // this will retrieve all the cookies, or possibly null if none exist
        String cookie = (String) ((JSObject) ((JSObject) JSObject
                .getWindow(applet)).getMember(DOCUMENT_MEMBER_NAME))
                .getMember(COOKIE_MEMBER_NAME);
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
                    cookieObj = Cookie.valueOf(cookie);
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
        ((JSObject) JSObject.getWindow(applet).getMember(DOCUMENT_MEMBER_NAME))
                .setMember(COOKIE_MEMBER_NAME, cookieStr);
    }

    /**
     * Retrieves a given cookie from a client response
     * 
     * @param response
     *            the client response
     * @param cookieName
     *            name of the cookie
     * @return the new cookie of the same name, or null if it wasn't found
     */
    public static Cookie getCookieFromClientResponse(ClientResponse response,
            String cookieName) {
        List<NewCookie> cookies = response.getCookies();
        for (NewCookie cookie : cookies) {
            if (cookie.getName().equals(cookieName)) {
                return cookie;
            }
        }
        return null;
    }
}