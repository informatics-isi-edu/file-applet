package edu.isi.misd.tagfiler;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import javax.swing.JApplet;
import javax.swing.JOptionPane;

import netscape.javascript.JSException;
import netscape.javascript.JSObject;
import edu.isi.misd.tagfiler.security.TagFilerSecurity;
import edu.isi.misd.tagfiler.ui.FileUI;
import edu.isi.misd.tagfiler.util.ClientUtils;
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
    private static final String TAGFILER_APPLET_TEST_FILE = "tagfiler.applet.test";

    // parameter name for the tagserver URL
    private static final String TAGFILER_SERVER_URL_PARAM = "tagfiler.server.url";

    // parameter name for the tagserver URL
    private static final String TAGFILER_AUTHN_URL_PARAM = "tagfiler.authn.url";

    private static final String COOKIE_NAME_PROPERTY = "tagfiler.cookie.name";
    // tagfiler server URL specified from the parameter of the applet
    protected String tagFilerServerURL = null;

    // cookie maintainined in the session
    protected String sessionCookie = null;

    private String tagFilerWebauthURL;

    protected boolean testMode;
    protected boolean stopped;
    protected boolean browseDir;
    protected Object lock= new Object();

    protected Properties testProperties = new Properties();

    /**
     * Loads security settings, common parameters, session cookie
     */
    public void init() {

    	// load any security settings
        TagFilerSecurity.loadSecuritySettings();

        sessionCookie = ClientUtils.getCookieFromBrowser(this,
                TagFilerProperties.getProperty(COOKIE_NAME_PROPERTY));

        // arguments
        tagFilerServerURL = this.getParameter(TAGFILER_SERVER_URL_PARAM);
        if (tagFilerServerURL == null || tagFilerServerURL.length() == 0) {
            throw new IllegalArgumentException(TAGFILER_SERVER_URL_PARAM
                    + " must be" + " specified as a parameter to the applet.");
        }

        // arguments
        String testFile = this.getParameter(TAGFILER_APPLET_TEST_FILE);
        if (testFile != null) {
        	try {
				FileInputStream fis = new FileInputStream(testFile);
				testProperties.load(fis);
	        	testMode = true;
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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
    
    public void stop() {
    	synchronized (lock) {
    		stopped = true;
        	lock.notifyAll();
    	}
    	super.stop();
    }

    /**
     * Redirects to an url
     */
    public void redirect(String urlStr) {
        assert (urlStr != null);
        this.stop();
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
