package edu.isi.misd.tagfiler;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import netscape.javascript.JSException;
import netscape.javascript.JSObject;

import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.ui.CustomTagMapImplementation;
import edu.isi.misd.tagfiler.ui.FileUploadAddListener;
import edu.isi.misd.tagfiler.ui.FileUploadUI;
import edu.isi.misd.tagfiler.upload.FileUpload;
import edu.isi.misd.tagfiler.upload.FileUploadImplementation;
import edu.isi.misd.tagfiler.upload.FileUploadListener;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.TagFilerProperties;
import edu.isi.misd.tagfiler.util.TagFilerPropertyUtils;

/**
 * Applet class that is used for uploading a directory of files from an end
 * user's browser to a tagfiler servlet with custom tags associated with it. The
 * dataset name is first generated as a random 10-digit number and is created as
 * a URL (representing the dataset query) in the dataset with the custom tags
 * assigned to it. Each file that is uploaded in the dataset has its checksum
 * stored in an attribute and is given a name containing the dataset name and
 * the path relative to the directory that was chosen for uploading from the
 * client machine. An attribute that contains the dataset name is also stored so
 * that the collection of files can be easily queried.
 * 
 * @param tagfiler
 *            .server.url Required parameter for the applet that specifies the
 *            URL of the tagfiler server and path to connect to (i.e.
 *            https://tagfiler.isi.edu:443/tagfiler)
 * 
 * @author David Smith
 * 
 */
