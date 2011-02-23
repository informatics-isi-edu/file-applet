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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
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
    client = new ConcurrentJakartaClient(allowChunks ? ((AbstractTagFilerApplet) applet).getMaxConnections() : 2, ((AbstractTagFilerApplet) applet).getSocketBufferSize(), this);
    client.setChunked(allowChunks);
    client.setChunkSize(((AbstractTagFilerApplet) applet).getChunkSize());
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
        		fileDownloadListener.notifyFailure(dataset, "Can not retrieve the file(s) to be downloaded");
        	}
        }
    	
        return fileNames;
    }

    /**
     * Performs the dataset download.
     * 
     * @param destDir
     *            destination directory for the download
     */
    public boolean downloadFiles(String destDir) {
        if (destDir == null || destDir.length() == 0) throw new IllegalArgumentException(destDir);
        try {
			client.setBaseURL(DatasetUtils.getBaseDownloadUrl(dataset, tagFilerServerURL));
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

        boolean success = true;
        long totalSize = datasetSize;
        if (enableChecksum) {
        	Set<String> keys = checksumMap.keySet();
        	for (String key : keys) {
        		if (bytesMap.get(key) != null) {
        			totalSize += bytesMap.get(key);
        		}
        	}
        }
        fileDownloadListener.notifyStart(dataset, totalSize);
        start = System.currentTimeMillis();
        client.download(fileNames, destDir, checksumMap, bytesMap, versionMap);

        return success;
    }

    /**
     * Sets the values for the custom tags of the dataset to be downloaded.
     * 
     * @throws UnsupportedEncodingException
     */
    private void setCustomTags() throws UnsupportedEncodingException {
    	JSONObject tagsValues = getDatasetTagValues();
        Set<String> tags = customTagMap.getTagNames();
	StringBuffer buffer = new StringBuffer();
        for (String tag : tags) {
            String value = "";
			try {
				value = tagsValues.getString(tag);
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
        			if (tagsValues.getJSONObject(i).isNull("bytes")) {
        				totalFiles--;
        			}
        		}
        		if (totalFiles > 0) {
                    fileDownloadListener.notifyRetrieveStart(totalFiles);
                    
            		for (int i=0; i < tagsValues.length(); i++) {
            			JSONObject fileTags = tagsValues.getJSONObject(i);
            			
            			// make sure we have a file
            			if (fileTags.isNull("bytes")) {
            				continue;
            			}
            			
            			// get the file name
                        String file = fileTags.getString("name").substring(dataset.length()+1);
                        fileNames.add(file);

                        // get the bytes
                        long bytes = fileTags.getLong("bytes");
                        datasetSize += bytes;
                        bytesMap.put(file, bytes);

                        // get the version
                        String vname = fileTags.getString("vname");
                        int version = Integer.parseInt(vname.substring(vname.lastIndexOf("@") + 1));
                        versionMap.put(file, version);

                        // get the checksum
                        if (!fileTags.isNull("sha256sum")) {
                            String checksum = fileTags.getString("sha256sum");
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
    public boolean verifyValidControlNumber(String controlNumber, int version, StringBuffer code, StringBuffer errorMessage) {
        if (controlNumber == null || controlNumber.length() == 0) throw new IllegalArgumentException(controlNumber);
        boolean valid = false;
        ClientURLResponse response = null;
        try {
        	String url = DatasetUtils.getDatasetUrl(controlNumber, version, tagFilerServerURL);
        	System.out.println("Verify Valid ControlNumber for: \"" + url + "\".");
        	response = client.verifyValidControlNumber(url, cookie);
            if (response == null) {
            	dataset = controlNumber;
            	notifyFailure("Error: NULL response in retrieving study " + controlNumber);
            	return valid;
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
    private JSONObject getDatasetTagValues() {
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
        	notifyFailure("Error: NULL response in getting the tag values for the study " + dataset);
        	return null;
        }

	cookie = client.updateSessionCookie(applet, cookie);

        if (response.getStatus() != 200)
        {
        	// if status is 404, the tag might have been deleted
        	if (response.getStatus() != 404) {
            	fileDownloadListener.notifyFailure(dataset, response.getStatus(), response.getErrorMessage());
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
	 */
	public void notifyFailure(String err) {
		// TODO Auto-generated method stub
		boolean notify = false;
		synchronized (this) {
			if (!cancel) {
				cancel = true;
				notify = true;
			}
		}
		if (notify) {
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
		if (fileDownloadListener.getFilesCompleted() == fileNames.size()) {
	        long t1 = System.currentTimeMillis();
	        System.out.println("Download time: " + (t1-start) + " ms.");
	        System.out.println("Download rate: " + DatasetUtils.roundTwoDecimals(((double) datasetSize)/1000/(t1-start)) + " MB/s.");
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
		long t = System.currentTimeMillis();
		if ((t - lastCookieUpdate) >= cookieUpdatePeriod) {
			lastCookieUpdate = t;
	        cookie = client.updateSessionCookie(applet, cookie);
		}
	}
}

