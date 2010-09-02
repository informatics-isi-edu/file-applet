package edu.isi.misd.tagfiler.ui;

import java.awt.event.ActionEvent;

import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Action class for the "View Log" button.
 * 
 * @author David Smith
 * 
 */
public class FileDownloadLogListener extends FileDownloadActionListener {

    public FileDownloadLogListener(FileDownloadUI ui) {
        super(ui);
    }

    /**
     * Called when the user wants to view the log.
     */
    public void actionPerformed(ActionEvent e) {
        LogDialog.showDialog(fileDownloadUI.getComponent(),
                TagFilerProperties.getProperty("tagfiler.title.logwindow"),
                fileDownloadUI.getLog());
    }
}
