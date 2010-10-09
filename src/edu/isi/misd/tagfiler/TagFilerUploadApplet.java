package edu.isi.misd.tagfiler;

import java.io.File;
import java.io.FilenameFilter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import netscape.javascript.JSException;
import netscape.javascript.JSObject;

import edu.isi.misd.tagfiler.ui.FileUploadUI;
import edu.isi.misd.tagfiler.upload.FileUpload;
import edu.isi.misd.tagfiler.upload.FileUploadImplementation;
import edu.isi.misd.tagfiler.upload.FileUploadListener;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.TagFilerProperties;

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

    // does the work of the file upload
    private FileUpload fileUpload = null;

    // true if an upload request was issued
    private boolean upload;

    // files to upload
    private List<String> filesList = new ArrayList<String>();
    
    /**
     * Excludes "." and ".." from directory lists in case the client is
     * UNIX-based.
     */
    private static final FilenameFilter excludeDirFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return (!name.equals(".") && !name.equals(".."));
        }
    };

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

    /**
     * Start the threads
     * Enable the HTML buttons
     */
	public void start() {
        super.start();
        
    	filesTimer = new Timer(true);
    	filesTimer.schedule(new UploadTask(), 1000);
    	enableAdd();
    	
        if (testMode) {
        	filesTimer.schedule(new TestTimerTask(), 1000);
        }
    }

    /**
     * Create the applet UI.
     */
    protected void createUI() {

        super.createUI();

        fileChooser.setDialogTitle(TagFilerProperties
                .getProperty("tagfiler.filedialog.SelectDirectoryToUpload"));
        
        // the file uploader itself
        fileUpload = new FileUploadImplementation(tagFilerServerURL,
                new TagFilerAppletUploadListener(), customTagMap,
                sessionCookie, this);
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
     * Allows the adding of a directory to be invoked.
     */
    public void enableAdd() {
        try {
            JSObject window = (JSObject) JSObject.getWindow(
            		TagFilerUploadApplet.this);

            window.eval("setEnabled('Browse')");
        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
        	e.printStackTrace();
        }

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

        /**
         * Called when a failure occurred.
         */
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

        /**
         * Called when a failure occurred.
         */
        public void notifyFailure(String datasetName) {
            notifyFailure(datasetName, -1);
        }

        /**
         * Called when a failure occurred.
         */
        public void notifyFailure(String datasetName, String err) {
        }

        /**
         * Called to log a message.
         */
        public void notifyLogMessage(String message) {
            assert (message != null);
            System.out.println(message);
        }

        /**
         * Called when upload starts.
         */
        public void notifyStart(String datasetName, long totalSize) {
            assert (datasetName != null && datasetName.length() > 0);
            totalFiles = filesList.size();
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

        /**
         * Called when an error occurred
         */
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
        
        /**
         * Called when a fatal error occurred
         */
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
     * set the custom tags values as received from the HTML page
     */
    private void setCustomTags() throws Exception {
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
     * send event for the upload process
     */
    public void uploadAll() {
        synchronized (lock) {
        	upload = true;
        	lock.notifyAll();
        }
    }

    /**
     * send event for selecting the upload directory
     */
    public void browse() {
        synchronized (lock) {
        	browseDir = true;
        	lock.notifyAll();
        }
    }

    /**
     * select the upload directory
     */
    private void chooseDir() {
        int result;
        File fileTest = fileChooser.getSelectedFile();
        if (fileTest != null && fileTest.getName().length() > 0) {
        	result = JFileChooser.APPROVE_OPTION;
        } else {
            result = fileChooser.showOpenDialog(getContentPane());
        }
        if (JFileChooser.APPROVE_OPTION == result) {
            File selectedDirectory = fileChooser.getSelectedFile();
            fileUpload.setBaseDirectory(selectedDirectory.getAbsolutePath());
            filesList.clear();
            addFilesToList(new File[] { selectedDirectory });
            enableUpload();
            fileUpload.addFilesToList(filesList);
        }
        // clear out the selected files, regardless
        fileChooser.setSelectedFiles(new File[] { new File("") });
    }

    /**
     * Adds the files to the list, if an entry is a directory then all its files
     * are added as well.
     * 
     * @param files
     *            the files to add
     */
    private void addFilesToList(File[] files) {
        assert (files != null);
        final List<File> dirs = new LinkedList<File>();
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                dirs.add(files[i].getAbsoluteFile());
            } else if (files[i].isFile()) {
                filesList.add(files[i].getAbsolutePath());
            }
        }

        // go through any directories
        for (File dir : dirs) {
            final File[] children = dir.listFiles(excludeDirFilter);
            if (children != null) {
                addFilesToList(children);
            }
        }
    }
    /**
     * validate a Date value
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
     * Destroys the applet
     */
    public void destroy() {
        super.destroy();
    }

    /**
     * Task to execute a test
     */
    private class TestTimerTask extends TimerTask {

    	public void run() {
    		Set<String> tags = customTagMap.getTagNames();
    		for (String tag : tags) {
    			String value = testProperties.getProperty(tag, "null");
    			customTagMap.setValue(tag, value);
    		}
    		fileChooser.setSelectedFile(new File(testProperties.getProperty("Source Directory", "null")));
        }
    }

    /**
     * Process select upload directory and upload files
     */
    private class UploadTask extends TimerTask {

    	public void run() {
    		synchronized (lock) {
    			while (!stopped) {
            		while (!upload && !browseDir) {
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
            	            if (filesList.size() > 0) {
            	                fileUpload.postFileData(filesList);
            	            }
            	        } catch (Exception ex) {
            	            JOptionPane.showMessageDialog(getComponent(),
            	                    ex.getMessage());
            	            getComponent().requestFocusInWindow();
            	        }
            		} else if (browseDir) {
            			browseDir = false;
            			chooseDir();
            		}
    			}
    		}
        }
    }

}
