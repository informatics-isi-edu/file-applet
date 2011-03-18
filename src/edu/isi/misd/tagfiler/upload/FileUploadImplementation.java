package edu.isi.misd.tagfiler.upload;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.isi.misd.tagfiler.AbstractFileTransferSession;
import edu.isi.misd.tagfiler.AbstractTagFilerApplet;
import edu.isi.misd.tagfiler.TagFilerUploadApplet;
import edu.isi.misd.tagfiler.client.ClientURLListener;
import edu.isi.misd.tagfiler.client.ClientURLResponse;
import edu.isi.misd.tagfiler.client.ConcurrentJakartaClient;
import edu.isi.misd.tagfiler.exception.FatalException;
import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.ui.FileListener;
import edu.isi.misd.tagfiler.util.ClientUtils;
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
    public FileUploadImplementation(String url, FileListener l,
				    CustomTagMap tagMap, String c, TagFilerUploadApplet a) {
        if (url == null || url.length() == 0 ||
        		l == null || tagMap == null) 
        	throw new IllegalArgumentException(""+url+", "+l+", "+tagMap);

        tagFilerServerURL = url;
        fileUploadListener = (FileUploadListener) l;
        customTagMap = tagMap;
        cookie = c;
	applet = a;
	boolean allowChunks = ((AbstractTagFilerApplet) applet).allowChunksTransfering();
    client = new ConcurrentJakartaClient(allowChunks ? ((AbstractTagFilerApplet) applet).getMaxConnections() : 2, ((AbstractTagFilerApplet) applet).getSocketBufferSize(), this);
    client.setChunked(allowChunks);
    client.setChunkSize(((AbstractTagFilerApplet) applet).getChunkSize());
    client.setRetryCount(((AbstractTagFilerApplet) applet).getMaxRetries());
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
        	if (dataset == null || dataset.length() == 0) {
        		dataset = getSequenceNumber("transmitnumber");
        	}
        	datasetId = getSequenceNumber("keygenerator");
            result = postFiles(files);
        } catch (Exception e) {
            e.printStackTrace();
            fileUploadListener.notifyError(e);
            result = false;
        }
        return result;
    }

    /**
     * Makes a web server access to get a sequence number.
     * @param table
     *            the sequnce table to generate a number.
     * @return a sequence number
     */
    private String getSequenceNumber(String table) throws FatalException {
        String ret = "";
        String query = tagFilerServerURL + "/transmitnumber";
        ClientURLResponse response = client.getSequenceNumber(query, table, cookie);

        if (response == null) {
			notifyFailure("Failure in uploading a study. Can not get a sequence number from table \"" + table + "\".\\n\\n" +
					TagFilerProperties.getProperty("tagfiler.connection.lost"), true);
        	return null;
        }
        synchronized (this) {
            cookie = client.updateSessionCookie(applet, cookie);
        }

        if (200 == response.getStatus()) {
            ret = response.getLocationString();
        } else {
            fileUploadListener
                    .notifyLogMessage("Error getting a sequence number, table \"" + table + "\" (code="
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
        checksumMap = new HashMap<String, String>();
        bytesMap = new HashMap<String, Long>();
        fileNames = new ArrayList<String>();
        for (String filename : files) {
            file = new File(filename);
            if (file.exists() && file.canRead()) {

                if (file.isFile()) {
                    fileSize = file.length();
                    datasetSize += fileSize;
                    String basename = DatasetUtils.getBaseName(filename, baseDirectory);
                    bytesMap.put(basename, fileSize);
                    fileNames.add(basename);
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
    public boolean postFiles(List<String> files) {
        if (files == null) throw new IllegalArgumentException(""+files);

        boolean success = false;
        ClientURLResponse response = null;

        // retrieve the amount of total bytes, checksums for each file
        fileUploadListener
                .notifyLogMessage("Computing size and checksum of files...");
        try {
        	buildTotalSize(files);
            fileUploadListener.notifyStart(dataset, (enableChecksum ? 2 : 1)*datasetSize);
            fileUploadListener.notifyLogMessage(datasetSize
                    + " total bytes will be transferred");

            fileUploadListener
                    .notifyLogMessage("Beginning transfer of dataset '"
                            + dataset + "'...");

            // upload all the files
            long t2 = 0;
            if (!((AbstractTagFilerApplet) applet).allowChunksTransfering()) {
                ClientUtils.disableExpirationWarning(applet);
            }
            synchronized (lock) {
        	    success = postFileDataHelper(files);
                t2 = System.currentTimeMillis();
        	    lock.wait();
            }
            if (!((AbstractTagFilerApplet) applet).allowChunksTransfering()) {
                ClientUtils.enableExpirationWarning(applet);
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
                    .getDatasetURLUploadQuery(dataset, datasetId, tagFilerServerURL,
                            customTagMap);
            String datasetBody = DatasetUtils.getDatasetURLUploadBody(
            		datasetId, tagFilerServerURL);
            
            fileUploadListener.notifyLogMessage("Creating dataset URL entry.");
            fileUploadListener.notifyLogMessage("Query: " + datasetURLQuery
                    + "\nBody: " + datasetBody);
            
            applet.updateStatus(TagFilerProperties.getProperty("tagfiler.label.CompleteUploadStatus"));
            t1 = System.currentTimeMillis();
            response = client.postFileData(datasetURLQuery, datasetBody, cookie);
            t2 = System.currentTimeMillis();
            System.out.println("Elapsed time: " + (t2-t1) + " ms.");
            
            if (response == null) {
            	notifyFailure("Can not create the URL entry for the dataset \"" + dataset + "\".\\n\\n" + 
            			TagFilerProperties.getProperty("tagfiler.connection.lost"), true);
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
		        String err = "<p>Failure in creating the dataset \"" + dataset + "\".<p>Status ";
		        err += ConcurrentJakartaClient.getStatusMessage(response);
		        response.release();
		        fileUploadListener.notifyFailure(dataset, err);
		        return success;
            }
            
            // get the dataset version
            datasetVersion = DatasetUtils.getVersion(response.getLocationString());
            
            response.release();
            
            if (datasetVersion > 1) {
            	// delete the vcontains, as it was populated by coping files from the previous versions
                datasetURLQuery = DatasetUtils.getDatasetURLUploadQuery(dataset, datasetVersion, tagFilerServerURL);
                fileUploadListener.notifyLogMessage("Deleting \"vcontains\" tag.");
                fileUploadListener.notifyLogMessage("Query: " + datasetURLQuery);
                response = client.delete(datasetURLQuery, cookie);
                if (response == null) {
                	notifyFailure("Can not delete the \"vcontains\" tag of the dataset \"" + dataset + "\".\\n\\n" +
                			TagFilerProperties.getProperty("tagfiler.connection.lost"), true);
                	success = false;
                	return success;
                } else {
                    synchronized (this) {
                        cookie = client.updateSessionCookie(applet, cookie);
                    }
                    
                    int status = response.getStatus();
                    String errMsg = "<p>Can not delete the \"vcontains\" tag.<p>Status ";
                    errMsg += (status == 200) ? "" : ConcurrentJakartaClient.getStatusMessage(response);
                    response.release();
                    if (status != 200) {
                        fileUploadListener
                        .notifyLogMessage("Error creating the dataset URL entry (code="
                                + status + "). Can not delete the \"vcontains\" tag");
		                success = false;
		                fileUploadListener.notifyFailure(dataset, errMsg);
		                return success;
                    }
                }
            }
            
            // Register the dataset files
            datasetURLQuery = DatasetUtils
            	.getDatasetURLUploadQuery(dataset, datasetVersion, tagFilerServerURL);
            datasetBody = DatasetUtils.getDatasetURLUploadBody(
            		dataset, tagFilerServerURL, files, versionMap, baseDirectory);
            
            fileUploadListener.notifyLogMessage("Registering dataset files.");
            fileUploadListener.notifyLogMessage("Query: " + datasetURLQuery
                    + "\nBody: " + datasetBody);
            
            t1 = System.currentTimeMillis();
            response = client.putFileData(datasetURLQuery, datasetBody, cookie);
            t2 = System.currentTimeMillis();
            System.out.println("Elapsed time: " + (t2-t1) + " ms.");
            if (response == null) {
            	notifyFailure("Can not register the files for the dataset \"" + dataset + "\".\\n\\n" + 
            			TagFilerProperties.getProperty("tagfiler.connection.lost"), true);
            	success = false;
            	return success;
            }
            
            synchronized (this) {
                cookie = client.updateSessionCookie(applet, cookie);
            }

            // successful tagfiler POST issues 303 redirect to result page
            int status = response.getStatus();
            success = (200 == status || 303 == status);
            String errMsg = "<p>Can not register the dataset files.<p>Status ";
            errMsg += (status == 200) ? "" : ConcurrentJakartaClient.getStatusMessage(response);
        	response.release();
        	
        	if (success) {
            	success = checkDataSet();
            	if (!success) {
            		errMsg = "<p>Failure in checking the uploaded files.";
            		System.out.println(errMsg);
            		status = -1;
            	}
        	}
            
            // validate the upload
            datasetURLQuery = DatasetUtils.getDatasetQuery(dataset, tagFilerServerURL);
            System.out.println("Sending Upload Validate Action: \"" + datasetURLQuery + "\".");
            response = client.validateAction(datasetURLQuery, datasetId, success ? "success" : "failure", datasetSize, files.size(), "upload", cookie);
            if (response == null) {
            	notifyFailure("Can not validate upload for the dataset \"" + dataset + "\".\\n\\n" +
            			TagFilerProperties.getProperty("tagfiler.connection.lost"), true);
            	success = false;
            	return success;
            }
            synchronized (this) {
                cookie = client.updateSessionCookie(applet, cookie);
            }
            if (success) {
            	status = response.getStatus();
            	errMsg = (status == 200) ? "" : "<p>Can not validate upload.<p>Status " + ConcurrentJakartaClient.getStatusMessage(response);
            }

            if (200 == status) {
                fileUploadListener.notifyLogMessage("Dataset URL entry created successfully.");
                success = true;
        		fileUploadListener.notifySuccess(dataset, datasetVersion);
            } else {
                fileUploadListener
                        .notifyLogMessage("Error creating the dataset URL entry (code="
                                + status + ")");
                success = false;
                fileUploadListener.notifyFailure(dataset, errMsg);
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
     * Gets the files to be downloaded.
     */
    private boolean checkDataSet() throws Exception {
        boolean result = false;

        try {
        	// get the "bytes" and "sha256sum" tags of the files
        	JSONArray tagsValues = getFilesTagValues(applet, fileUploadListener);
        	if (tagsValues != null) {
            	if (fileNames.size() != tagsValues.length()) {
            		return result;
            	}
        		for (int i=0; i < tagsValues.length(); i++) {
        			JSONObject fileTags = tagsValues.getJSONObject(i);
        			
                    // get the file name
                    String file = fileTags.getString("name").substring(dataset.length());
                    if (!fileNames.remove(file)) {
                    	return result;
                    }

                    // get the bytes
                    long bytes = fileTags.getLong("bytes");
                    Long size = bytesMap.remove(file);
                    if (size == null || size != bytes) {
                    	return result;
                    }
                    
                    if (enableChecksum) {
                    	if (fileTags.isNull("sha256sum")) {
                    		return result;
                    	}
                        String checksum = fileTags.getString("sha256sum");
                        String cksum = checksumMap.remove(file);
                        if (!checksum.equals(cksum)) {
                        	return result;
                        }
                    }
        		}
        		if (fileNames.size() != 0) {
        			return result;
        		}
                result = true;
        	}
        } catch (Exception e) {
            e.printStackTrace();
            fileUploadListener.notifyError(e);
            result = false;
        }

        return result;
    }

    /**
     * Helper method for transferring files.
     * 
     * @param files
     *            list of the files
     * @return true if the file transfer was a success
     * @throws FatalException
     *             if an error occurred in one of the file transfers
     */
    private boolean postFileDataHelper(List<String> files)
            throws FatalException {
        if (files == null) throw new IllegalArgumentException(""+files);

        client.setBaseURL(DatasetUtils.getBaseUploadQuery(dataset, tagFilerServerURL));
        client.upload(files, baseDirectory, checksumMap, versionMap);
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
     * @param connectionBroken
     *            true if the error is due to a broken connection
	 */
	public void notifyFailure(String err, boolean connectionBroken) {
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
			if (!connectionBroken) {
	            String datasetURLQuery = null;
				try {
					datasetURLQuery = DatasetUtils.getDatasetQuery(dataset, tagFilerServerURL);
				} catch (FatalException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				client.validateAction(datasetURLQuery, datasetId, "failure", 0, 0, "upload", cookie);
				fileUploadListener.notifyFailure(dataset, err);
			} else {
				// send here JavaScript error message, as the connection is broken
				applet.eval("notifyFailure", err, false);
			}
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
		String newCookie = client.getSessionCookie();
		String oldKey = cookie.split("\\|")[0];
		String newKey = newCookie.split("\\|")[0];
		long t = System.currentTimeMillis();
		boolean mustUpdate = (t - lastCookieUpdate) >= cookieUpdatePeriod || !oldKey.equals(newKey);
		if (mustUpdate) {
			lastCookieUpdate = t;
	        cookie = client.updateSessionCookie(applet, cookie);
		}
	}

	@Override
	public void setDatasetName(String name) {
		dataset = name;
		
	}

}

