package edu.isi.misd.tagfiler.upload;

import edu.isi.misd.tagfiler.ui.FileListener;

/**
 * Listener interface for a class that wants to be notified of events from the
 * {@link edu.isi.misd.tagfiler.upload.FileUpload} class.
 * 
 * @author David Smith
 * 
 */
public interface FileUploadListener extends FileListener {

    /**
     * Called when a dataset fails to transfer completely.
     * 
     * @param datasetName
     *            name of the dataset
     */
    public void notifyFailure(String datasetName);

}
