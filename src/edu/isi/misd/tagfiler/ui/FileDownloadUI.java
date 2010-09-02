package edu.isi.misd.tagfiler.ui;

import java.awt.Component;

/**
 * Interface for the file download UI that is used to communicate between the UI
 * and the file download components.
 * 
 * @author David Smith
 * 
 */
public interface FileDownloadUI {

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
     * @return true if the fields are valid.
     */
    public boolean validateFields();

    /**
     * 
     * @return the parent component that should be used for additional
     *         components that are generated.
     */
    public Component getComponent();

    /**
     * Clear all the user-defined fields
     */
    public void clearFields();

    /**
     * 
     * @return the log contents
     */
    public String getLog();

    /**
     * Allow the user to click the "View Log" button.
     */
    public void enableLog();

    /**
     * Don't allow the user to click the "View Log" button
     */
    public void disableLog();

    /**
     * Clear the log.
     */
    public void clearLog();

    /**
     * Enables the update operation
     */
    public void enableUpdate();

    /**
     * Disables the update operation
     */
    public void disableUpdate();
}
