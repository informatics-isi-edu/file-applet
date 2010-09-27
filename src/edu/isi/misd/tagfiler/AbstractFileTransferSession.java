package edu.isi.misd.tagfiler;

/**
 * Implementation of {@code edu.isi.misd.tagfiler.FileTransfer}
 * 
 * @author David Smith
 * 
 */
public class AbstractFileTransferSession implements FileTransfer {
    protected String cookie = null;

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
