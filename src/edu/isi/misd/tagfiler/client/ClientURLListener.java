/**
 * 
 */
package edu.isi.misd.tagfiler.client;

/**
 * @author Serban Voinea
 *
 */
public interface ClientURLListener {
	
    /**
     * Callback to notify success for the entire upload/download process
     * 
     */
	public void notifySuccess();
	
    /**
     * Callback to notify a failure during the upload/download process
     * 
     * @param err
     *            the error message
     */
	public void notifyFailure(String err);
	
    /**
     * Callback to notify a chunk block transfer completion during the upload/download process
     * 
     * @param size
     *            the chunk size
     */
	public void notifyChunkTransfered(long size);
	
    /**
     * Callback to notify a file transfer completion during the upload/download process
     * 
     * @param size
     *            the chunk size
     */
	public void notifyFileTransfered(long size);
	
    /**
     * Callback to notify an error during the upload/download process
     * 
     * @param err
     *            the error message
     * @param e
     *            the exception
     */
	public void notifyError(String err, Exception e);
	
    /**
     * Callback to get the cookie.
     * 
     * @return the cookie or null if cookies are not used
     */
	public String getCookie();
	
    /**
     * Callback to update the session cookie. Should have an empty body if cookies are not used.
    */
	public void updateSessionCookie();
	
    /**
     * Get the dataset name
     * @return the dataset name
     */
	public String getDataset();

    /**
     * @return true if the checksum is enabled
     */
    public boolean isEnableChecksum();

    /**
     * @return the dataset id
     */
    public String getDatasetId();

}
