package edu.isi.misd.tagfiler.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Abstract class that listens for actions that are fired from the UI.
 * 
 * @author David Smith
 * 
 */
abstract class FileUploadActionListener implements ActionListener {

    // the UI responsible for the upload
    protected final FileUploadUI fileUploadUI;

    /**
     * 
     * @param ui
     *            UI that contains the components that fires the upload actions.
     */
    public FileUploadActionListener(FileUploadUI ui) {
        fileUploadUI = ui;
    }

    /**
     * Called when an action occurs
     * 
     * @param e
     *            action event
     */
    abstract public void actionPerformed(ActionEvent e);
}
