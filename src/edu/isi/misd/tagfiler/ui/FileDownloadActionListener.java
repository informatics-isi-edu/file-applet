package edu.isi.misd.tagfiler.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Abstract class that listens for actions that are fired from the UI.
 * 
 * @author David Smith
 * 
 */
abstract class FileDownloadActionListener implements ActionListener {

    // the UI responsible for the download
    protected final FileDownloadUI fileDownloadUI;

    /**
     * 
     * @param ui
     *            UI that contains the components that fires the download
     *            actions.
     */
    public FileDownloadActionListener(FileDownloadUI ui) {
        fileDownloadUI = ui;
    }

    /**
     * Called when an action occurs
     * 
     * @param e
     *            action event
     */
    abstract public void actionPerformed(ActionEvent e);
}
