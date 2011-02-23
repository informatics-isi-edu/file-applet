package edu.isi.misd.tagfiler.security;

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
