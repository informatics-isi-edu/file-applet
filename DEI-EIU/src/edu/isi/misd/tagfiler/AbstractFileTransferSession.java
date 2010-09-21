package edu.isi.misd.tagfiler;

import javax.ws.rs.core.Cookie;

/**
 * Implementation of {@code edu.isi.misd.tagfiler.FileTransfer}
 * 
 * @author David Smith
 * 
 */
public class AbstractFileTransferSession implements FileTransfer {
    protected Cookie cookie = null;

    /**
     * Updates the session cookie
     */
    public synchronized void updateSessionCookie(Cookie sessionCookie) {
        cookie = sessionCookie;
    }

    /**
     * @return the session cookie
     */
    public synchronized Cookie getSessionCookie() {
        return cookie;
    }
}
