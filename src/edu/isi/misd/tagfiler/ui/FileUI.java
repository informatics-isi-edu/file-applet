package edu.isi.misd.tagfiler.ui;

import java.awt.Component;

/**
 * Interface for a file transfer user interface.
 * 
 * @author smithd
 * 
 */
public interface FileUI {

    /**
     * Redirects the UI to an URL
     * 
     * @param url
     *            the URL to redirect to
     */
    public void redirect(String url);

    /**
     * 
     * @return the parent component that should be used for additional
     *         components that are generated.
     */
    public Component getComponent();


    /**
     * Tells the UI to clear all of its user-editable fields.
     */
    public void clearFields();

    /**
     * Tells the UI to deactivate all of its user-actionable components (i.e.
     * buttons, fields)
     */
    public void deactivate();

    /**
     * Reloads the UI
     */
    public void reload();

}
