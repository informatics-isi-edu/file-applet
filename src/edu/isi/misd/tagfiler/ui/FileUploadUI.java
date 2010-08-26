package edu.isi.misd.tagfiler.ui;

import java.awt.Component;

/**
 * Interface for the file upload user interface that is needed to communicate
 * between the file upload components and the UI.
 * 
 * @author David Smith
 * 
 */
public interface FileUploadUI {

    /**
     * Allow the user to click the "Upload" button
     * 
     */
    public void enableUpload();

    /**
     * Allow the user to click the "Remove" button
     */
    public void enableRemove();

    /**
     * Don't allow the user to click the "Upload" button
     * 
     */
    public void disableUpload();

    /**
     * Don't allow the user to click the "Remove" button.
     */
    public void disableRemove();

    /**
     * Allow the user to click the "Add" button.
     */
    public void enableAdd();

    /**
     * Don't allow the user to click the "Add" button.
     */
    public void disableAdd();

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
}
