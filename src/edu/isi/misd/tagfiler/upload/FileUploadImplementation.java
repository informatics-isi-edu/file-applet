package edu.isi.misd.tagfiler.upload;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.isi.misd.tagfiler.AbstractFileTransferSession;
import edu.isi.misd.tagfiler.AbstractTagFilerApplet;
import edu.isi.misd.tagfiler.TagFilerUploadApplet;
import edu.isi.misd.tagfiler.client.ClientURLListener;
import edu.isi.misd.tagfiler.client.ClientURLResponse;
import edu.isi.misd.tagfiler.client.ConcurrentClientURL;
import edu.isi.misd.tagfiler.client.ConcurrentJakartaClient;
import edu.isi.misd.tagfiler.exception.FatalException;
import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.LocalFileChecksum;
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

    // tagfiler server URL
    private final String tagFilerServerURL;

    // listener for file upload progress
    private final FileUploadListener fileUploadListener;

    // client used to connect with the tagfiler server
    private final ConcurrentClientURL client;

    // map containing the checksums of all files to be uploaded.
    private final Map<String, String> checksumMap = new HashMap<String, String>();

    // total amount of bytes to be transferred
    private long datasetSize = 0;

    // base directory to use
    private String baseDirectory = "";

    // custom tags that are used
    private final CustomTagMap customTagMap;

    // the session cookie
    private String cookie = null;

    // the applet
    private TagFilerUploadApplet applet = null;
    
    private String dataset;

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
        assert (url != null && url.length() > 0);
        assert (l != null);
        assert (tagMap != null);

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
        assert (baseDir != null);
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
        assert (filesList != null);
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
        assert (files != null);
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
        assert (files != null);

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
     * Convenience method for computing checksums and totaling the file transfer
     * size.
     * 
     * @param files
     *            list of the files to transfer.
     * @throws FatalException
     *             if a fatal exception occurs when computing checksums
     */
    private void buildTotalAndChecksum(List<String> files)
            throws FatalException {
        assert (files != null);

        checksumMap.clear();
    }

    /**
     * Helper method for computing checksums and totaling the file transfer
     * size.
     * 
     * @param files
     *            list of the files to transfer.
     * @throws FatalException
     *             if a fatal error occurs when computing the checksums.
     */
    private void buildChecksumHelper(String filename)
            throws FatalException {
        assert (filename != null);

        File file = null;
        String checksum = null;
        long fileSize = 0;

        file = new File(filename);
        if (file.exists() && file.canRead()) {

            if (file.isFile()) {
                fileSize = file.length();
                checksum = LocalFileChecksum.computeFileChecksum(file);
                checksumMap.put(filename, checksum);
                fileUploadListener.notifyLogMessage("File=" + filename
                        + ", size=" + fileSize + ", checksum=" + checksum);
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
        assert (files != null);
        assert (datasetName != null && datasetName.length() > 0);
        this.dataset = datasetName;

        boolean success = false;
        ClientURLResponse response = null;

        // retrieve the amount of total bytes, checksums for each file
        fileUploadListener
                .notifyLogMessage("Computing size and checksum of files...");
        try {
        	buildTotalSize(files);
            fileUploadListener.notifyStart(datasetName, datasetSize);
        	buildTotalAndChecksum(files);
            fileUploadListener.notifyLogMessage(datasetSize
                    + " total bytes will be transferred");

            fileUploadListener
                    .notifyLogMessage("Beginning transfer of dataset '"
                            + datasetName + "'...");

	    // upload all the files
            synchronized (lock) {
        	    success = postFileDataHelper(files, datasetName);
        	    lock.wait();
            }

            // then create and tag the dataset url entry
            final String datasetURLQuery = DatasetUtils
                    .getDatasetURLUploadQuery(datasetName, tagFilerServerURL,
                            customTagMap);
            final String datasetURLFileBody = DatasetUtils.getDatasetURLUploadTagsBody(
                    datasetName, tagFilerServerURL);
            
            System.out.println("\ndatasetURLQuery: "+datasetURLQuery);
            System.out.println("datasetURLFileBody: "+datasetURLFileBody);
            
            response = client.postFileData(datasetURLQuery, datasetURLFileBody, cookie);
            
            synchronized (this) {
                cookie = client.updateSessionCookie(applet, cookie);
            }

            // successful tagfiler POST issues 303 redirect to result page
            if (200 != response.getStatus() && 303 != response.getStatus()) {
                fileUploadListener
                .notifyLogMessage("Error creating the dataset URL entry (code="
                        + response.getStatus() + ")");
		        success = false;
		        fileUploadListener.notifyFailure(datasetName, response.getStatus(), response.getErrorMessage());
            }
            
            final String datasetURLRegisterQuery = DatasetUtils
            .getDatasetURLRegisterQuery(datasetName, tagFilerServerURL);
            final String datasetURLBody = DatasetUtils.getDatasetTagsQuery(
                    datasetName, tagFilerServerURL, files, baseDirectory);
            //System.out.println("datasetURLBody: "+datasetURLBody);
            fileUploadListener.notifyLogMessage("Creating dataset URL entry");
            //fileUploadListener.notifyLogMessage("Query: " + datasetURLQuery
            //        + " Body:" + datasetURLBody);
            
            
            System.out.println("\ndatasetURLRegisterQuery: "+datasetURLRegisterQuery);
            System.out.println("datasetURLBody: "+datasetURLBody);
            // TODO: get the cookie and pass it here to the call

            // WebResource webResource =
            // ClientUtils.createWebResource(client,
            // datasetURLQuery, null);

            // need to capture builder result of cookie() and invoke request on
            // it
            // or cookie is lost!
            response = client.putFileData(datasetURLRegisterQuery, datasetURLBody, cookie);
            
            synchronized (this) {
                cookie = client.updateSessionCookie(applet, cookie);
            }

            // successful tagfiler POST issues 303 redirect to result page
            if (200 == response.getStatus() || 303 == response.getStatus()) {
                try {
                    fileUploadListener
                            .notifyLogMessage("Dataset URL entry created successfully.");
                } catch (Exception e) {
                    e.printStackTrace();
                    success = false;
                }
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
        	response.release();
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
        assert (files != null);
        assert (datasetName != null && datasetName.length() > 0);

        for (String fileName : files) {
        	buildChecksumHelper(fileName);
        }
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
	 * Get the URL parameters for uploads/downloads, if any
	 * In DIRC necessary for Transmission Number and checksum
	 * 
	 * @param file
	 *            the file to be uploaded/downloaded
	 * @return the URL parameters or null if None
	 */
	public String getURLParameters(String file) {
		// TODO Auto-generated method stub
		try {
			return DatasetUtils.getUploadQuerySuffix(dataset, checksumMap.get(file));
		} catch (FatalException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
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
		fileUploadListener.notifyFailure(dataset, err);
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
        cookie = client.updateSessionCookie(applet, cookie);
	}

}

