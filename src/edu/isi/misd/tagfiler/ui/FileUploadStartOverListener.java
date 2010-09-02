package edu.isi.misd.tagfiler.ui;

import java.awt.event.ActionEvent;

/**
 * Action listener that fires when the user wants to remove a file from the list
 * of files to remove.
 * 
 * @author David Smith
 * 
 */
public class FileUploadStartOverListener extends FileUploadActionListener {

    /**
     * 
     * @param ui
     *            the file upload UI
     * @param model
     *            the default list model
     * @param l
     *            the list
     */
    public FileUploadStartOverListener(FileUploadUI ui) {
        super(ui);
    }

    /**
     * called when the start over action is invoked.
     */
    public void actionPerformed(ActionEvent e) {
        fileUploadUI.reload();
    }
}
