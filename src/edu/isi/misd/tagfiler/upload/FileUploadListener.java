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
     * Called when the FileUpload logs a message that could be displayed in a
     * log or UI
     * 
     * @param message
     *            the message to display.
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
}
