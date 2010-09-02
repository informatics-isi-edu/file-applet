package edu.isi.misd.tagfiler.security;

import org.apache.commons.httpclient.contrib.ssl.EasySSLProtocolSocketFactory;
import org.apache.commons.httpclient.protocol.Protocol;

import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Class that is responsible for managing security-related tasks.
 * 
 * @author David Smith
 * 
 */
public class TagFilerSecurity {

    /**
     * Loads any security settings into the current environment.
     */
    @SuppressWarnings("deprecation")
    public static void loadSecuritySettings() {

        // Read whether or not to allow self-signed certificates to be
        // authenticated,
        // if so then load a custom socket factory that allows them to be
        // trusted
        if (Boolean.valueOf(TagFilerProperties
                .getProperty("tagfiler.security.AllowSelfSignedCerts"))) {
            Protocol https = new Protocol("https",
                    new EasySSLProtocolSocketFactory(), 443);
            Protocol.registerProtocol("https", https);
        }
    }
}
