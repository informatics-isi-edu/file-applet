package edu.isi.misd.tagfiler.ui;

import java.awt.Container;
import java.awt.event.ActionEvent;

import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Action listener that fires when the user wants to view the log window.
 * 
 * @author David Smith
 * 
 */
public class FileUploadLogListener extends FileUploadActionListener {

    private final Container parentContainer;

    /**
     * 
     * @param ui
     *            the file upload UI
     * @param parent
     *            the parent component
     */
    public FileUploadLogListener(FileUploadUI ui, Container parent) {
        super(ui);
        assert (parent != null);
        parentContainer = parent;
    }

    /**
     * Called when the user wants to view the log.
     */
    public void actionPerformed(ActionEvent e) {
        LogDialog.showDialog(parentContainer,
                TagFilerProperties.getProperty("tagfiler.title.logwindow"),
                fileUploadUI.getLog());
    }

}
