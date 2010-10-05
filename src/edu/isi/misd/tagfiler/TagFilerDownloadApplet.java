package edu.isi.misd.tagfiler;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import netscape.javascript.JSException;
import netscape.javascript.JSObject;

import edu.isi.misd.tagfiler.download.FileDownload;
import edu.isi.misd.tagfiler.download.FileDownloadImplementation;
import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.ui.CustomTagMapImplementation;
import edu.isi.misd.tagfiler.ui.FileDownloadSelectDestinationDirectoryListener;
import edu.isi.misd.tagfiler.ui.FileDownloadUI;
import edu.isi.misd.tagfiler.upload.FileUploadListener;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.TagFilerProperties;
import edu.isi.misd.tagfiler.util.TagFilerPropertyUtils;

/**
 * Applet class that is used for downloading a set of files from a tagfiler
 * server to a specified destination directory.
 * 
 * @param tagfiler
 *            .server.url Required parameter for the applet that specifies the
 *            URL of the tagfiler server and path to connect to (i.e.
 *            https://tagfiler.isi.edu:443/tagfiler)
 * @param tagfiler
 *            .control.number (optional) transmission number used to retrieve the
 *            files
 * 
 * @author David Smith
 * 
 */
public final class TagFilerDownloadApplet extends AbstractTagFilerApplet
        implements
        FileDownloadUI {

    private static final long serialVersionUID = 2134123;

    private static final String TAGFILER_CONTROL_NUM_PARAM = "tagfiler.server.transmissionnum";

    // parameters referenced from the applet.properties file
    private static final String FONT_NAME_PROPERTY = "tagfiler.font.name";

    private static final String FONT_STYLE_PROPERTY = "tagfiler.font.style";

    private static final String FONT_SIZE_PROPERTY = "tagfiler.font.size";

    private static final String FONT_COLOR_PROPERTY = "tagfiler.font.color";

    private JButton selectDirBtn = null;

    private StringBuffer destinationDirectoryField = new StringBuffer();

    private String defaultControlNumber = null;

    private List<String> filesToDownload = null;

    private JFileChooser fileChooser = null;

    // does the work of the file download
    private FileDownload fileDownload = null;

    // font, color used in the applet
    private Color fontColor;

    private Font font;
    
    private boolean started;

    // map containing the names and values of custom tags
    private CustomTagMap customTagMap = null;

    private boolean downloadStudy;

    private Timer filesTimer;

    private boolean download;

    private Object lock= new Object();

    private class EventTimerTask extends TimerTask {

    	
    	public void run() {
    		downloadStudy = true;
        }
    }

    /**
     * Initializes the applet by reading parameters, polling the tagfiler
     * servlet to retrieve any authentication requests, and constructing the
     * applet UI.
     */
    public void init() {

        super.init();

        defaultControlNumber = this.getParameter(TAGFILER_CONTROL_NUM_PARAM);
        if (defaultControlNumber == null) {
            defaultControlNumber = "";
        }

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
        started = true;
    	filesTimer = new Timer(true);
    	filesTimer.schedule(new DownloadTask(), 1000);
        	if (defaultControlNumber.length() > 0)
        	{
            	filesTimer.schedule(new EventTimerTask(), 1000);
        	} else if (testMode) {
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

        selectDirBtn = new JButton(
                TagFilerProperties
                        .getProperty("tagfiler.button.Browse"));

        filesToDownload = new ArrayList<String>();

        final JLabel selectDestinationLabel = createLabel(TagFilerProperties
                .getProperty("tagfiler.label.SelectDestinationDir"));

        selectDestinationLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        selectDirBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        disableSelectDirectory();

        final JPanel top = createPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setAlignmentX(Component.CENTER_ALIGNMENT);
        top.setAlignmentY(Component.TOP_ALIGNMENT);

        top.add(selectDestinationLabel, Component.CENTER_ALIGNMENT);
        top.add(selectDirBtn, Component.CENTER_ALIGNMENT);
        top.validate();

        customTagMap = new CustomTagMapImplementation();

        // file chooser window
        fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(TagFilerProperties
                .getProperty("tagfiler.filedialog.SelectDestinationDirectory"));
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);

        // the file uploader itself
        // TODO: create container for cookie so that the object reference
        // remains intact when
        // they are replaced
        fileDownload = new FileDownloadImplementation(tagFilerServerURL,
                new TagFilerAppletUploadListener(), sessionCookie,
                customTagMap, this);

        selectDirBtn
                .addActionListener(new FileDownloadSelectDestinationDirectoryListener(
                        this, destinationDirectoryField, fileChooser));

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
     * Enables the download button
     */
    public void enableDownload() {
        try {
            JSObject window = (JSObject) JSObject.getWindow(this);

            window.eval("setDestinationDirectory('" + destinationDirectoryField.toString().trim() + "')");
            window.eval("setEnabled('Download Files')");
        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
        	e.printStackTrace();
        }
        updateStatus(TagFilerProperties
                .getProperty("tagfiler.label.DefaultDownloadStatus"));
    }

    /**
     * Disables the download button
     */
    public void disableDownload() {
        updateStatus(TagFilerProperties
                .getProperty("tagfiler.label.DefaultDestinationStatus"));
    }

    /**
     * Enables the select directory button
     */
    public void enableSelectDirectory() {
        selectDirBtn.setEnabled(true);
    }

    /**
     * Disallows the adding of a directory to be invoked.
     */
    public void disableSelectDirectory() {
        selectDirBtn.setEnabled(false);
    }

    /**
     * Private class that listens for progress from the file download process
     * and takes action on the applet UI based on this progress.
     * 
     * @author David Smith
     * 
     */
    private class TagFilerAppletUploadListener implements FileUploadListener {

        private int filesCompleted = 0;

        private int totalFiles = 0;

        private long totalBytes = 0;

        private long bytesTransferred = 0;

        private int unit = 1;

        /**
         * Called when a dataset is complete.
         */
        public void notifySuccess(String datasetName) {
            System.out.println(TagFilerProperties.getProperty(
                    "tagfiler.message.download.DatasetSuccess",
                    new String[] { datasetName }));

            final StringBuffer buff = new StringBuffer(tagFilerServerURL)
                    .append(TagFilerProperties.getProperty(
                            "tagfiler.url.DownloadSuccess",
                            new String[] { datasetName }));
            redirect(buff.toString());
        }

        public void notifyFailure(String datasetName, String err) {
            String message = TagFilerProperties.getProperty(
                    "tagfiler.message.download.DatasetFailure",
                    new String[] { datasetName });
            if (err != null) {
                message += " " + err + ".";
            }
            try {
                message = DatasetUtils.urlEncode(message);
            } catch (UnsupportedEncodingException e) {
                // just pass the unencoded message
            }
            final StringBuffer buff = new StringBuffer(tagFilerServerURL)
                    .append(TagFilerProperties.getProperty(
                            "tagfiler.url.DownloadFailure", new String[] {
                                    datasetName, message }));
            redirect(buff.toString());
        }

        public void notifyFailure(String datasetName, int code) {
            assert (datasetName != null && datasetName.length() > 0);
            String message = TagFilerProperties.getProperty(
                    "tagfiler.message.download.DatasetFailure",
                    new String[] { datasetName });
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
                            "tagfiler.url.DownloadFailure", new String[] {
                                    datasetName, message }));
            redirect(buff.toString());

        }

        public void notifyFailure(String datasetName) {
            notifyFailure(datasetName, -1);
        }

        public void notifyLogMessage(String message) {
            System.out.println(message);
        }

        public void notifyStart(String datasetName, long totalSize) {

            totalFiles = filesToDownload.size();
            totalBytes = totalSize + totalFiles;
            filesCompleted = 0;

            // if the size of the transfer is beyond the integer max value,
            // make sure we divide the total and increments so that they fit in
            // the progress bar's integer units
            while ((totalBytes / unit) >= Integer.MAX_VALUE) {
                unit *= 10;
            }

            long percent = 0;
            try {
                JSObject window = (JSObject) JSObject.getWindow(
                		TagFilerDownloadApplet.this);

                window.eval("drawProgressBar(" + percent + ")");
            } catch (JSException e) {
                // don't throw, but make sure the UI is unuseable
            	e.printStackTrace();
            }

        }

        /**
         * Called when a transmission number update starts
         */
        public void notifyUpdateStart(String filename) {
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.label.UpdateDownloadStatus",
                    new String[] { }));
            System.out.println("Updating transmission number " + filename + "...");
        }

        /**
         * Called when retrieving files starts
         * @param size
         *            number of files to be retrieved.
         */
        public void notifyRetrieveStart(int size) {
            while ((size / unit) >= Integer.MAX_VALUE) {
                unit *= 10;
            }

            totalFiles = size;
            filesCompleted = 0;
            long percent = 0;
            try {
                JSObject window = (JSObject) JSObject.getWindow(
                		TagFilerDownloadApplet.this);

                window.eval("drawProgressBar(" + percent + ")");
            } catch (JSException e) {
                // don't throw, but make sure the UI is unuseable
            	e.printStackTrace();
            }
            
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.message.download.FileRetrieveStatus",
                    new String[] { Integer.toString(filesCompleted + 1),
                            Integer.toString(totalFiles) }));
            
            System.out.println("Start retrieving " + size + " files.");
        }

        /**
         * Called when the retrieving of a file completed
         * @param name
         *            the retrieved file.
         */
        public void notifyFileRetrieveComplete(String filename) {
        	filesToDownload.add(filename);
        	filesCompleted++;
            long percent = filesCompleted * 100 / totalFiles;
            try {
                JSObject window = (JSObject) JSObject.getWindow(
                		TagFilerDownloadApplet.this);

                window.eval((filesCompleted == 1 ? "setFiles('" : "addFile('") + filename + "')");
                window.eval("drawProgressBar(" + percent + ")");
            } catch (JSException e) {
                // don't throw, but make sure the UI is unuseable
            	e.printStackTrace();
            }
            if (filesCompleted < totalFiles) {
                updateStatus(TagFilerProperties.getProperty(
                        "tagfiler.message.download.FileRetrieveStatus",
                        new String[] { Integer.toString(filesCompleted + 1),
                                Integer.toString(totalFiles) }));
            }
        }

        /**
         * Called when a transmission number update completes
         */
        public void notifyUpdateComplete(String filename) {
            long percent = 0;
            try {
                JSObject window = (JSObject) JSObject.getWindow(
                		TagFilerDownloadApplet.this);

                window.eval("drawProgressBar(" + percent + ")");
            } catch (JSException e) {
                // don't throw, but make sure the UI is unuseable
            	e.printStackTrace();
            }
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.label.DefaultDestinationStatus",
                    new String[] { }));
            System.out.println("Completed updating transmission number " + filename + "...");
        }

        /**
         * Called when a file transfer starts
         */
        public void notifyFileTransferStart(String filename) {
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.message.download.FileTransferStatus",
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
                		TagFilerDownloadApplet.this);

                window.eval("drawProgressBar(" + percent + ")");
            } catch (JSException e) {
                // don't throw, but make sure the UI is unuseable
            	e.printStackTrace();
            }
            if (filesCompleted < totalFiles) {
                updateStatus(TagFilerProperties.getProperty(
                        "tagfiler.message.download.FileTransferStatus",
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

        public void notifyError(Throwable e) {
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.message.download.Error", new String[] { e
                            .getClass().getCanonicalName() }));
        }
        
        public void notifyFatal(Throwable e) {
            String message = TagFilerProperties.getProperty(
                    "tagfiler.message.download.Error", new String[] { e
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
    	updateStatus(status, false);
    }

    /**
     * Convenience method for updating the status label
     * 
     * @param status
     */
    private void updateStatus(String status, boolean paint) {
        try {
            JSObject window = (JSObject) JSObject.getWindow(this);

            window.eval("setStatus('" + status + "')");
        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
        	e.printStackTrace();
        }
    	
    	if (paint) {
        	//statusLabel.paintImmediately(statusLabel.getVisibleRect());
    	}
    }

    /**
     * @return 1 if the custom fields that are editable by the user are all valid
     *         0 if the destination directory is not empty and the user has canceled the download.
     *        -1 if some fields are not filled.
     */
    public int validateFields() {
        int valid = 1;

        if (destinationDirectoryField.toString().trim().length() == 0) {
            valid = -1;
        }
        else if (!testMode) {
        	// check the directory is empty to prevent overwriting
        	File dir = new File(destinationDirectoryField.toString().trim());
        	if (dir.list().length > 0) {
        		int res = JOptionPane.showConfirmDialog(getComponent(),
        			    "The destination directory is not empty.\n"
        			    + "It might overwrite some files.\n"
        			    + "Do you want to continue?",
        	            "Warning", JOptionPane.YES_NO_OPTION);
        		valid = (res == JOptionPane.YES_OPTION) ? 1 : 0;
        	}
        }
        return valid;
    }

    /**
     * @return the component representing this UI
     */
    public void downloadFiles() {
        synchronized (lock) {
        	download = true;
        	lock.notifyAll();
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
        filesToDownload.clear();
        customTagMap.clearValues();
    }

    /**
     * Deactivates the applet controls
     */
    public void deactivate() {
        selectDirBtn.setEnabled(false);
    }

    /**
     * @return the FileDownload object
     */
    public FileTransfer getFileTransfer() {
        return fileDownload;
    }
    
    public boolean isDownloadStudy() {
    	return downloadStudy;
    }
    
    public void getDatasetInfo(String controlNumber, String tags) {
    	String name[] = tags.split("<br/>");
        //customTagMap.clearValues();
        defaultControlNumber = controlNumber;
        //for (int i=0; i < name.length; i++) {
        //	customTagMap.setValue(name[i], "");
        //}

        // make sure the transmission number exists
    	StringBuffer errorMessage = new StringBuffer();
    	StringBuffer status = new StringBuffer();
    	while (!started) {
    		try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
        if (fileDownload.verifyValidControlNumber(controlNumber, status, errorMessage)) {
            final List<String> fileList = fileDownload
                    .getFiles(controlNumber);

            if (fileList.size() > 0) {
                enableSelectDirectory();
            }

        } else {
            JOptionPane
                    .showMessageDialog(
                            getComponent(),
                    TagFilerProperties.getProperty(
                            "tagfiler.dialog.InvalidControlNumber",
                            new String[] { controlNumber, errorMessage.toString() }),
                            status.toString(), JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private class TestTimerTask extends TimerTask {

    	
    	public void run() {
    		defaultControlNumber = testProperties.getProperty("Control Number", "null");
    		
    		while (!selectDirBtn.isEnabled() && destinationDirectoryField.toString().trim().length() == 0) {
    			try {
    				Thread.sleep(1000);
    			} catch (InterruptedException e) {
				}
    		}
    		
    		destinationDirectoryField.append(testProperties.getProperty("Destination Directory", "null"));
        }
    }

    private class DownloadTask extends TimerTask {

    	public void run() {
    		synchronized (lock) {
        		while (!download) {
        			try {
        				lock.wait();
    				} catch (InterruptedException e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    				}
        		}
        		if (download) {
        			download = false;
        	        int valid = validateFields();
        	        if (valid == 1) {
                        fileDownload.downloadFiles(destinationDirectoryField.toString().trim());
        	        } else if (valid == -1) {
        	            JOptionPane.showMessageDialog(getComponent(),
        	                    TagFilerProperties
        	                            .getProperty("tagfiler.dialog.FieldsNotFilled"));
        	            getComponent().requestFocusInWindow();
        	        }
        		}
    		}
        }
    }

}
