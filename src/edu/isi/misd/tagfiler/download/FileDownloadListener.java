package edu.isi.misd.tagfiler.download;

import edu.isi.misd.tagfiler.ui.FileListener;

/**
 * Listener interface for a class that wants to be notified of events from the
 * {@link edu.isi.misd.tagfiler.upload.FileUpload} class.
 * 
 * @author David Smith
 * 
 */
public interface FileDownloadListener extends FileListener {

    /**
     * Called when a dataset fails to transfer completely.
     * 
     * @param datasetName
     *            name of the dataset
     */
    public void notifyFailure(String datasetName, String message);

    /**
     * Called when a Dataset update starts
     * @param filename
     *            name of the dataset being transferred.
     */
    public void notifyUpdateStart(String filename);

    /**
     * Called when a Dataset update completes
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
