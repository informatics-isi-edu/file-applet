package edu.isi.misd.tagfiler;

import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.JApplet;
import javax.swing.JOptionPane;
import javax.ws.rs.core.Cookie;

import netscape.javascript.JSException;
import netscape.javascript.JSObject;
import edu.isi.misd.tagfiler.security.TagFilerSecurity;
import edu.isi.misd.tagfiler.ui.FileUI;
import edu.isi.misd.tagfiler.util.JerseyClientUtils;
import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Common parent class for the upload and download applet, which is responsible
 * for session and browser-related responsibilities.
 * 
 * @author David Smith
 * 
 */
public abstract class AbstractTagFilerApplet extends JApplet implements FileUI {

    private static final long serialVersionUID = 1L;

    // parameter name for the tagserver URL
    private static final String TAGFILER_SERVER_URL_PARAM = "tagfiler.server.url";

    // parameter name for the tagserver URL
    private static final String TAGFILER_AUTHN_URL_PARAM = "tagfiler.authn.url";

    private static final String COOKIE_NAME_PROPERTY = "tagfiler.cookie.name";
    // tagfiler server URL specified from the parameter of the applet
    protected String tagFilerServerURL = null;

    // cookie maintainined in the session
    protected Cookie sessionCookie = null;

    private String tagFilerWebauthURL;

    /**
     * Loads security settings, common parameters, session cookie
     */
    public void init() {

    	// load any security settings
        TagFilerSecurity.loadSecuritySettings();

        sessionCookie = JerseyClientUtils.getCookieFromBrowser(this,
                TagFilerProperties.getProperty(COOKIE_NAME_PROPERTY));

        // arguments
        tagFilerServerURL = this.getParameter(TAGFILER_SERVER_URL_PARAM);
        if (tagFilerServerURL == null || tagFilerServerURL.length() == 0) {
            throw new IllegalArgumentException(TAGFILER_SERVER_URL_PARAM
                    + " must be" + " specified as a parameter to the applet.");
        }

        // use the tagFilerServerURL to get the webauth URL
        // TODO: pass this as a parameter
        try {
            final URL tagFilerURL = new URL(tagFilerServerURL);
            tagFilerWebauthURL = tagFilerURL.getProtocol() + "://"
                    + tagFilerURL.getHost();
            if (tagFilerURL.getPort() > 0) {
                tagFilerWebauthURL = tagFilerWebauthURL + ":"
                        + tagFilerURL.getPort();
            }
            tagFilerWebauthURL = tagFilerWebauthURL + "/webauthn";
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(tagFilerServerURL
                    + " is not a valid URL for the tagfiler server.");
        }
    }

    /**
     * Redirects to an url
     */
    public void redirect(String urlStr) {
        assert (urlStr != null);
        System.out.println("redirect: " + urlStr);
        try {
            final URL url = new URL(urlStr);
            getAppletContext().showDocument(url, "_self");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reloads the UI
     */
    public void reload() {
        this.stop();
        this.destroy();

        try {
            JSObject window = (JSObject) JSObject.getWindow(
                    AbstractTagFilerApplet.this).getMember("location");

            window.call("reload", new Boolean[] { true });

        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
            deactivate();
            JOptionPane.showMessageDialog(this.getComponent(), e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);

        }
    }

    /**
     * Child class must provide a method of accessing the file transfer object
     * 
     * @return the FileTransfer object
     */
    abstract protected FileTransfer getFileTransfer();
    
}