public final class TagFilerUploadApplet extends AbstractTagFilerApplet
        implements FileUploadUI {

    private static final long serialVersionUID = 2134123;

    // parameters referenced from the applet.properties file
    private static final String FONT_NAME_PROPERTY = "tagfiler.font.name";

    private static final String FONT_STYLE_PROPERTY = "tagfiler.font.style";

    private static final String FONT_SIZE_PROPERTY = "tagfiler.font.size";

    private static final String FONT_COLOR_PROPERTY = "tagfiler.font.color";

    private JButton addBtn = null;

    private DefaultListModel filesToUpload = null;

    private JFileChooser fileChooser = null;

    // does the work of the file upload
    private FileUpload fileUpload = null;

    // font, color used in the applet
    private Color fontColor;

    private Font font;

    // map containing the names and values of custom tags
    private CustomTagMap customTagMap = null;

    private Timer filesTimer;

    private boolean upload;

    /**
     * Initializes the applet by reading parameters, polling the tagfiler
     * servlet to retrieve any authentication requests, and constructing the
     * applet UI.
     */
    public void init() {
        super.init();

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    createUI();

                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
            (new TagFilerAppletUploadListener()).notifyFatal(e);
        }
    }

	public void start() {
        super.start();
        
    	filesTimer = new Timer(true);
    	filesTimer.schedule(new UploadTask(), 1000);
    	
        if (testMode) {
        	filesTimer.schedule(new TestTimerTask(), 1000);
        }
    }

    /**
     * Create the applet UI.
     */
    private void createUI() {

        fontColor = TagFilerPropertyUtils.renderColor(FONT_COLOR_PROPERTY);
        font = TagFilerPropertyUtils.renderFont(FONT_NAME_PROPERTY,
                FONT_STYLE_PROPERTY, FONT_SIZE_PROPERTY);

        addBtn = new JButton(
                TagFilerProperties.getProperty("tagfiler.button.Browse"));

        filesToUpload = new DefaultListModel();

       final JLabel lbl = createLabel(TagFilerProperties
                .getProperty("tagfiler.label.SelectDirectoryToUpload"));

        final JPanel top = createPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setAlignmentX(Component.CENTER_ALIGNMENT);
        top.setAlignmentY(Component.TOP_ALIGNMENT);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        addBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        top.add(lbl, Component.CENTER_ALIGNMENT);
        top.add(addBtn, Component.CENTER_ALIGNMENT);
        top.validate();

        customTagMap = new CustomTagMapImplementation();

        // file chooser window
        fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(TagFilerProperties
                .getProperty("tagfiler.filedialog.SelectDirectoryToUpload"));
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);

        // the file uploader itself
        // TODO: create container for cookie so that the object reference
        // remains intact when
        // they are replaced
        fileUpload = new FileUploadImplementation(tagFilerServerURL,
                new TagFilerAppletUploadListener(), customTagMap,
                sessionCookie, this);

        // listeners
        addBtn.addActionListener(new FileUploadAddListener(this, fileUpload,
                fileChooser, getContentPane(), filesToUpload));

        // begin main panel -----------------------
        final JPanel main = createPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(BorderFactory.createLineBorder(Color.gray, 2));
        main.add(top);

        getContentPane().setBackground(Color.white);
        getContentPane().add(main);
        // end main panel ---------------------------
    }

    /**
     * Creates a label
     * 
     * @param str
     *            text of the label
     * @return new JLabel with the default styling
     */
    private JLabel createLabel(String str) {
        final JLabel label = new JLabel(str);
        label.setBackground(Color.white);
        label.setForeground(fontColor);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setFont(font);
        return label;
    }

    /**
     * Creates a panel
     * 
     * @return a new JPanel with the default styling
     */
    private JPanel createPanel() {

        final JPanel panel = new JPanel();
        panel.setBackground(Color.white);
        panel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        return panel;
    }

    /**
     * Allows the upload action to be invoked.
     */
    public void enableUpload() {
        try {
            JSObject window = (JSObject) JSObject.getWindow(
            		TagFilerUploadApplet.this);

            window.eval("setEnabled('Upload All')");
        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
        	e.printStackTrace();
        }

    }

    /**
     * Disallows the upload action to be invoked.
     */
    public void disableUpload() {
    }

    /**
     * Allows the adding of a directory to be invoked.
     */
    public void enableAdd() {
        addBtn.setEnabled(true);
    }

    /**
     * Disallows the adding of a directory to be invoked.
     */
    public void disableAdd() {
        addBtn.setEnabled(false);
    }

    /**
     * Private class that listens for progress from the file upload process and
     * takes action on the applet UI based on this progress.
     * 
     * @author David Smith
     * 
     */
    private class TagFilerAppletUploadListener implements FileUploadListener {

        private int filesCompleted = 0;

        private int totalFiles = 0;

        private long totalBytes = 0;

        private long bytesTransferred = 0;

        /**
         * Called when a dataset is complete.
         */
        public void notifySuccess(String datasetName) {
            assert (datasetName != null && datasetName.length() > 0);
            System.out.println(TagFilerProperties
                    .getProperty("tagfiler.message.upload.DatasetSuccess"));

            final StringBuffer buff = new StringBuffer(tagFilerServerURL)
                    .append(TagFilerProperties.getProperty(
                            "tagfiler.url.UploadSuccess",
                            new String[] { datasetName }));
            redirect(buff.toString());
        }

        public void notifyFailure(String datasetName, int code) {
            assert (datasetName != null && datasetName.length() > 0);
            String message = TagFilerProperties
                    .getProperty("tagfiler.message.upload.DatasetFailure");
            if (code != -1) {
                message += " (Status Code: " + code + ").";
            }
            try {
                message = DatasetUtils.urlEncode(message);
            } catch (UnsupportedEncodingException e) {
                // just pass the unencoded message
            }
            final StringBuffer buff = new StringBuffer(tagFilerServerURL)
                    .append(TagFilerProperties.getProperty(
                            "tagfiler.url.UploadFailure", new String[] {
                                    datasetName, message }));
            redirect(buff.toString());

        }

        public void notifyFailure(String datasetName) {
            notifyFailure(datasetName, -1);

        }

        public void notifyFailure(String datasetName, String err) {
            notifyFailure(datasetName, -1);

        }

        public void notifyLogMessage(String message) {
            assert (message != null);
            System.out.println(message);
        }

        public void notifyStart(String datasetName, long totalSize) {
            assert (datasetName != null && datasetName.length() > 0);
            totalFiles = filesToUpload.size();
            totalBytes = totalSize + totalFiles;
            filesCompleted = 0;

            updateStatus(TagFilerProperties
                    .getProperty(
                            "tagfiler.message.upload.FileTransferStatus",
                            new String[] { Integer.toString(0),
                                    Integer.toString(totalFiles) }));
        }

        /**
         * Called when a file transfer starts
         */
        public void notifyFileTransferStart(String filename) {
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.message.upload.FileTransferStatus",
                    new String[] { Integer.toString(filesCompleted + 1),
                            Integer.toString(totalFiles) }));
            System.out.println("Transferring " + filename + "...");
        }

        /**
         * Called when a file transfer completes
         */
        public void notifyFileTransferComplete(String filename, long size) {
            filesCompleted++;
            
            bytesTransferred += size + 1;
            long percent = bytesTransferred * 100 / totalBytes;
            try {
                JSObject window = (JSObject) JSObject.getWindow(
                		TagFilerUploadApplet.this);

                window.eval("drawProgressBar(" + percent + ")");
            } catch (JSException e) {
                // don't throw, but make sure the UI is unuseable
            	e.printStackTrace();
            }

            if (filesCompleted < totalFiles) {
                updateStatus(TagFilerProperties.getProperty(
                        "tagfiler.message.upload.FileTransferStatus",
                        new String[] { Integer.toString(filesCompleted + 1),
                                Integer.toString(totalFiles) }));
            }
        }

        /**
         * Called if a file is skipped and not transferred
         */
        public void notifyFileTransferSkip(String filename) {
            filesCompleted++;
        }

        /**
         * Called when a transmission number update starts
         */
        public void notifyUpdateStart(String filename) {
        }

        /**
         * Called when a transmission number update completes
         */
        public void notifyUpdateComplete(String filename) {
        }

        /**
         * Called when retrieving files starts
         * @param size
         *            number of files to be retrieved.
         */
        public void notifyRetrieveStart(int size) {
        }

        /**
         * Called when the retrieving of a file completed
         * @param name
         *            the retrieved file.
         */
        public void notifyFileRetrieveComplete(String filename) {
        }

        public void notifyError(Throwable e) {
            String message = TagFilerProperties.getProperty(
                    "tagfiler.message.upload.Error", new String[] { e
                            .getClass().getCanonicalName() });
            try {
                message = DatasetUtils.urlEncode(message);
            } catch (UnsupportedEncodingException f) {
                // just use the unencoded message
            }

            StringBuffer buff = new StringBuffer(tagFilerServerURL)
                    .append(TagFilerProperties.getProperty(
                            "tagfiler.url.GenericFailure",
                            new String[] { message }));

            redirect(buff.toString());

        }
        
        public void notifyFatal(Throwable e) {
            String message = TagFilerProperties.getProperty(
                    "tagfiler.message.upload.Error", new String[] { e
                            .getClass().getCanonicalName() });
            try {
                message = DatasetUtils.urlEncode(message);
            } catch (UnsupportedEncodingException f) {
                // just use the unencoded message
            }

            StringBuffer buff = new StringBuffer(tagFilerServerURL)
                    .append(TagFilerProperties.getProperty(
                            "tagfiler.url.GenericFailure",
                            new String[] { message }));

            redirect(buff.toString());

        }
        
}

    /**
     * Convenience method for updating the status label
     * 
     * @param status
     */
    private void updateStatus(String status) {
        updateStatus(status, fontColor);
        try {
            JSObject window = (JSObject) JSObject.getWindow(
            		TagFilerUploadApplet.this);

            window.eval("setStatus('" + status + "')");
        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
        	e.printStackTrace();
        }

    }

    /**
     * Convenience method for updating the status label with a font color
     * 
     * @param status
     * @param c
     *            the font color
     */
    private void updateStatus(String status, Color c) {
    }

    /**
     * @return true if the custom fields that are editable by the user are all
     *         valid.
     */
    public void setCustomTags() throws Exception {
        try {
            JSObject window = (JSObject) JSObject.getWindow(
                    this);

            String tags = (String) window.eval("getTags()");
            String customTag[] = tags.split("<br/>");
            for (int i=0; i < customTag.length; i+=2) {
            	customTagMap.setValue(customTag[i], i < customTag.length - 1 ? customTag[i+1] : "");
            }

        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
        	e.printStackTrace();
        }
    }

    /**
     * @return the component representing this UI
     */
    public void uploadAll() {
        synchronized (lock) {
        	upload = true;
        	lock.notifyAll();
        }
    }

    /**
     * @return the component representing this UI
     */
    public boolean validateDate(String date) {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			sdf.setLenient(false);
			sdf.parse(date);
			return true;
		}
		catch (Throwable e) {
			e.printStackTrace();
			return false;
		}
    }

    /**
     * @return the component representing this UI
     */
    public Component getComponent() {
        return getContentPane();
    }

    /**
     * Clears all user-editable fields
     */
    public void clearFields() {
        customTagMap.clearValues();
    }

    /**
     * Puts the UI in a state where it is no longer active
     */
    public void deactivate() {
        addBtn.setEnabled(false);
    }

    /**
     * Destroys the applet
     */
    public void destroy() {
        super.destroy();
    }

    /**
     * @return the FileUpload object
     */
    public FileTransfer getFileTransfer() {
        return fileUpload;
    }
    
    private class TestTimerTask extends TimerTask {

    	public void run() {
    		Set<String> tags = customTagMap.getTagNames();
    		for (String tag : tags) {
    			String value = testProperties.getProperty(tag, "null");
    			customTagMap.setValue(tag, value);
    		}
    		fileChooser.setSelectedFile(new File(testProperties.getProperty("Source Directory", "null")));
    		addBtn.doClick();
        }
    }

    private class UploadTask extends TimerTask {

    	public void run() {
    		synchronized (lock) {
    			while (!stopped) {
            		while (!upload) {
            			try {
            				lock.wait();
        				} catch (InterruptedException e) {
        					// TODO Auto-generated catch block
        					e.printStackTrace();
        				}
            		}
            		if (upload) {
            			upload = false;
            	        try {
            	        	setCustomTags();
            	            if (filesToUpload.size() > 0) {
            	                deactivate();
            	                List<String> files = new LinkedList<String>();
            	                for (int i = 0; i < filesToUpload.size(); i++) {
            	                    files.add((String) filesToUpload.get(i));
            	                }
            	                fileUpload.postFileData(files);
            	            }
            	        } catch (Exception ex) {
            	            JOptionPane.showMessageDialog(getComponent(),
            	                    ex.getMessage());
            	            getComponent().requestFocusInWindow();
            	        }
            		}
    			}
    		}
        }
    }

}
