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
     * Don't allow the user to click the "Upload" button
     * 
     */
    public void disableUpload();

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
     * Puts the UI in a state where it cannot be modified
     */
    public void deactivate();

    /**
     * Reloads the UI
     */
    public void reload();

    /**
     * Redirects the UI to an URL
     * 
     * @param url
     *            the URL to redirect to
     */
    public void redirect(String url);
}
