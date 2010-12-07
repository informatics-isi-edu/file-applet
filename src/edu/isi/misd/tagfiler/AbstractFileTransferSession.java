package edu.isi.misd.tagfiler;

import edu.isi.misd.tagfiler.client.ConcurrentClientURL;
import edu.isi.misd.tagfiler.ui.CustomTagMap;

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

    // flag to mark a failure
    protected boolean cancel;

    // the time when the last cookie was updated
    protected long lastCookieUpdate;

    // the time when the last cookie was updated
    protected long cookieUpdatePeriod = 1*60*1000;

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

}
