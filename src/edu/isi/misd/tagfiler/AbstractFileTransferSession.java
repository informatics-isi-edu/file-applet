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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;

import edu.isi.misd.tagfiler.client.ClientURLResponse;
import edu.isi.misd.tagfiler.client.ConcurrentClientURL;
import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.ui.FileListener;
import edu.isi.misd.tagfiler.util.DatasetUtils;

/**
 * Implementation of {@code edu.isi.misd.tagfiler.FileTransfer}
 * 
 * @author David Smith
 * 
 */
public abstract class AbstractFileTransferSession implements FileTransfer {
    protected static final String NAME = "name";
    
    protected static final String VNAME = "vname";
    
    protected static final String BYTES = "bytes";
    
    protected static final String SHA256SUM = "sha256sum";
    
    protected static final String CHECK_POINT_OFFSET = "check point offset";
    
    protected static final String RESUME_TARGET = "resume";
    
    protected static final String ALL_TARGET = "all";
    
	protected String cookie = null;

    // tagfiler server URL
    protected String tagFilerServerURL;

    // client used to connect with the tagfiler server
    protected ConcurrentClientURL client;

    // total amount of bytes to be uploaded/downloaded
    protected long datasetSize = 0;

    // the number of sent requests for upload/download
    protected long sentRequests = 0;

    // custom tags that are used
    protected CustomTagMap customTagMap;

    // the Dataset Name
    protected String dataset;

    // the Dataset Id
    protected String datasetId;

	// the Dataset Version
    protected int datasetVersion;

    // list containing the files names to be downloaded.
    protected List<String> fileNames = new ArrayList<String>();

    // map containing the bytes of all files to be transferred.
    protected Map<String, Long> bytesMap = new HashMap<String, Long>();

    // map containing the checksums of all files to be transferred.
    protected Map<String, String> checksumMap = new HashMap<String, String>();

    // map containing the versions of all files to be transferred.
    protected Map<String, Integer> versionMap = new HashMap<String, Integer>();

	// flag to mark a failure
    protected boolean cancel;

    // the time when the last cookie was updated
    protected long lastCookieUpdate;

    // the time when the last cookie was updated
    protected long cookieUpdatePeriod = 1*60*1000;

    // flag to enable/disable the checksum
    protected boolean enableChecksum;

    // the transfer target: 'all' or 'resume'
    protected String target = ALL_TARGET;

	/**
     * Updates the session cookie
     */
    public synchronized void updateSessionCookie(String sessionCookie) {
        cookie = sessionCookie;
    }

    /**
     * @return the session cookie
     */
    public synchronized String getSessionCookie() {
        return cookie;
    }

	public String getDataset() {
		return dataset;
	}

    public boolean isEnableChecksum() {
		return enableChecksum;
	}

	public void setEnableChecksum(boolean enableChecksum) {
		this.enableChecksum = enableChecksum;
	}

    public String getDatasetId() {
		return datasetId;
	}

	public void setDatasetId(String datasetId) {
		this.datasetId = datasetId;
	}

    /**
     * Get the values for the "bytes" and "sha256sum" tags of the dataset files
     * 
	 * @param applet
	 *            the applet object
	 * @param fl
	 *            the file listener to send error message
     * @return the JSON Array with the tags values
     */
    protected JSONArray getFilesTagValues(AbstractTagFilerApplet applet, FileListener fl) {
    	String tags[] = {"bytes", "sha256sum", "vname", "name", "check point offset"};
    	String tagsList = DatasetUtils.joinEncode(tags, ";");
        String query = null;
		try {
			if (target.equals(ALL_TARGET)) {
				query = DatasetUtils.getFilesTags(dataset, datasetVersion,
				        tagFilerServerURL, tagsList);
			} else {
				query = DatasetUtils.getFilesTags(dataset,
				        tagFilerServerURL, tagsList);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Files Query: "+query);
        ClientURLResponse response = client.getTagsValues(query, cookie);

        if (response == null) {
        	notifyFailure("Error: NULL response in getting the files tag values for the study " + dataset, true);
        	return null;
        }
	cookie = client.updateSessionCookie(applet, cookie);

        if (response.getStatus() != 200)
        {
        	// if status is 404, the tag might have been deleted
        	if (response.getStatus() != 404) {
            	fl.notifyFailure(dataset, response.getStatus(), response.getErrorMessage());
        	}
        	response.release();
        	return null;
        }
		String values = null;
		try {
			values = DatasetUtils.urlDecode(response.getEntityString());
			System.out.println("Files Response: "+values);
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
		} catch (Throwable e) {
			e.printStackTrace();
		}
        response.release();
        
        return array;
    }

	/**
	 * Callback to notify a failure during the upload/download process
	 * 
	 * @param err
	 *            the error message
     * @param connectionBroken
     *            true if the error is due to a broken connection
	 */
     public abstract void notifyFailure(String err, boolean connectionBroken);
}
