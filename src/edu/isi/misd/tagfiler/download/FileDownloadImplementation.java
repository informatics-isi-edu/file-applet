package edu.isi.misd.tagfiler.download;

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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.isi.misd.tagfiler.AbstractFileTransferSession;
import edu.isi.misd.tagfiler.AbstractTagFilerApplet;
import edu.isi.misd.tagfiler.TagFilerDownloadApplet;
import edu.isi.misd.tagfiler.client.ClientURLListener;
import edu.isi.misd.tagfiler.client.ClientURLResponse;
import edu.isi.misd.tagfiler.client.ConcurrentJakartaClient;
import edu.isi.misd.tagfiler.exception.FatalException;
import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.ui.FileListener;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.ClientUtils;
import edu.isi.misd.tagfiler.util.FileWrapper;
import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Default implementation of the
 * {@link edu.isi.misd.tagfiler.download.FileDownload} interface.
 * 
 * @author Serban Voinea
 * 
 */
public class FileDownloadImplementation extends AbstractFileTransferSession
        implements FileDownload, ClientURLListener {

    // listener for file download progress
    private final FileDownloadListener fileDownloadListener;

    // the download start time
    private long start;

    // the applet
    private TagFilerDownloadApplet applet = null;

    /**
     * Constructs a new file download
     * 
     * @param url
     *            tagfiler server url
     * @param l
     *            listener for file upload progress
     * @param c
     *            session cookie
     * @param tagMap
     *            map of the custom tags
     */
    public FileDownloadImplementation(String url, FileListener l,
				      String c, CustomTagMap tagMap, TagFilerDownloadApplet a) {
        if (url == null || url.length() == 0 ||
        		l == null || c == null || tagMap == null) 
        	throw new IllegalArgumentException(""+url+", "+l+", "+c+", "+tagMap);

        tagFilerServerURL = url;
        fileDownloadListener = (FileDownloadListener) l;
        cookie = c;
        customTagMap = tagMap;
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
     * Returns the total number of bytes to be downloaded.
     */
    public long getSize() {
        return datasetSize;
    }

    /**
     * Returns the list of the file names to be downloaded.
     * 
     * @param controlNumber
     *            the controlNumber of the dataset
     */
    public List<String> getFiles(String controlNumber, int version) {
        if (controlNumber == null || controlNumber.length() == 0) throw new IllegalArgumentException(controlNumber);
        dataset = controlNumber;
        datasetVersion = version;
    	fileDownloadListener.notifyUpdateStart(dataset);
    	boolean success = false;

        try {
            fileNames = new ArrayList<String>();
            checksumMap = new HashMap<String, String>();
            bytesMap = new HashMap<String, Long>();
            datasetSize = 0;

            if (customTagMap.getTagNames().size() > 0) {
                setCustomTags();
            }
            success = getDataSet();
        } catch (Exception e) {
            e.printStackTrace();
            fileDownloadListener.notifyError(e);
        }
        finally {
        	if (success) {
            	fileDownloadListener.notifyUpdateComplete(dataset);
        	} else {
        		fileDownloadListener.notifyFailure(dataset, "<p>Can not retrieve the file(s) to be downloaded.");
        	}
        }
    	
        return fileNames;
    }

    /**
     * Performs the dataset download.
     * 
     * @param destDir
     *            destination directory for the download
     * @param target
     *            resume or download all
     */
    @SuppressWarnings("unchecked")
	public boolean downloadFiles(String destDir, String target) {
        if (destDir == null || destDir.length() == 0 || target == null) throw new IllegalArgumentException(destDir+", "+target);
        this.target = target;
        try {
			client.setBaseURL(DatasetUtils.getBaseDownloadUrl(dataset, tagFilerServerURL));
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

        // the copy is done for performance reasons in the inner loop
        ArrayList<String> tempFiles = new ArrayList<String>(fileNames);
        
        List<FileWrapper> filesList = new ArrayList<FileWrapper>();
        if (target.equals(RESUME_TARGET)) {
        	// resume download
        	String filename = destDir + File.separator + TagFilerProperties.getProperty("tagfiler.checkpoint.file");
        	File file = new File(filename);
        	if (file.exists() && file.isFile() && file.canRead()) {
        		// get the download check point status
        		try {
					FileInputStream fis = new FileInputStream(filename);
					ObjectInputStream in = new ObjectInputStream(fis);
					Hashtable<String, Long> checkPoint = (Hashtable<String, Long>) in.readObject();
					HashMap<String, String> checksum = (HashMap<String, String>) in.readObject();
					in.close();
					fis.close();
					System.out.println("Check Points Read: "+checkPoint+"\n"+checksum);
					Set<String> keys = checkPoint.keySet();
					for (String key : keys) {
						if (tempFiles.contains(key)) {
							tempFiles.remove(key);
							boolean complete = (long)bytesMap.get(key) == (long)checkPoint.get(key);
							if (complete && enableChecksum) {
								complete = checksum.get(key) != null && checksumMap.get(key) != null && 
										   checksum.get(key).equals(checksumMap.get(key));
							}
							if (complete) {
								// file already downloaded
								bytesMap.remove(key);
								versionMap.remove(key);
								checksumMap.remove(key);
							} else {
								// file partial downloaded
								filesList.add(new FileWrapper(key, checkPoint.get(key), versionMap.get(key), bytesMap.get(key)));
							}
						}
					}
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        }
        
        // add the rest of the files without check points
        for (String filename : tempFiles) {
			filesList.add(new FileWrapper(filename, 0, versionMap.get(filename), bytesMap.get(filename)));
        }
        
        System.out.println(""+filesList.size()+" file(s) will be downloaded");
        
        // get the total size of the files to be downloaded and checksum
        long totalSize = 0;
        long tb = 0;
        for (FileWrapper fileWrapper : filesList) {
        	tb += fileWrapper.getFileLength() - fileWrapper.getOffset();
        	totalSize += fileWrapper.getFileLength() - fileWrapper.getOffset();
        	if (enableChecksum) {
    			totalSize += fileWrapper.getFileLength();
        	}
        }
        fileDownloadListener.notifyLogMessage(tb
                + " total bytes will be transferred\n"+totalSize+ " total bytes in the progress bar");
        fileDownloadListener.notifyStart(dataset, totalSize);
        if (!((AbstractTagFilerApplet) applet).allowChunksTransfering()) {
            ClientUtils.disableExpirationWarning(applet);
        }
        start = System.currentTimeMillis();
        cancel = false;
        client.download(filesList, destDir, checksumMap, bytesMap, versionMap);

        return true;
    }

    /**
     * Sets the values for the custom tags of the dataset to be downloaded.
     * 
     * @throws UnsupportedEncodingException
     */
    private void setCustomTags() throws UnsupportedEncodingException, FatalException {
    	JSONObject tagsValues = getDatasetTagValues();
        Set<String> tags = customTagMap.getTagNames();
	StringBuffer buffer = new StringBuffer();
        for (String tag : tags) {
            String value = "";
			try {
				if (!tagsValues.isNull(tag)) {
					value = tagsValues.getString(tag);
				}
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    buffer.append(tag).append("<br/>").append(value).append("<br/>");
            customTagMap.setValue(tag, value);
        }
	String values = buffer.toString().replaceAll("'", "\\\\'");
	applet.eval("setTags", values);
    }

    /**
     * Gets the files to be downloaded.
     */
    private boolean getDataSet() throws Exception {
        boolean result = false;

        try {
        	// get the "bytes" and "sha256sum" tags of the files
        	JSONArray tagsValues = getFilesTagValues(applet, fileDownloadListener);
        	if (tagsValues != null) {
        		// get the number of files to be downloaded
        		// by replacing a file with an URL, we might have have fewer files to be downloaded
        		int totalFiles = tagsValues.length();
        		for (int i=0; i < tagsValues.length(); i++) {
        			if (tagsValues.getJSONObject(i).isNull(BYTES)) {
        				totalFiles--;
        			}
        		}
        		if (totalFiles > 0) {
                    fileDownloadListener.notifyRetrieveStart(totalFiles);
                    
            		for (int i=0; i < tagsValues.length(); i++) {
            			JSONObject fileTags = tagsValues.getJSONObject(i);
            			
            			// make sure we have a file
            			if (fileTags.isNull(BYTES)) {
            				continue;
            			}
            			
            			// get the file name
                        String file = fileTags.getString(NAME).substring(dataset.length()+1);
                        fileNames.add(file);

                        // get the bytes
                        long bytes = fileTags.getLong(BYTES);
                        datasetSize += bytes;
                        bytesMap.put(file, bytes);

                        // get the version
                        String vname = fileTags.getString(VNAME);
                        int version = Integer.parseInt(vname.substring(vname.lastIndexOf("@") + 1));
                        versionMap.put(file, version);

                        // get the checksum
                        if (!fileTags.isNull(SHA256SUM)) {
                            String checksum = fileTags.getString(SHA256SUM);
                            checksumMap.put(file, checksum);
                        }
                        fileDownloadListener.notifyFileRetrieveComplete(file);
            		}
            	}
        	}
            result = true;
        } catch (Exception e) {
            e.printStackTrace();
            fileDownloadListener.notifyError(e);
            result = false;
        }

        return result;
    }

    /**
     * Checks with the tagfiler server to verify that a dataset by the control
     * number already exists
     * 
     * @param controlNumber
     *            the Dataset Name to check
     * @param status
     *            the status returned by the HTTP response 
     * @param errorMessage
     *            the error message to be displayed
     * @return true if a dataset with the particular Dataset Name exists,
     *         false otherwise
     */
    public boolean verifyValidControlNumber(String controlNumber, int version, StringBuffer code, StringBuffer errorMessage)  throws FatalException {
        if (controlNumber == null || controlNumber.length() == 0) throw new IllegalArgumentException(controlNumber);
        boolean valid = false;
        ClientURLResponse response = null;
        try {
        	String url = DatasetUtils.getDatasetUrl(controlNumber, version, tagFilerServerURL);
        	System.out.println("Verify Valid ControlNumber for: \"" + url + "\".");
        	response = client.verifyValidControlNumber(url, cookie);
            if (response == null) {
            	dataset = controlNumber;
            	ArrayList<String> errMsg = new ArrayList<String>();
            	errMsg.add(client.getReason());
            	errMsg.add("Can not verify control number: \"" + controlNumber + "\" for the dataset \"" + dataset + "\".");
            	errMsg.add(TagFilerProperties.getProperty("tagfiler.connection.lost"));
            	fileDownloadListener.notifyLogMessage(DatasetUtils.join(errMsg, "\n"));
            	throw new FatalException(DatasetUtils.join(errMsg, "<br/>"));
            }
        	int status = response.getStatus();
            if ((status == 200 || status == 303) && response.checkResponseHeaderPattern(
            		ClientUtils.LOCATION_HEADER_NAME, 
                    tagFilerServerURL
                    + DatasetUtils.QUERY_URI
                    + DatasetUtils.KEY
                    + ".*"
                    + DatasetUtils.ANY_VERSION)) {
                valid = true;
            }
            else {
                System.out.println("Dataset Name verification failed, code="
                        + (status != 200 && status != 303 ? response.getStatus() : -1));

            	code.append("Status ").append(status);
                switch (status) {
                case 404:
                	errorMessage.append("Not Found");
                	break;
                case 403:
                	errorMessage.append("Forbidden");
                	break;
                case 401:
                	errorMessage.append("Unauthorized");
                	break;
                case 400:
                	errorMessage.append("Bad Request");
                	break;
                case 409:
                	errorMessage.append("Conflict");
                	break;
                case 500:
                	errorMessage.append("Internal Server Error");
                	break;
                default:
                	errorMessage.append("Unmatched Response Header Pattern");
                }
            }
            cookie = client.updateSessionCookie(applet, cookie);
        } catch (UnsupportedEncodingException e) {
            valid = false;
            e.printStackTrace();
            fileDownloadListener.notifyError(e);
        } finally {
        	if (response != null) {
            	response.release();
        	}
        }
        return valid;
    }
    
    /**
     * Get the values for the custom tags of the dataset
     * @return the JSON Object with the tags values
     */
    private JSONObject getDatasetTagValues() throws FatalException {
    	String tags = DatasetUtils.joinEncode(customTagMap.getTagNames(), ",");
        String query = null;
		try {
			query = DatasetUtils.getDatasetTags(dataset, datasetVersion,
			        tagFilerServerURL, tags);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Dataset Query: "+query);
        ClientURLResponse response = client.getTagsValues(query, cookie);
        if (response == null) {
        	ArrayList<String> errMsg = new ArrayList<String>();
        	errMsg.add(client.getReason());
        	errMsg.add("Can not get the tags for the dataset \"" + dataset + "\".");
        	errMsg.add(TagFilerProperties.getProperty("tagfiler.connection.lost"));
        	fileDownloadListener.notifyLogMessage(DatasetUtils.join(errMsg, "\n"));
        	throw new FatalException(DatasetUtils.join(errMsg, "<br/>"));
        }

	cookie = client.updateSessionCookie(applet, cookie);

        if (response.getStatus() != 200)
        {
        	// if status is 404, the tag might have been deleted
        	if (response.getStatus() != 404) {
            	fileDownloadListener.notifyFailure(dataset, "<p>Can not get the dataset tags.<p>Status " + ConcurrentJakartaClient.getStatusMessage(response));
        	}
        	response.release();
        	return null;
        }
		String values = null;
		try {
			values = DatasetUtils.urlDecode(response.getEntityString());
			System.out.println("Dataset Response: "+values);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		JSONArray array = null;
		try {
			array = new JSONArray(values);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (array.length() != 1) {
        	response.release();
			return null;
		}
		JSONObject obj = null;
		try {
			obj = array.getJSONObject(0);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        response.release();
        
        return obj;
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
		fileDownloadListener.notifyChunkTransfered(false, size);
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
		fileDownloadListener.notifyFailure(dataset, err);
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
		synchronized (this) {
			if (!cancel) {
				cancel = true;
				notify = true;
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
				// log download failure
				client.validateAction(datasetURLQuery, datasetId, "failure", 0, 0, "download", cookie);
				fileDownloadListener.notifyFailure(dataset, err);
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
		fileDownloadListener.notifyChunkTransfered(true, size);
		sentRequests++;
		if (fileDownloadListener.getFilesCompleted() == fileNames.size()) {
	        long t1 = System.currentTimeMillis();
            if (enableChecksum) {
            	sentRequests /= 2;
            }
            long downloadTime = t1 - start;
            double downloadRate = DatasetUtils.roundTwoDecimals(((double) datasetSize)/1000/downloadTime);
            double fileRate = DatasetUtils.roundTwoDecimals(((double) fileNames.size())*1000/downloadTime);
            double requestRate = DatasetUtils.roundTwoDecimals(((double) sentRequests)*1000/downloadTime);
            System.out.println("Total files: " + fileNames.size());
            System.out.println("Total bytes: " + datasetSize);
            System.out.println("Total download requests: " + sentRequests);
            System.out.println("Download time: " + downloadTime + " ms");
            System.out.println("Download rate: [" + downloadRate + " MB/sec, " + fileRate + " files/sec, " + requestRate + " requests/sec, " + (datasetSize/fileNames.size()) + " bytes/file]");
            if (!((AbstractTagFilerApplet) applet).allowChunksTransfering()) {
                ClientUtils.enableExpirationWarning(applet);
            }
		}
	}

	/**
	 * Callback to notify success for the entire upload/download process
	 * 
	 */
	public void notifySuccess() {
		// TODO Auto-generated method stub
        String datasetURLQuery = null;
		try {
			datasetURLQuery = DatasetUtils.getDatasetQuery(dataset, tagFilerServerURL);
		} catch (FatalException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// log download failure
		client.validateAction(datasetURLQuery, datasetId, "success", 0, 0, "download", cookie);
		fileDownloadListener.notifySuccess(dataset, datasetVersion);
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
}

