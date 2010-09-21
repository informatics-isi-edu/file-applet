package edu.isi.misd.tagfiler;

import javax.ws.rs.core.Cookie;

/**
 * Interface for a file transfer operation.
 * 
 * @author David Smith
 * 
 */
public interface FileTransfer {

    /**
     * Updates the session cookie maintained by the file transfer session. Must
     * be thread-safe.
     * 
     * @param sessionCookie
     *            the new session cookie
     */
    public void updateSessionCookie(Cookie sessionCookie);

    /**
     * Retrieves the session cookie maintained by the file transfer session.
     * Must be thread-safe.
     * 
     * @return the session cookie
     */
    public Cookie getSessionCookie();
}
