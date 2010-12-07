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

/**
 * Common parent class for the upload and download applet, which is responsible
 * for session and browser-related responsibilities.
 * 
 * @author David Smith
 * 
 */
public abstract class AbstractTagFilerApplet extends JApplet implements FileUI {

    private static final long serialVersionUID = 1L;

    // parameter name for the chunk size
    private static final String CHUNK_SIZE = "tagfiler.chunkbytes";

    // parameter name for the socket buffer size
    private static final String SOCKET_BUFFER_SIZE = "tagfiler.socket.buffer.size";

    // parameter name for transfering the file in chunks
    private static final String TAGFILER_ALLOW_CHUNKS = "tagfiler.allow.chunks";

    // parameter name for the connection numbers
    private static final String TAGFILER_APPLET_MAX_CONNECTIONS = "tagfiler.connections";

    // parameter name for applet test file
    private static final String TAGFILER_CUSTOM_PROPERTIES = "custom.properties";

    // parameter name for applet test file
    private static final String TAGFILER_APPLET_TEST_FILE = "tagfiler.applet.test";

    // parameter name for applet log file
    private static final String TAGFILER_APPLET_LOG_FILE = "tagfiler.applet.log";

    // parameter name for the tagserver URL
    private static final String TAGFILER_SERVER_URL_PARAM = "tagfiler.server.url";

    private static final String COOKIE_NAME_PROPERTY = "tagfiler.cookie.name";
    
    // tagfiler server URL specified from the parameter of the applet
    protected String tagFilerServerURL = null;

    // cookie maintainined in the session
    protected String sessionCookie = null;

    // map containing the names and values of custom tags
    protected CustomTagMap customTagMap = new CustomTagMapImplementation();

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

    
    protected int maxConnections = 2;

    protected int socketBufferSize = 8192;

    protected int chunkSize = 4194304;

    // the mode for transfering the file
    protected boolean allowChunks;
    
    // Window for JavaScript calls
    protected JSObject window;

    /**
     * Loads security settings, common parameters, session cookie
     */
	public void init() {

    	// load any security settings
        TagFilerSecurity.loadSecuritySettings();
        
        window = (JSObject) JSObject.getWindow(this);
        if (window == null) {
            throw new IllegalArgumentException("NULL JavaScript window");
        }


        sessionCookie = ClientUtils.getCookieFromBrowser(this,
                TagFilerProperties.getProperty(COOKIE_NAME_PROPERTY));

        // arguments
        tagFilerServerURL = this.getParameter(TAGFILER_SERVER_URL_PARAM);
        if (tagFilerServerURL == null || tagFilerServerURL.length() == 0) {
            throw new IllegalArgumentException(TAGFILER_SERVER_URL_PARAM
                    + " must be" + " specified as a parameter to the applet.");
        }

        // arguments
        String maxConn = this.getParameter(TAGFILER_APPLET_MAX_CONNECTIONS);
        if (maxConn != null) {
        	maxConnections = Integer.parseInt(maxConn);
        }

        // arguments
        String socketBuffSize = this.getParameter(SOCKET_BUFFER_SIZE);
        if (socketBuffSize != null) {
        	socketBufferSize = Integer.parseInt(socketBuffSize);
        }

        // arguments
        String value = this.getParameter(TAGFILER_ALLOW_CHUNKS);
        if (value != null) {
        	allowChunks = Boolean.parseBoolean(value);
        }

        // arguments
        value = this.getParameter(CHUNK_SIZE);
        if (value != null) {
        	chunkSize = Integer.parseInt(value);
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

        // check for custom properties
        String customProperties = this.getParameter(TAGFILER_CUSTOM_PROPERTIES);
        if (customProperties != null) {
        	String properties[] = customProperties.split(",");
        	for (int i=0; i < properties.length; i++) {
        		String property[] = properties[i].split("=");
        		
        		if (property.length > 1) {
        			TagFilerProperties.setProperty(property[0].trim(), property[1].trim());
        		}
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
    public void updateStatus(String status) {
        try {
            window.eval("setStatus('" + status + "')");
        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
        	e.printStackTrace();
        }

    }
    
    /**
     * Convenience method for drawing the progress bar
     * 
     * @param percent
     */
    protected void drawProgressBar(long percent) {
        try {
            window.eval("drawProgressBar(" + percent + ")");
        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
        	e.printStackTrace();
        }
    }

    /**
     * Convenience method for evaluating a JS function
     * 
     * @param function
     * 			the function to be evaluated
     */
    public String eval(String function) {
    	String res = "";
        try {
            res = (String) window.eval(function);
        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
        	e.printStackTrace();
        }
        
        return res;
    }

    /**
     * Convenience method for evaluating a JS function
     * 
     * @param function
     * 			the function to be evaluated
     * @param arg
     * 			the function argument
     */
    public void eval(String function, String arg) {
        try {
            window.eval(function + "('" + arg + "')");
        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
        	e.printStackTrace();
        }
    }

    /**
     * Convenience method for enabling an HTML button
     * 
     * @param button
     * 			the button to be enabled
     */
    protected void setEnabled(String button) {
        try {
            window.eval("setEnabled('" + button + "')");
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
    
    public String getTagFilerServerURL() {
    	return tagFilerServerURL;
    }

    /**
     * Redirects to an url
     */
    public void redirect(String urlStr) {
        if (urlStr == null) throw new IllegalArgumentException(urlStr);
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
            ((JSObject) window.getMember("location")).call("reload", new Boolean[] { true });
        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
            JOptionPane.showMessageDialog(this.getComponent(), e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);

        }
    }

    /**
     * Get the maximum number of HTTP connections
     * 
     * @return the maximum number of HTTP connections
     */
    public int getMaxConnections() {
    	return maxConnections;
    }
    
    /**
     * Get the socket buffer size
     * 
     * @return the socket buffer size
     */
    public int getSocketBufferSize() {
    	return socketBufferSize;
    }
    
    /**
     * Get the chunk size
     * 
     * @return the chunk size
     */
    public int getChunkSize() {
    	return chunkSize;
    }
    
    /**
     * Get the mode for transferring the file
     * 
     * @return the mode for transferring the file
     */
    public boolean allowChunksTransfering() {
    	return allowChunks;
    }

    /**
     * Getter method
     * 
     * @return the mode for transferring the file
     */
	public JSObject getWindow() {
		return window;
	}

}
