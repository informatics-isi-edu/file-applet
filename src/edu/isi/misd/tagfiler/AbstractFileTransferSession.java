package edu.isi.misd.tagfiler;

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
public class AbstractFileTransferSession implements FileTransfer {
    protected String cookie = null;

    // tagfiler server URL
    protected String tagFilerServerURL;

    // client used to connect with the tagfiler server
    protected ConcurrentClientURL client;

    // total amount of bytes to be uploaded/downloaded
    protected long datasetSize = 0;

    // custom tags that are used
    protected CustomTagMap customTagMap;

    // the dataset transmission number
    protected String dataset;

    // list containing the files names to be downloaded.
    protected List<String> fileNames = new ArrayList<String>();

    // map containing the bytes of all files to be transferred.
    protected Map<String, Long> bytesMap = new HashMap<String, Long>();

    // map containing the checksums of all files to be transferred.
    protected Map<String, String> checksumMap = new HashMap<String, String>();

    // flag to mark a failure
    protected boolean cancel;

    // the time when the last cookie was updated
    protected long lastCookieUpdate;

    // the time when the last cookie was updated
    protected long cookieUpdatePeriod = 1*60*1000;

    // flag to enable/disable the checksum
    protected boolean enableChecksum;

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
    	String tags = "bytes,sha256sum";
        String query = null;
		try {
			query = DatasetUtils.getFilesTags(dataset,
			        tagFilerServerURL, tags);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Files Query: "+query);
        ClientURLResponse response = client.getTagsValues(query, cookie);

        if (response == null) {
        	fl.notifyFailure(dataset, -1, "Error: NULL response in getting the files tag values for the study " + dataset);
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
		}
        response.release();
        
        return array;
    }

}
