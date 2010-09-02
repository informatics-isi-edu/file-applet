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
     * Sets the base directory for the file upload.
     * 
     * @param baseDir
     *            base directory
     */
    public void setBaseDirectory(String baseDir);

    /**
     * Returns the list of the file names to be downloaded.
     */
    public List<String> getFiles();

    /**
     * Performs the dataset download.
     * @return true if all files were downloaded successfully
     */
    public boolean downloadFiles();

}
