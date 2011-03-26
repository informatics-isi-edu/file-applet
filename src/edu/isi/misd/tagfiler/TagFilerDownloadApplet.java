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
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import edu.isi.misd.tagfiler.download.FileDownload;
import edu.isi.misd.tagfiler.download.FileDownloadImplementation;
import edu.isi.misd.tagfiler.download.FileDownloadListener;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Applet class that is used for downloading a set of files from a tagfiler
 * server to a specified destination directory.
 * 
 * @param tagfiler
 *            .server.url Required parameter for the applet that specifies the
 *            URL of the tagfiler server and path to connect to (i.e.
 *            https://tagfiler.isi.edu:443/tagfiler)
 * @param tagfiler
 *            .control.number (optional) Dataset Name used to retrieve the
 *            files
 * 
 * @author David Smith
 * 
 */
public class TagFilerDownloadApplet extends AbstractTagFilerApplet {

    private static final long serialVersionUID = 2134123;

    private static final String TAGFILER_CONTROL_NUM_PARAM = "tagfiler.server.transmissionnum";

    private static final String TAGFILER_VERSION_PARAM = "tagfiler.server.version";

    // download directory
    private StringBuffer destinationDirectoryField = new StringBuffer();

    // Dataset Name
    private String defaultControlNumber = null;

    // Dataset Version
    private int defaultVersion = 0;

    // download files
    private Set<String> filesToDownload = new HashSet<String>();

    // does the work of the file download
    private FileDownload fileDownload = null;

    // if true, process download
    private boolean download;

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
        } else {
        	String version = this.getParameter(TAGFILER_VERSION_PARAM);
        	if (version != null && version.length() > 0) {
        		defaultVersion = Integer.parseInt(version);
        	}
        }

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    createUI();

                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
            (new TagFilerAppletDownloadListener()).notifyFatal(e);
        }
    }

    /**
     * Start the threads
     * Enable the HTML buttons
     */
	public void start() {
        super.start();
    	filesTimer = new Timer(true);
    	if (testMode) {
        	filesTimer.schedule(new TestTimerTask(), 1000);
    	}
    	else {
        	filesTimer.schedule(new DownloadTask(), 1000);
        	if (defaultControlNumber.length() == 0) {
        		setEnabled("UpdateButton");
        		setEnabled("TransmissionNumber");
        		setEnabled("Version");
        	} else {
        		String tags = eval("getTagsName()");
            	getDatasetInfo(tags);
        	}
    	}
    	
    }

    /**
     * Create the applet UI.
     */
    protected void createUI() {

        super.createUI();

        fileChooser.setDialogTitle(TagFilerProperties
                .getProperty("tagfiler.filedialog.SelectDirectoryToDownload"));
        
        // the file uploader itself
        fileListener = new TagFilerAppletDownloadListener();
        fileDownload = new FileDownloadImplementation(tagFilerServerURL,
        		fileListener, sessionCookie,
                customTagMap, this);
    }

    /**
     * Enables the download button
     */
    public void enableDownload() {
    	setEnabled("Download Files");
    	setEnabled("Resume");
    	eval("setDestinationDirectory", destinationDirectoryField.toString().trim().replaceAll("\\\\", "\\\\\\\\"));
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
    	setEnabled("Browse");
    }

    /**
     * Private class that listens for progress from the file download process
     * and takes action on the applet UI based on this progress.
     * 
     * @author David Smith
     * 
     */
    private class TagFilerAppletDownloadListener extends TagFilerAppletListener implements FileDownloadListener {

        /**
         * Called when a dataset is complete.
         */
        public void notifySuccess(String datasetName, int version) {
            System.out.println(TagFilerProperties.getProperty(
                    "tagfiler.message.download.DatasetSuccess",
                    new String[] { datasetName }));

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
                            "tagfiler.url.DownloadSuccess",
                            new String[] { datasetName }));
            redirect(buff.toString());
        }

        /**
         * Called when a failure occurred.
         */
        public void notifyFailure(String datasetName, String err) {
            String message = TagFilerProperties.getProperty(
                    "tagfiler.message.download.DatasetFailure",
                    new String[] { datasetName });
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
                            "tagfiler.url.DownloadFailure", new String[] {
                                    datasetName, message }));
            redirect(buff.toString());
        }

        /**
         * Called when a failure occurred.
         */
        public void notifyFailure(String datasetName, int code, String errorMessage) {
            if (datasetName == null || datasetName.length() == 0) throw new IllegalArgumentException(datasetName);
        	super.notifyFailure(TagFilerDownloadApplet.this, "tagfiler.message.download.DatasetFailure", 
        			"tagfiler.url.DownloadFailure", datasetName, code, errorMessage);
        }

        /**
         * Called when download starts.
         */
        public void notifyStart(String datasetName, long totalSize) {

            totalFiles = filesToDownload.size();
            totalBytes = totalSize + totalFiles;
            filesCompleted = 0;
            lastPercent = 0;

            drawProgressBar(0);

        }

        /**
         * Called when a Dataset Name update starts
         */
        public void notifyUpdateStart(String filename) {
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.label.UpdateDownloadStatus",
                    new String[] { }));
            System.out.println("Updating Dataset Name " + filename + "...");
        }

        /**
         * Called when retrieving files starts
         * @param size
         *            number of files to be retrieved.
         */
        public void notifyRetrieveStart(int size) {
            totalFiles = size;
            filesCompleted = 0;
            lastPercent = 0;
            
            drawProgressBar(0);
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
            drawProgressBar(percent);
            if (filesCompleted < totalFiles) {
                updateStatus(TagFilerProperties.getProperty(
                        "tagfiler.message.download.FileRetrieveStatus",
                        new String[] { Integer.toString(filesCompleted + 1),
                                Integer.toString(totalFiles) }));
            } else {
            	String fileList = DatasetUtils.join(filesToDownload, "<br/>");
                eval("setFiles", fileList);
            }
        }

        /**
         * Called when a Dataset Name update completes
         */
        public void notifyUpdateComplete(String filename) {
        	drawProgressBar(0);
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.label.DefaultDestinationStatus",
                    new String[] { }));
            System.out.println("Completed updating Dataset Name " + filename + "...");
        }

        /**
         * Called when a file transfer starts
         */
        public void notifyFileTransferStart(String filename) {
        	super.notifyFileTransferStart(TagFilerDownloadApplet.this, "tagfiler.message.download.FileTransferStatus", filename);
        }

        /**
         * Called when a file transfer completes
         */
        public void notifyFileTransferComplete(String filename, long size) {
        	super.notifyFileTransferComplete(TagFilerDownloadApplet.this, "tagfiler.message.download.FileTransferStatus", filename, size);
        }

        /**
         * Called when a file chunk transfer completes
	     * @param file
	     *            if true, the file transfer completed
	     * @param size
	     *            the chunk size
         */
		public void notifyChunkTransfered(boolean file, long size) {
        	super.notifyChunkTransfered(TagFilerDownloadApplet.this, "tagfiler.message.download.FileTransferStatus", file, size);
		}
		
        /**
         * Called when an error occurred
         */
        public void notifyError(Throwable e) {
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.message.download.Error", new String[] { e
                            .getClass().getCanonicalName() + (e.getMessage() != null ? ". " + e.getMessage() : "") }));
        }
        
        /**
         * Called when a fatal error occurred
         */
        public void notifyFatal(Throwable e) {
        	super.notifyFatal(TagFilerDownloadApplet.this, "tagfiler.message.download.Error", e);
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
     * send event for the download process
     */
    public void downloadFiles(String target) {
        synchronized (lock) {
        	this.target = target;
        	download = true;
        	lock.notifyAll();
        }
    }

    /**
     * send event for selecting the download directory
     */
    public void browse() {
        synchronized (lock) {
        	browseDir = true;
        	lock.notifyAll();
        }
    }

    /**
     * select the download directory
     */
    private void chooseDir() {
        int result;
        File fileTest = fileChooser.getSelectedFile();
        if (fileTest != null && fileTest.getName().length() > 0) {
        	result = JFileChooser.APPROVE_OPTION;
        } else {
            result = fileChooser.showOpenDialog(getComponent());
            getComponent().requestFocusInWindow();
        }
        if (JFileChooser.APPROVE_OPTION == result) {
            File selectedDirectory = fileChooser.getSelectedFile();
            destinationDirectoryField.setLength(0);
            destinationDirectoryField.append(selectedDirectory.getAbsolutePath());
            if (destinationDirectoryField.toString().trim().length() > 0) {
            	enableDownload();
            }
        }
        
        // clear out the selected files, regardless
        fileChooser.setSelectedFiles(new File[] { new File("") });
    }

    /**
     * Retrieve the tags and files to be downloaded
     */
    public void getDatasetInfo(String tags) {
    	String name[] = tags.split("<br/>");
        for (int i=0; i < name.length && name[i].trim().length() > 0; i++) {
        	customTagMap.setValue(name[i], "");
        }
        
        // make sure the Dataset Name exists
    	StringBuffer errorMessage = new StringBuffer();
    	StringBuffer status = new StringBuffer();
        if (fileDownload.verifyValidControlNumber(defaultControlNumber, defaultVersion, status, errorMessage)) {
            final List<String> fileList = fileDownload
                    .getFiles(defaultControlNumber, defaultVersion);

            if (fileList.size() > 0) {
                enableSelectDirectory();
            }

        } else {
        	String version = (defaultVersion > 0) ? "@"+defaultVersion : "";
            JOptionPane
                    .showMessageDialog(
                            getComponent(),
                    TagFilerProperties.getProperty(
                            "tagfiler.dialog.InvalidControlNumber",
                            new String[] { defaultControlNumber, version, errorMessage.toString() }),
                            status.toString(), JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Retrieve the tags and files to be downloaded
     * This function is called from JavaScript
     */
    public void getDatasetInfo(String controlNumber, String version, String tags) {
    	defaultControlNumber = controlNumber;
    	defaultVersion = (version != null && version.length() > 0) ? Integer.parseInt(version) : 0;
    	getDatasetInfo(tags);
    }
    
    /**
     * Task to execute a test
     */
    private class TestTimerTask extends TimerTask {

    	
    	public void run() {
    		defaultControlNumber = testProperties.getProperty("Control Number", "null");
    		TagFilerDownloadApplet.this.eval("setTransmissionNumber", defaultControlNumber);
    		String tags = eval("getTagsName()");
        	getDatasetInfo(tags);
        	File dir = new File(testProperties.getProperty("Destination Directory", "null") + "/" + UUID.randomUUID());
        	dir.mkdirs();
    		fileChooser.setSelectedFile(dir);
			chooseDir();
	        int valid = validateFields();
	        if (valid == 1) {
                fileDownload.downloadFiles(destinationDirectoryField.toString().trim(), target);
	        }
    	}
    }

    /**
     * Process select download directory and download files
     */
    private class DownloadTask extends TimerTask {

    	public void run() {
    		synchronized (lock) {
    			while (!stopped) {
            		while (!download && !browseDir) {
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
            	        	fileDownload.setEnableChecksum(getChecksum());
                            fileDownload.downloadFiles(destinationDirectoryField.toString().trim(), target);
            	        } else if (valid == -1) {
            	            JOptionPane.showMessageDialog(getComponent(),
            	                    TagFilerProperties
            	                            .getProperty("tagfiler.dialog.FieldsNotFilled"));
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
