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
import org.json.JSONException;
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
import edu.isi.misd.tagfiler.util.FileWrapper;
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
    client = new ConcurrentJakartaClient(allowChunks ? ((AbstractTagFilerApplet) applet).getMaxConnections() : 2, ((AbstractTagFilerApplet) applet).getSocketBufferSize(), ((AbstractTagFilerApplet) applet).getSocketTimeout(), this);
    client.setChunked(allowChunks);
    client.setChunkSize(((AbstractTagFilerApplet) applet).getChunkSize());
    client.setRetryCount(((AbstractTagFilerApplet) applet).getMaxRetries());
    applet.setClient((ConcurrentJakartaClient) client);
    client.setCookieName(applet.getCookieName());
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
     * @param target
     *            resume or upload all
     */
    public boolean postFileData(List<String> files, String target) {
        if (files == null || target == null) throw new IllegalArgumentException(""+files+", "+target);
        this.target = target;
        boolean result = false;
        try {
        	if (dataset == null || dataset.length() == 0) {
        		dataset = getSequenceNumber(TagFilerProperties.getProperty("tagfiler.tag.transmitnumber"));
        	}
        	datasetId = getSequenceNumber(TagFilerProperties.getProperty("tagfiler.tag.keygenerator"));
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
        String query = tagFilerServerURL + "/" + TagFilerProperties.getProperty("tagfiler.tag.transmitnumber");
        ClientURLResponse response = client.getSequenceNumber(query, table, cookie);

        if (response == null) {
        	ArrayList<String> errMsg = new ArrayList<String>();
        	errMsg.add(client.getReason());
        	errMsg.add(TagFilerProperties.getProperty("tagfiler.message.upload.ControlNumberError"));
        	errMsg.add("Failure in uploading a study. Can not get a sequence number from table \"" + table + "\".");
        	errMsg.add(TagFilerProperties.getProperty("tagfiler.connection.lost"));
        	fileUploadListener.notifyLogMessage(DatasetUtils.join(errMsg, "\n"));
        	throw new FatalException(DatasetUtils.join(errMsg, "<br/>"));
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
    private List<FileWrapper> buildTotalSize(List<String> files)
            throws FatalException {
        if (files == null) throw new IllegalArgumentException(""+files);

        // the copy is done for performance reasons in the inner loop
        ArrayList<String> tempFiles = new ArrayList<String>(files);
        
        List<FileWrapper> filesList = new ArrayList<FileWrapper>();
        checksumMap = new HashMap<String, String>();
        bytesMap = new HashMap<String, Long>();
        fileNames = new ArrayList<String>();
        datasetSize = 0;
        
        if (target.equals(RESUME_TARGET)) {
        	// check what is completed or partial done
            JSONArray array = getFilesTagValues(applet, fileUploadListener);
            if (array != null) {
                for (int i=0; i<array.length(); i++) {
                	try {
        				JSONObject obj = array.getJSONObject(i);
                        String vname = obj.getString(VNAME);
                        String cksum = null;
                        if (!obj.isNull(SHA256SUM)) {
                            cksum = obj.getString(SHA256SUM);
                        }
        				for (String filename : tempFiles) {
    						long fileSize = (new File(filename)).length();
        					String basename = DatasetUtils.getBaseName(filename, baseDirectory);
        					if (obj.getString(NAME).equals(dataset+basename)) {
        	                    int version = Integer.parseInt(vname.substring(vname.lastIndexOf("@") + 1));
        						if (cksum != null) {
            						checksumMap.put(basename, cksum);
        						}
        						if (!obj.isNull(CHECK_POINT_OFFSET)) {
        							long offset = obj.getLong(CHECK_POINT_OFFSET);
        							if (offset != fileSize) {
        								// the file is partial uploaded
        								filesList.add(new FileWrapper(filename, offset, version, fileSize));
        							} else {
        								// upload completed for this file
        								// initialize the tables for upload validation
                						fileNames.add(basename);
                	                    bytesMap.put(basename, fileSize);
            		                    datasetSize += fileSize;
        							}
        							// for inner loop performance
        							tempFiles.remove(filename);
        						}
        						versionMap.put(filename, version);
        						break;
        					}
        				}
        			} catch (JSONException e) {
        				// TODO Auto-generated catch block
        				e.printStackTrace();
        			}
                }
            }
        }

        // add the rest of the files without check points
        for (String filename : tempFiles) {
        	int version = 0;
        	if (versionMap.get(filename) != null) {
        		version = versionMap.get(filename);
        	}
			filesList.add(new FileWrapper(filename, 0, version, (new File(filename)).length()));
        }
        
        // update the tables for the files that will be upload
        for (FileWrapper fileWrapper : filesList) {
        	String filename = fileWrapper.getName();
        	File file = new File(filename);
            if (file.exists() && file.canRead()) {
                if (file.isFile()) {
                    long fileSize = file.length();
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
        
        return filesList;
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
        cancel = false;
        fileUploadListener
                .notifyLogMessage("Computing size and checksum of files...");
        try {
        	List<FileWrapper> filesList = buildTotalSize(files);
        	
        	// get the total size of the files that will be uploaded
        	long totalSize = 0;
        	long uploadSize = 0;
        	for (FileWrapper fileWrapper : filesList) {
        		long size = fileWrapper.getFileLength() - fileWrapper.getOffset();
        		totalSize += size;
        		uploadSize += size;
        		if (enableChecksum) {
        			// checksum can not be resumed - so it will be applied to the entire file
        			totalSize += fileWrapper.getFileLength();
        		}
        	}
            fileUploadListener.notifyStart(dataset, totalSize);
            fileUploadListener.notifyLogMessage(uploadSize
                    + " total bytes will be transferred\n"+totalSize+ " total bytes in the progress bar");

            fileUploadListener
                    .notifyLogMessage("Beginning transfer of dataset '"
                            + dataset + "'...");

            // upload all the files
            long t2 = 0;
            if (!((AbstractTagFilerApplet) applet).allowChunksTransfering()) {
                ClientUtils.disableExpirationWarning(applet);
            }
            if (filesList.size() > 0) {
                synchronized (lock) {
            	    success = postFileDataHelper(filesList);
                    t2 = System.currentTimeMillis();
            	    lock.wait();
                }
            }
            if (cancel) {
            	success = false;
            	return success;
            }

            if (!((AbstractTagFilerApplet) applet).allowChunksTransfering()) {
                ClientUtils.enableExpirationWarning(applet);
            }
            
            long t1 = System.currentTimeMillis();
            long uploadTime = t1 - t2;
            double uploadRate = DatasetUtils.roundTwoDecimals(((double) datasetSize)/1000/(uploadTime));
            // then create and tag the dataset url entry
            String config = applet.eval("getConfig()");
            String studyType = null;
            if (config.length() > 0) {
            	studyType = config.split("=")[1];
            }
            String datasetURLQuery = DatasetUtils
                    .getDatasetURLUploadQuery(dataset, datasetId, tagFilerServerURL,
                            customTagMap, studyType);
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
            	ArrayList<String> errMsg = new ArrayList<String>();
            	errMsg.add(client.getReason());
            	errMsg.add("Can not create the URL entry for the dataset \"" + dataset + "\".");
            	errMsg.add(TagFilerProperties.getProperty("tagfiler.connection.lost"));
            	fileUploadListener.notifyLogMessage(DatasetUtils.join(errMsg, "\n"));
            	throw new FatalException(DatasetUtils.join(errMsg, "<br/>"));
            }
            synchronized (this) {
                cookie = client.updateSessionCookie(applet, cookie);
            }

            // check result
            if (200 != response.getStatus() && 303 != response.getStatus() && 201 != response.getStatus()) {
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
                datasetURLQuery = DatasetUtils.getDatasetURLUploadQuery(dataset, datasetVersion, tagFilerServerURL, "vcontains");
                fileUploadListener.notifyLogMessage("Deleting \"vcontains\" tag.");
                fileUploadListener.notifyLogMessage("Query: " + datasetURLQuery);
                response = client.delete(datasetURLQuery, cookie);
                if (response == null) {
                	ArrayList<String> errMsg = new ArrayList<String>();
                	errMsg.add(client.getReason());
                	errMsg.add("Can not delete the \"vcontains\" tag of the dataset \"" + dataset + "\".");
                	errMsg.add(TagFilerProperties.getProperty("tagfiler.connection.lost"));
                	fileUploadListener.notifyLogMessage(DatasetUtils.join(errMsg, "\n"));
                	throw new FatalException(DatasetUtils.join(errMsg, "<br/>"));
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
            	.getDatasetURLUploadQuery(dataset, datasetVersion, tagFilerServerURL, null);
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
            	ArrayList<String> errMsg = new ArrayList<String>();
            	errMsg.add(client.getReason());
            	errMsg.add("Can not register the files for the dataset \"" + dataset + "\".");
            	errMsg.add(TagFilerProperties.getProperty("tagfiler.connection.lost"));
            	fileUploadListener.notifyLogMessage(DatasetUtils.join(errMsg, "\n"));
            	throw new FatalException(DatasetUtils.join(errMsg, "<br/>"));
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
                datasetURLQuery = DatasetUtils
            		.getDatasetURLUploadQuery(dataset, datasetVersion, tagFilerServerURL, TagFilerProperties.getProperty("tagfiler.tag.incomplete"));
				System.out.println("Sending DELETE query: "+datasetURLQuery);
				response = client.delete(datasetURLQuery, cookie);
                if (response == null) {
                	ArrayList<String> errMsgs = new ArrayList<String>();
                	errMsgs.add(client.getReason());
                	errMsgs.add("Can not delete the \""+TagFilerProperties.getProperty("tagfiler.tag.incomplete")+"\" tag of the dataset \"" + DatasetUtils.urlDecode(datasetURLQuery) + "\".");
                	errMsgs.add(TagFilerProperties.getProperty("tagfiler.connection.lost"));
                	fileUploadListener.notifyLogMessage(DatasetUtils.join(errMsgs, "\n"));
                	throw new FatalException(DatasetUtils.join(errMsgs, "<br/>"));
                } else {
                    updateSessionCookie();
                    status = response.getStatus();
                    response.release();
                    if (status != 200) {
	                    errMsg = "<p>Can not delete the \""+TagFilerProperties.getProperty("tagfiler.tag.incomplete")+"\" tag.<p>Status ";
	                    errMsg += (status == 200) ? "" : ConcurrentJakartaClient.getStatusMessage(response);
	                	notifyFailure(" Can not delete the \""+TagFilerProperties.getProperty("tagfiler.tag.incomplete")+"\" tag of the dataset \""  + DatasetUtils.urlDecode(datasetURLQuery) + "\"." + errMsg, false);
		                return false;
                    } 
                }
            }
        	
        	if (success) {
            	success = checkDataSet();
            	if (!success) {
            		errMsg = "Failure in checking the uploaded files.";
            		System.out.println(errMsg);
            		status = -1;
            		notifyFailure(" Failure in checking the uploaded files.", false);
            		return success;
            	}
        	}
            
            // validate the upload
            datasetURLQuery = DatasetUtils.getDatasetQuery(dataset, tagFilerServerURL);
            System.out.println("Sending Upload Validate Action: \"" + datasetURLQuery + "\".");
            response = client.validateAction(datasetURLQuery, datasetId, success ? "success" : "failure", datasetSize, files.size(), "upload", cookie);
            if (response == null) {
            	ArrayList<String> errMsgs = new ArrayList<String>();
            	errMsgs.add(client.getReason());
            	errMsgs.add("Can not validate upload for the dataset \"" + dataset + "\".");
            	errMsgs.add(TagFilerProperties.getProperty("tagfiler.connection.lost"));
            	fileUploadListener.notifyLogMessage(DatasetUtils.join(errMsgs, "\n"));
            	throw new FatalException(DatasetUtils.join(errMsgs, "<br/>"));
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
                if (enableChecksum) {
                	sentRequests /= 2;
                }
                double fileRate = DatasetUtils.roundTwoDecimals(((double) files.size())*1000/uploadTime);
                double requestRate = DatasetUtils.roundTwoDecimals(((double) sentRequests)*1000/uploadTime);
                System.out.println("Total files: " + files.size());
                System.out.println("Total bytes: " + uploadSize);
                System.out.println("Total upload requests: " + sentRequests);
                System.out.println("Upload time: " + uploadTime + " ms");
                System.out.println("Upload rate: [" + uploadRate + " MB/sec, " + fileRate + " files/sec, " + requestRate + " requests/sec, " + (uploadSize/files.size()) + " bytes/file]");
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
            		System.out.println("fileNames.size(): " + fileNames.size() + " is different from tagsValues.length(): " + tagsValues.length() +".");
            		return result;
            	}
        		for (int i=0; i < tagsValues.length(); i++) {
        			JSONObject fileTags = tagsValues.getJSONObject(i);
        			
                    // get the file name
                    String file = fileTags.getString(NAME).substring(dataset.length());
                    if (!fileNames.remove(file)) {
                		System.out.println("file: \"" + file + "\" not found in fileNames.");
                    	return result;
                    }

                    // get the bytes
                    long bytes = fileTags.getLong(BYTES);
                    Long size = bytesMap.remove(file);
                    if (size == null || size != bytes) {
                    	System.out.println("file: \"" + file + "\" size is: " + size + ", bytes: " + bytes + ".");
                    	return result;
                    }
                    
                    if (enableChecksum) {
                    	if (fileTags.isNull(SHA256SUM)) {
                    		System.out.println("file: \"" + file + "\" SHA256SUM is NULL.");
                    		return result;
                    	}
                        String checksum = fileTags.getString(SHA256SUM);
                        String cksum = checksumMap.remove(file);
                        if (!checksum.equals(cksum)) {
                        	System.out.println("file: \"" + file + "\" checksum tag: " + checksum + ",  checksum file: " + cksum + ".");
                        	return result;
                        }
                    }
        		}
        		if (fileNames.size() != 0) {
        			System.out.println("Files not uploaded:");
        			for (String file : fileNames) {
        				System.out.println("\t" + file);
        			}
        			return result;
        		}
                result = true;
        	} else {
        		System.out.println("JSONArray tagsValues is NULL");
        	}
        } catch (Exception e) {
            e.printStackTrace();
            fileUploadListener.notifyError(e);
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
    private boolean postFileDataHelper(List<FileWrapper> files)
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
		sentRequests++;
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
		sentRequests++;
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
		if (newCookie != null) {
			String oldKey = cookie.split("\\|")[0];
			String newKey = newCookie.split("\\|")[0];
			long t = System.currentTimeMillis();
			boolean mustUpdate = (t - lastCookieUpdate) >= cookieUpdatePeriod || !oldKey.equals(newKey);
			if (mustUpdate) {
				lastCookieUpdate = t;
		        cookie = client.updateSessionCookie(applet, cookie);
			}
		}
	}

	@Override
	public void setDatasetName(String name) {
		dataset = name;
		
	}

}

