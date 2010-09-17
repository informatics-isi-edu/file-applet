package edu.isi.misd.tagfiler.upload;

/**
 * Listener interface for a class that wants to be notified of events from the
 * {@link edu.isi.misd.tagfiler.upload.FileUpload} class.
 * 
 * @author David Smith
 * 
 */
public interface FileUploadListener {

    /**
     * Called when a dataset is transferred successfully.
     * 
     * @param datasetName
     *            name of the dataset
     */
    public void notifySuccess(String datasetName);

    /**
     * Called when a dataset fails to transfer completely.
     * 
     * @param datasetName
     *            name of the dataset
     */
    public void notifyFailure(String datasetName);

    /**
     * Called when a dataset fails to transfer completely.
     * 
     * @param datasetName
     *            name of the dataset
     */
    public void notifyFailure(String datasetName, String message);

    /**
     * Called when a dataset fails to transfer completely.
     * 
     * @param datasetName
     *            name of the dataset
     * @param message
     *            the failure message
     */
    public void notifyFailure(String datasetName, int code);

    /**
     * Called when the FileUpload logs a message that could be displayed in a
     * log or UI
     * 
     * @param message
     *            the message to display.
     *            the response status.
     */
    public void notifyLogMessage(String message);

    /**
     * Called before a file transfer operation begins.
     * 
     * @param filename
     *            name of the file that will be transferred.
     */
    public void notifyFileTransferStart(String filename);

    /**
     * Called after a file transfer operation completes successfully.
     * 
     * @param filename
     *            name of the file that was transferred
     * @param totalBytes
     *            size of the file that was transferred.
     */
    public void notifyFileTransferComplete(String filename, long totalBytes);

    /**
     * Called before a dataset transfer starts.
     * 
     * @param datasetName
     *            name of the dataset being transferred.
     * @param totalBytes
     *            total amount of bytes to transfer for the dataset.
     */
    public void notifyStart(String datasetName, long totalBytes);

    /**
     * Called when a file in the dataset is skipped, in case the listener is
     * counting files.
     * 
     * @param filename
     *            name of the file that was skipped.
     */
    public void notifyFileTransferSkip(String filename);

    /**
     * Called when a fatal error occurs that should be reported to the UI
     */
    public void notifyError(Throwable t);
    
    /**
     * Called when a transmission number update starts
     * @param filename
     *            name of the dataset being transferred.
     */
    public void notifyUpdateStart(String filename);

    /**
     * Called when a transmission number update completes
     * @param filename
     *            name of the dataset being transferred.
     */
    public void notifyUpdateComplete(String filename);

    /**
     * Called when retrieving files starts
     * @param size
     *            number of files to be retrieved.
     */
    public void notifyRetrieveStart(int size);

    /**
     * Called when the retrieving of a file completed
     * @param name
     *            the retrieved file.
     */
    public void notifyFileRetrieveComplete(String filename);
}
