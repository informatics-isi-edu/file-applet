package edu.isi.misd.tagfiler.util;

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

import netscape.javascript.JSObject;

import edu.isi.misd.tagfiler.AbstractTagFilerApplet;
import edu.isi.misd.tagfiler.client.ClientURL;
import edu.isi.misd.tagfiler.client.JakartaClient;

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
    public static ClientURL getClientURL(int maxConnections, int socketBufferSize) {

    	JakartaClient client = new JakartaClient(maxConnections, socketBufferSize);
    	
    	if (client.isValid()) {
    		return client;
    	} else {
    		return null;
    	}
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
    public static String getCookieFromBrowser(AbstractTagFilerApplet applet, String cookieName) {
        if (applet == null||
        		cookieName == null || cookieName.length() == 0) 
        	throw new IllegalArgumentException(""+applet+", "+cookieName);

        String cookie = null;

        // this will retrieve all the cookies, or possibly null if none exist
        Object jsObject = ((JSObject) applet.getWindow()
                .getMember(DOCUMENT_MEMBER_NAME)).getMember(COOKIE_MEMBER_NAME);
        if (jsObject instanceof JSObject) {
            // this typing seems to work on Firefox on Fedora...
            cookie = ((JSObject) jsObject).toString();
        } else {
            // this typing seems to work on Firefox on Fedora...
            cookie = (String) jsObject;
        }
        
        if (cookie == null) {
        	cookie = (String) applet.getWindow().eval("getCookie('"+cookieName+"')");
        } else {
            final String search = cookieName + "=";
            int offset = cookie.indexOf(search);
            if (offset >= 0) {
                offset += search.length();
                int end = cookie.indexOf(";", offset);
                if (end < 0) {
                    end = cookie.length();
                }
                cookie = cookie.substring(offset, end);
            }
        }
        
        return cookie;
    }

    /**
     * Sets a cookie on the browser
     * 
     * @param applet
     *            the applet that is embedded in the browser
     * @param cookie
     *            the cookie to set
     */
    public static void setCookieInBrowser(AbstractTagFilerApplet applet, String cookie) {
        // TODO: I think this will wipe out any other cookies for this domain.
        // Perhaps we should append/replace.
	String jstext = "setCookie(\"webauthn\", \"" + cookie + "\")";
	applet.getWindow().eval(jstext);
    }

}
