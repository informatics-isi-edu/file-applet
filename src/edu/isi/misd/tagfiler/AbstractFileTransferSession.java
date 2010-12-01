package edu.isi.misd.tagfiler;

import java.util.HashMap;
import java.util.Map;

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

    // map containing the checksums of all files to be uploaded/downloaded.
    protected Map<String, String> checksumMap = new HashMap<String, String>();

    // total amount of bytes to be uploaded/downloaded
    protected long datasetSize = 0;

    // custom tags that are used
    protected CustomTagMap customTagMap;

    // the dataset transmission number
    protected String dataset;

    // flag to mark a failure
    protected boolean cancel;

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
}
