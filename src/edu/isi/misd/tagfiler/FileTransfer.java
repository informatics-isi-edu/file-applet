package edu.isi.misd.tagfiler;

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
    public void updateSessionCookie(String sessionCookie);

    /**
     * Retrieves the session cookie maintained by the file transfer session.
     * Must be thread-safe.
     * 
     * @return the session cookie
     */
    public String getSessionCookie();
    
    /**
     * @return true if the checksum is enabled
     */
    public boolean isEnableChecksum();

    /**
     * Set the checksum switch
     * @param enableChecksum
     *            the checksum switch
     */
	public void setEnableChecksum(boolean enableChecksum);
}
