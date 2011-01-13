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
import edu.isi.misd.tagfiler.exception.FatalException;
import edu.isi.misd.tagfiler.security.TagFilerSecurity;
import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.ui.CustomTagMapImplementation;
import edu.isi.misd.tagfiler.ui.FileListener;
import edu.isi.misd.tagfiler.util.ClientUtils;
import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Common parent class for the upload and download applet, which is responsible
 * for session and browser-related responsibilities.
 * 
 * @author David Smith
 * 
 */
public abstract class AbstractTagFilerApplet extends JApplet {

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

    // parameter name for cookie name
    private static final String COOKIE_NAME_PROPERTY = "tagfiler.cookie.name";
    
    // timeout for JavaScript call execution (milliseconds)
    private static final long JAVASCRIPT_TIMEOUT = 10 * 1000;
    
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
    
    // listener to send notifications
    protected FileListener fileListener;
    
    // thread to execute javaScript calls
    private JavaScriptThread javaScriptThread;

    /**
     * Loads security settings, common parameters, session cookie
     */
	public void init() {

    	// load any security settings
        TagFilerSecurity.loadSecuritySettings();
        
        // start javaScriptThread
        javaScriptThread = new JavaScriptThread();
        javaScriptThread.start();
        
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
    	synchronized (javaScriptThread.getLoadLock()) {
        	javaScriptThread.setReady(true);
        	javaScriptThread.getLoadLock().notifyAll();
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
    	invoke("setStatus('" + status + "')");
    }
    
    /**
     * Convenience method for drawing the progress bar
     * 
     * @param percent
     */
    protected void drawProgressBar(long percent) {
    	invoke("drawProgressBar(" + percent + ")");
    }

    /**
     * Convenience method for evaluating a JS function
     * 
     * @param function
     * 			the function to be evaluated
     * @return the JS function evaluation result
     */
    public String eval(String function) {
    	String res = (String) invoke(function);
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
    	invoke(function + "('" + arg + "')");
    }

    /**
     * Convenience method for enabling an HTML button
     * 
     * @param button
     * 			the button to be enabled
     */
    protected void setEnabled(String button) {
    	invoke("setEnabled('" + button + "')");
    }

    /**
     * Convenience method to check if the checksum is on
     * 
     * @return true if checksum is enabled, false otherwise
     */
    protected boolean getChecksum() {
        boolean value = false;
    	try {
            value = Boolean.parseBoolean((String) invoke("getChecksum()"));
        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
        	e.printStackTrace();
        }
        return value;
    }

    /**
     * Convenience method to get the dataset name
     * 
     * @return the dataset name
     */
    protected String getDatasetName() {
        String value = null;
    	try {
            value = (String) invoke("getDatasetName()");
        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
        	e.printStackTrace();
        }
        return value;
    }

    /**
     * Convenience method for evaluating a JS function
     * 
     * @param function
     * 			the function to be evaluated
     * @return the JS function evaluation result
     */
    public Object invoke(String function) {
    	Object res = "";
    	// set the command to be executed to the JavaScript thread
    	synchronized (javaScriptThread.getLoadLock()) {
        	javaScriptThread.setCommand(function);
        	javaScriptThread.getLoadLock().notifyAll();
    	}
    	
    	long t0, t1;
    	t0 = t1 = System.currentTimeMillis();
    	// wait now for the result, but not more than 100 seconds
    	synchronized (javaScriptThread.getReleaseLock()) {
    		while (javaScriptThread.getCommand() != null && (t1 - t0) < JAVASCRIPT_TIMEOUT) {
    			try {
					javaScriptThread.getReleaseLock().wait(JAVASCRIPT_TIMEOUT - (t1 - t0));
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				t1 = System.currentTimeMillis();
    		}
    		if (javaScriptThread.getCommand() == null) {
    			// success
    			res = javaScriptThread.getResult();
    		} else {
    			// timeout; raise FatalException
    			System.out.println("OOPS: The browser is not responding.");
                JOptionPane.showMessageDialog(this.getComponent(), "The browser is not responding.\nIt is recommended to restart the browser.",
                        "Warning", JOptionPane.WARNING_MESSAGE);
    			FatalException e = new FatalException("The browser is not responding");
    			fileListener.notifyFatal(e);
    		}
    	}
        return res;
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
	
	private class JavaScriptThread extends Thread {
		// the command to be evaluated by the JavaScript
		String command;
		
		// the javaScript evaluation result
		Object result;
		
		// mutex for receiving commands
		Object loadLock = new Object();
		
		// mutex for releasing results
		Object releaseLock = new Object();
		
		// the javaScript evaluation result
		boolean ready;
		
		public void run() {
			while (true) {
				synchronized (loadLock) {
					while (!ready && command == null) {
						try {
							loadLock.wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				if (ready) {
					break;
				} else {
					result = window.eval(command);
					synchronized (releaseLock) {
						command = null;
						releaseLock.notifyAll();
					}
				}
			}
		}

		public void setReady(boolean ready) {
			this.ready = ready;
		}
		
		public Object getResult() {
			return result;
		}

		public String getCommand() {
			return command;
		}

		public Object getLoadLock() {
			return loadLock;
		}

		public void setCommand(String command) {
			this.command = command;
		}

		public Object getReleaseLock() {
			return releaseLock;
		}
	}

}
