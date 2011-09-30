package edu.isi.misd.tagfiler;

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
public class TagFilerUploadApplet extends AbstractTagFilerApplet {

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
    	enableAdd();
    	if (testMode) {
        	filesTimer.schedule(new TestTimerTask(), 1000);
        } else {
        	filesTimer.schedule(new UploadTask(), 1000);
        }
    	
    }

    /**
     * Create the applet UI.
     */
    protected void createUI() {

        super.createUI();

        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setDialogTitle(TagFilerProperties
                .getProperty("tagfiler.filedialog.SelectDirectoryToUpload"));
        
        // the file uploader itself
        fileListener = new TagFilerAppletUploadListener();
        fileUpload = new FileUploadImplementation(tagFilerServerURL,
        		fileListener, customTagMap,
                sessionCookie, this);
    }

    /**
     * Allows the upload action to be invoked.
     */
    public void enableUpload() {
    	setEnabled("Upload All");
    	eval("enableUploadResume()");
        updateStatus(TagFilerProperties
                .getProperty("tagfiler.label.DefaultUploadStatus"));
    }

    /**
     * Allows the adding of a directory to be invoked.
     */
    public void enableAdd() {
    	setEnabled("Browse");

    }

    /**
     * Private class that listens for progress from the file upload process and
     * takes action on the applet UI based on this progress.
     * 
     * @author David Smith
     * 
     */
    private class TagFilerAppletUploadListener extends TagFilerAppletListener implements FileUploadListener {

        /**
         * Called when a dataset is complete.
         */
        public void notifySuccess(String datasetName, int version) {
            if (datasetName == null || datasetName.length() == 0) throw new IllegalArgumentException(datasetName);
            System.out.println(TagFilerProperties
                    .getProperty("tagfiler.message.upload.DatasetSuccess"));

            try {
                datasetName = "name=" + DatasetUtils.urlEncode(datasetName);
                if (version > 0) {
                	datasetName += ";version=" + version;
                }
            } catch (UnsupportedEncodingException e) {
                // just pass the unencoded message
            	e.printStackTrace();
            }
            final StringBuffer buff = new StringBuffer(tagFilerServerURL)
                    .append(TagFilerProperties.getProperty(
                            "tagfiler.url.UploadSuccess",
                            new String[] { datasetName }));
            redirect(buff.toString());
        }

        /**
         * Called when a failure occurred.
         */
        public void notifyFailure(String datasetName, int code, String errorMessage) {
            if (datasetName == null || datasetName.length() == 0) throw new IllegalArgumentException(datasetName);
            super.notifyFailure(TagFilerUploadApplet.this, "tagfiler.message.upload.DatasetFailure", 
            		"tagfiler.url.UploadFailure", datasetName, code, errorMessage);
        }

        /**
         * Called when a failure occurred.
         */
        public void notifyFailure(String datasetName) {
            notifyFailure(datasetName, -1, null);
        }

        public void notifyFailure(String datasetName, String err) {
            if (datasetName == null || datasetName.length() == 0) throw new IllegalArgumentException(datasetName);
            String message = TagFilerProperties
                    .getProperty("tagfiler.message.upload.DatasetFailure");
            if (err != null) {
                message += err;
            }
            try {
                message = DatasetUtils.urlEncode(message);
                datasetName = DatasetUtils.urlEncode(datasetName);
            } catch (UnsupportedEncodingException e) {
                // just pass the unencoded message
            	e.printStackTrace();
            }
            final StringBuffer buff = new StringBuffer(tagFilerServerURL)
                    .append(TagFilerProperties.getProperty(
                            "tagfiler.url.UploadFailure", new String[] {
                                    datasetName, message }));
            redirect(buff.toString());

        }

        /**
         * Called when upload starts.
         */
        public void notifyStart(String datasetName, long totalSize) {
            if (datasetName == null || datasetName.length() == 0) throw new IllegalArgumentException(datasetName);
            totalFiles = filesList.size();
            totalBytes = totalSize + totalFiles;
            filesCompleted = 0;
            lastPercent = 0;
            bytesTransferred = 0;
            drawProgressBar(0);

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
        	super.notifyFileTransferStart(TagFilerUploadApplet.this, "tagfiler.message.upload.FileTransferStatus", filename);
        }

        /**
         * Called when a file transfer completes
         */
        public void notifyFileTransferComplete(String filename, long size) {
        	super.notifyFileTransferComplete(TagFilerUploadApplet.this, "tagfiler.message.upload.FileTransferStatus", filename, size);
        }


        /**
         * Called when a file chunk transfer completes
	     * @param file
	     *            if true, the file transfer completed
	     * @param size
	     *            the chunk size
         */
		public void notifyChunkTransfered(boolean file, long size) {
        	super.notifyChunkTransfered(TagFilerUploadApplet.this, "tagfiler.message.upload.FileTransferStatus", file, size);
		}
		
        /**
         * Called when an error occurred
         */
        public void notifyError(Throwable e) {
            String message = TagFilerProperties.getProperty(
                    "tagfiler.message.upload.Error", new String[] { e
                            .getClass().getCanonicalName() + (e.getMessage() != null ? ". " + e.getMessage() : "") });
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
        	super.notifyFatal(TagFilerUploadApplet.this, "tagfiler.message.upload.Error", e);
        }
        
}

    /**
     * set the custom tags values as received from the HTML page
     */
    private void setCustomTags() throws Exception {
        String tags = eval("getTags()");
        if (tags.length() > 0) {
            String customTag[] = tags.split("<br/>");
            for (int i=0; i < customTag.length; i+=2) {
            	customTagMap.setValue(customTag[i], i < customTag.length - 1 ? customTag[i+1] : "");
            }
        }
  	  }

    /**
     * send event for the upload process
     */
    public void uploadAll(String target) {
    	synchronized (lock) {
        	this.target = target;
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
            if (selectedDirectory.isDirectory()) {
                fileUpload.setBaseDirectory(selectedDirectory.getAbsolutePath());
            } else {
            	String parent = selectedDirectory.getParent();
            	if (parent == null) {
            		parent = "";
            	}
                fileUpload.setBaseDirectory(parent);
            }
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
        if (files == null) throw new IllegalArgumentException("files");
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
    		try {
    			// get the tags from the templates
				setCustomTags();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        Set<String> tags = customTagMap.getTagNames();
	        StringBuffer buffer = new StringBuffer();
	        for (String tag : tags) {
	        	buffer.append(tag).append("<br/>");
	            String value = testProperties.getProperty(tag, "null");
	        	buffer.append(value).append("<br/>");
	        }
	        eval("setInputTags", buffer.toString().replaceAll("'", "\\\\'"));
    		fileChooser.setSelectedFile(new File(testProperties.getProperty("Source Directory", "null")));
    		chooseDir();
        	try {
				setCustomTags();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            if (filesList.size() > 0) {
                fileUpload.postFileData(filesList, target);
            }
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
            	        	fileUpload.setEnableChecksum(getChecksum());
            	        	fileUpload.setDatasetName(getDatasetName());
            	        	setCustomTags();
            	            if (filesList.size() > 0) {
            	                fileUpload.postFileData(filesList, target);
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
