package edu.isi.misd.tagfiler.download;

import java.util.List;

/**
 * Interface for a file download process to the tag server.
 * 
 * @author Serban Voinea
 * 
 */
public interface FileDownload {

    /**
     * Returns the total number of bytes to be downloaded.
     */
    public long getSize();

    /**
     * Returns the list of the file names to be downloaded.
     * 
     * @param dataset
     *            the dataset name of the files to retrieve
     */
    public List<String> getFiles(String dataset);

    /**
     * Performs the dataset download.
     * 
     * @param destinationDir
     *            directory to save the files
     * @return true if all files were downloaded successfully
     */
    public boolean downloadFiles(String destinationDir);

    /**
     * Checks with the tagfiler server to verify that a dataset by the control
     * number already exists
     * 
     * @param controlNumber
     *            the control number to check
     * @return true if a dataset with the particular control number exists,
     *         false otherwise
     */
    public boolean verifyValidControlNumber(String controlNumber);

    /**
     * Called when a control number update starts
     * @param filename
     *            name of the dataset being transferred.
     */
    public void notifyUpdateStart(String filename);

    /**
     * Called when a control number update completes
     * @param filename
     *            name of the dataset being transferred.
     */
    public void notifyUpdateComplete(String filename);
}
