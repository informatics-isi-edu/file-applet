package edu.isi.misd.tagfiler.ui;


/**
 * Interface for the file download UI that is used to communicate between the UI
 * and the file download components.
 * 
 * @author David Smith
 * 
 */
public interface FileDownloadUI extends FileUI {

    /**
     * Enables the destination directory
     */
    public void enableDestinationDirectory();

    /**
     * Disables the destination directory
     */
    public void disableDestinationDirectory();

    /**
     * Enables the download operation
     */
    public void enableDownload();

    /**
     * Enables the select destination directory operation
     */
    public void enableSelectDirectory();

    /**
     * Disables the download operation
     */
    public void disableDownload();

    /**
     * Disables the select destination directory operation
     */
    public void disableSelectDirectory();

    /**
     * Validate all user-defined fields.
     * 
     * @return 1 if the custom fields that are editable by the user are all
     *         valid 0 if the destination directory is not empty and the user
     *         has canceled the download. -1 if some fields are not filled.
     */
    public int validateFields();

    /**
     * Enables the update operation
     */
    public void enableUpdate();

    /**
     * Disables the update operation
     */
    public void disableUpdate();
}
