package edu.isi.misd.tagfiler.upload;

import java.io.File;
import java.util.List;

import edu.isi.misd.tagfiler.AbstractFileTransferSession;
import edu.isi.misd.tagfiler.AbstractTagFilerApplet;
import edu.isi.misd.tagfiler.TagFilerUploadApplet;
import edu.isi.misd.tagfiler.client.ClientURLListener;
import edu.isi.misd.tagfiler.client.ClientURLResponse;
import edu.isi.misd.tagfiler.client.ConcurrentJakartaClient;
import edu.isi.misd.tagfiler.exception.FatalException;
import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Default implementation of the {@link edu.isi.misd.tagfiler.upload.FileUpload}
 * interface.
 * 
 * @author David Smith
 * 
 */
public class FileUploadImplementation extends AbstractFileTransferSession
        implements FileUpload, ClientURLListener {

    // listener for file upload progress
    private final FileUploadListener fileUploadListener;

    // base directory to use
    private String baseDirectory = "";

    // the applet
    private TagFilerUploadApplet applet = null;
    
    // mutex to block thread execution until all files were uploaded
    private Object lock = new Object();

    /**
     * Constructs a new file upload
     * 
     * @param url
     *            tagfiler server url
     * @param l
     *            listener for file upload progress
     * @param tagMap
     *            map of the custom tags
     * @param c
     *            session cookie
     */
    public FileUploadImplementation(String url, FileUploadListener l,
				    CustomTagMap tagMap, String c, TagFilerUploadApplet a) {
        if (url == null || url.length() == 0 ||
        		l == null || tagMap == null) 
        	throw new IllegalArgumentException(""+url+", "+l+", "+tagMap);

        tagFilerServerURL = url;
        fileUploadListener = l;
        customTagMap = tagMap;
        cookie = c;
	applet = a;
	boolean allowChunks = ((AbstractTagFilerApplet) applet).allowChunksTransfering();
    client = new ConcurrentJakartaClient(allowChunks ? ((AbstractTagFilerApplet) applet).getMaxConnections() : 2, ((AbstractTagFilerApplet) applet).getSocketBufferSize(), this);
    client.setChunked(allowChunks);
    client.setChunkSize(((AbstractTagFilerApplet) applet).getChunkSize());
    }

    /**
     * Sets the base directory of the upload.
     * 
     * @param baseDir
     *            base directory
     */
    public void setBaseDirectory(String baseDir) {
        if (baseDir == null) throw new IllegalArgumentException(baseDir);
        baseDirectory = baseDir;
        applet.eval("setDestinationDirectory", baseDir.replaceAll("\\\\", "\\\\\\\\"));
    }

    /**
     * Sets the files to be uploaded on the Web Page.
     * 
     * @param filesList
     *            the list of files
     */
    public void addFilesToList(List<String> filesList) {
        if (filesList == null) throw new IllegalArgumentException(""+filesList);
        StringBuffer buffer = new StringBuffer();
        for (String file : filesList) {
        	buffer.append(file).append("<br/>");
        }
        
        if (buffer.length() > 0) {
        	buffer.setLength(buffer.length() - "<br/>".length());
        }
        applet.eval("setFiles", buffer.toString().replaceAll("\\\\", "\\\\\\\\"));

    }

    /**
     * Performs the file upload.
     * 
     * @param files
     *            list of absolute file names.
     */
    public boolean postFileData(List<String> files) {
        if (files == null) throw new IllegalArgumentException(""+files);
        boolean result = false;
        try {
            result = postFileData(files, getTransmitNumber());
        } catch (Exception e) {
            e.printStackTrace();
            fileUploadListener.notifyError(e);
            result = false;
        }
        return result;
    }

    /**
     * Makes a web server access to get a transmission number.
     */
    private String getTransmitNumber() throws FatalException {
        String ret = "";
        String query = tagFilerServerURL + "/transmitnumber";
        ClientURLResponse response = client.getTransmitNumber(query, cookie);

        if (response == null) {
        	notifyFailure("Error: NULL response in getting a transmission number for the study.");
        	return null;
        }
        synchronized (this) {
            cookie = client.updateSessionCookie(applet, cookie);
        }

        if (200 == response.getStatus()) {
            ret = response.getLocationString();
        } else {
            fileUploadListener
                    .notifyLogMessage("Error getting a transmission number (code="
                            + response.getStatus() + ")");
            throw new FatalException(
                    TagFilerProperties
                            .getProperty("tagfiler.message.upload.ControlNumberError"));
        }

        response.release();

        return ret;
    }

    /**
     * Convenience method for computing checksums and totaling the file transfer
     * size.
     * 
     * @param files
     *            list of the files to transfer.
     * @throws FatalException
     *             if a fatal exception occurs when computing checksums
     */
    private void buildTotalSize(List<String> files)
            throws FatalException {
        if (files == null) throw new IllegalArgumentException(""+files);

        File file = null;
        long fileSize = 0;
        datasetSize = 0;
        for (String filename : files) {
            file = new File(filename);
            if (file.exists() && file.canRead()) {

                if (file.isFile()) {
                    fileSize = file.length();
                    datasetSize += fileSize;
                } else if (file.isDirectory()) {
                    // do nothing
                } else {
                    fileUploadListener.notifyLogMessage("File '" + filename
                            + "' is not a regular file -- skipping.");
                }
            } else {
                fileUploadListener.notifyLogMessage("File '" + filename
                        + "' is not readible or does not exist.");
            }
        }
    }

    /**
     * Uploads a set of given files with a specified dataset name.
     * 
     * @param files
     *            list of files to upload.
     * @param datasetName
     *            defined name of the dataset.
     * @return true if the file transfer was a success
     */
    public boolean postFileData(List<String> files, String datasetName) {
        if (files == null ||
        		datasetName == null || datasetName.length() == 0) throw new IllegalArgumentException(datasetName+", "+files);
        this.dataset = datasetName;

        boolean success = false;
        ClientURLResponse response = null;

        // retrieve the amount of total bytes, checksums for each file
        fileUploadListener
                .notifyLogMessage("Computing size and checksum of files...");
        try {
        	buildTotalSize(files);
            fileUploadListener.notifyStart(datasetName, (enableChecksum ? 2 : 1)*datasetSize);
            fileUploadListener.notifyLogMessage(datasetSize
                    + " total bytes will be transferred");

            fileUploadListener
                    .notifyLogMessage("Beginning transfer of dataset '"
                            + datasetName + "'...");

            // upload all the files
            long t2 = 0;
            synchronized (lock) {
        	    success = postFileDataHelper(files, datasetName);
                t2 = System.currentTimeMillis();
        	    lock.wait();
            }
            
            if (cancel) {
            	success = false;
            	return success;
            }

            long t1 = System.currentTimeMillis();
            System.out.println("Upload time: " + (t1-t2) + " ms.");
            System.out.println("Upload rate: " + DatasetUtils.roundTwoDecimals(((double) datasetSize)/1000/(t1-t2)) + " MB/s.");
            // then create and tag the dataset url entry
            String datasetURLQuery = DatasetUtils
                    .getDatasetURLUploadQuery(datasetName, tagFilerServerURL,
                            customTagMap);
            String datasetBody = DatasetUtils.getDatasetURLUploadBody(
                    datasetName, tagFilerServerURL);
            
            fileUploadListener.notifyLogMessage("Creating dataset URL entry.");
            fileUploadListener.notifyLogMessage("Query: " + datasetURLQuery
                    + "\nBody:" + datasetBody);
            
            applet.updateStatus(TagFilerProperties.getProperty("tagfiler.label.CompleteUploadStatus"));
            t1 = System.currentTimeMillis();
            response = client.postFileData(datasetURLQuery, datasetBody, cookie);
            t2 = System.currentTimeMillis();
            System.out.println("Elapsed time: " + (t2-t1) + " ms.");
            
            if (response == null) {
            	notifyFailure("Error: NULL response in creating dataset URL entry.");
            	success = false;
            	return success;
            }
            synchronized (this) {
                cookie = client.updateSessionCookie(applet, cookie);
            }

            // check result
            if (200 != response.getStatus() && 303 != response.getStatus()) {
                fileUploadListener
                .notifyLogMessage("Error creating the dataset URL entry (code="
                        + response.getStatus() + ")");
		        success = false;
		        fileUploadListener.notifyFailure(datasetName, response.getStatus(), response.getErrorMessage());
		        return success;
            }
            
            response.release();
            
            // Register the dataset files
            datasetURLQuery = DatasetUtils
            	.getDatasetURLUploadQuery(datasetName, tagFilerServerURL);
            datasetBody = DatasetUtils.getDatasetURLUploadBody(
                    datasetName, tagFilerServerURL, files, baseDirectory);
            
            fileUploadListener.notifyLogMessage("Registering dataset files.");
            fileUploadListener.notifyLogMessage("Query: " + datasetURLQuery
                    + "\nBody:" + datasetBody);
            
            t1 = System.currentTimeMillis();
            response = client.putFileData(datasetURLQuery, datasetBody, cookie);
            t2 = System.currentTimeMillis();
            System.out.println("Elapsed time: " + (t2-t1) + " ms.");
            if (response == null) {
            	notifyFailure("Error: NULL response in registering dataset files.");
            	success = false;
            	return success;
            }
            
            synchronized (this) {
                cookie = client.updateSessionCookie(applet, cookie);
            }

            // successful tagfiler POST issues 303 redirect to result page
            if (200 == response.getStatus() || 303 == response.getStatus()) {
                fileUploadListener.notifyLogMessage("Dataset URL entry created successfully.");
        		fileUploadListener.notifySuccess(dataset);
            } else {
                fileUploadListener
                        .notifyLogMessage("Error creating the dataset URL entry (code="
                                + response.getStatus() + ")");
                success = false;
                fileUploadListener.notifyFailure(datasetName, response.getStatus(), response.getErrorMessage());
            }
        } catch (Exception e) {
            // notify the UI of any uncaught errors
            e.printStackTrace();
            fileUploadListener.notifyError(e);
        } finally {
        	if (response != null) {
            	response.release();
        	}
        }
        return success;
    }

    /**
     * Helper method for transferring files.
     * 
     * @param files
     *            list of the files
     * @param datasetName
     *            name of the dataset
     * @return true if the file tranfer was a success
     * @throws FatalException
     *             if an error occurred in one of the file transfers
     */
    private boolean postFileDataHelper(List<String> files, String datasetName)
            throws FatalException {
        if (datasetName == null || datasetName.length() == 0 ||
        		files == null) 
        	throw new IllegalArgumentException(""+datasetName+", "+files);

        client.setBaseURL(DatasetUtils.getBaseUploadQuery(datasetName, tagFilerServerURL));
        client.upload(files, baseDirectory);
        return true;
    }

    /**
     * Callback to get the cookie.
     * 
     * @return the cookie or null if cookies are not used
     */
	public String getCookie() {
		// TODO Auto-generated method stub
		return cookie;
	}

	/**
	 * Callback to notify a chunk block transfer completion during the upload/download process
	 * 
	 * @param size
	 *            the chunk size
	 */
	public void notifyChunkTransfered(long size) {
		// TODO Auto-generated method stub
		fileUploadListener.notifyChunkTransfered(false, size);
	}

	/**
	 * Callback to notify an error during the upload/download process
	 * 
	 * @param err
	 *            the error message
	 * @param e
	 *            the exception
	 */
	public void notifyError(String err, Exception e) {
		// TODO Auto-generated method stub
		fileUploadListener.notifyFailure(dataset, err);
	}

	/**
	 * Callback to notify a failure during the upload/download process
	 * 
	 * @param err
	 *            the error message
	 */
	public void notifyFailure(String err) {
		// TODO Auto-generated method stub
		boolean notify = false;
		synchronized (lock) {
			if (!cancel) {
				cancel = true;
				notify = true;
				lock.notifyAll();
			}
		}
		if (notify) {
			fileUploadListener.notifyFailure(dataset, err);
		}
	}

	/**
	 * Callback to notify a file transfer completion during the upload/download process
	 * 
	 * @param size
	 *            the chunk size
	 */
	public void notifyFileTransfered(long size) {
		// TODO Auto-generated method stub
		fileUploadListener.notifyChunkTransfered(true, size);
	}

	/**
	 * Callback to notify success for the entire upload/download process
	 * 
	 */
	public void notifySuccess() {
		// TODO Auto-generated method stub
		synchronized (lock) {
			lock.notifyAll();
		}
	}

	/**
	 * Callback to update the session cookie. Should have an empty body if cookies are not used.
	*/
	public void updateSessionCookie() {
		// TODO Auto-generated method stub
		long t = System.currentTimeMillis();
		if ((t - lastCookieUpdate) >= cookieUpdatePeriod) {
			lastCookieUpdate = t;
	        cookie = client.updateSessionCookie(applet, cookie);
		}
	}

}

