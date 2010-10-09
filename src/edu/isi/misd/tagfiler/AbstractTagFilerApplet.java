package edu.isi.misd.tagfiler;

import java.awt.Color;
import java.awt.Component;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.Timer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JApplet;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import netscape.javascript.JSException;
import netscape.javascript.JSObject;
import edu.isi.misd.tagfiler.security.TagFilerSecurity;
import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.ui.CustomTagMapImplementation;
import edu.isi.misd.tagfiler.ui.FileUI;
import edu.isi.misd.tagfiler.util.ClientUtils;
import edu.isi.misd.tagfiler.util.TagFilerProperties;
import edu.isi.misd.tagfiler.util.TagFilerPropertyUtils;

/**
 * Common parent class for the upload and download applet, which is responsible
 * for session and browser-related responsibilities.
 * 
 * @author David Smith
 * 
 */
public abstract class AbstractTagFilerApplet extends JApplet implements FileUI {

    private static final long serialVersionUID = 1L;

    // parameter name for applet test file
    private static final String TAGFILER_APPLET_TEST_FILE = "tagfiler.applet.test";

    // parameter name for applet log file
    private static final String TAGFILER_APPLET_LOG_FILE = "tagfiler.applet.log";

    // parameter name for the tagserver URL
    private static final String TAGFILER_SERVER_URL_PARAM = "tagfiler.server.url";

    private static final String COOKIE_NAME_PROPERTY = "tagfiler.cookie.name";
    
    private static final String FONT_COLOR_PROPERTY = "tagfiler.font.color";

    // tagfiler server URL specified from the parameter of the applet
    protected String tagFilerServerURL = null;

    // cookie maintainined in the session
    protected String sessionCookie = null;

    // map containing the names and values of custom tags
    protected CustomTagMap customTagMap = new CustomTagMapImplementation();

    // font, color used in the applet
    protected Color fontColor;

    // file chooser for upload/download directory
    protected JFileChooser fileChooser = null;

    // true if the applet runs in test mode
    protected boolean testMode;
    
    // true if the applet has stopped
    protected boolean stopped;
    
    // true if a upload/download directory select request was issued 
    protected boolean browseDir;
    
    // object for synchronizing the threads
    protected Object lock= new Object();

    // properties used by the test
    protected Properties testProperties = new Properties();

    // timer to schedule tasks
    protected Timer filesTimer;

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

        // redirect the log messages
        String logFile = this.getParameter(TAGFILER_APPLET_LOG_FILE);
        if (logFile != null) {
            try {
            	PrintStream ps = new PrintStream(new FileOutputStream(logFile, true));
            	System.setOut(ps);
            	System.setErr(ps);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
    }
    
    /**
     * Stop the threads
     */
    public void stop() {
    	synchronized (lock) {
    		stopped = true;
        	lock.notifyAll();
    	}
    	super.stop();
    }

    /**
     * Create the applet UI.
     */
    protected void createUI() {

        fontColor = TagFilerPropertyUtils.renderColor(FONT_COLOR_PROPERTY);

        // panel for creating an icon with green background
        final JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(BorderFactory.createLineBorder(Color.gray, 2));
        main.setBackground(Color.green);
        main.validate();

        // file chooser window
        fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);

        getContentPane().setBackground(Color.white);
        getContentPane().add(main);
        // end main panel ---------------------------
    }

    /**
     * Convenience method for updating the status label
     * 
     * @param status
     */
    protected void updateStatus(String status) {
        //updateStatus(status, fontColor);
        try {
            JSObject window = (JSObject) JSObject.getWindow(this);

            window.eval("setStatus('" + status + "')");
        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
        	e.printStackTrace();
        }

    }

    /**
     * @return the component representing this UI
     */
    public Component getComponent() {
        return getContentPane();
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
            JOptionPane.showMessageDialog(this.getComponent(), e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);

        }
    }

}
