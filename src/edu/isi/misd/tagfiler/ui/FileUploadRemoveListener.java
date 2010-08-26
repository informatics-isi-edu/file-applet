package edu.isi.misd.tagfiler.ui;

import java.awt.event.ActionEvent;

import javax.swing.DefaultListModel;
import javax.swing.JList;

/**
 * Action listener that fires when the user wants to remove a file from the list
 * of files to remove.
 * 
 * @author David Smith
 * 
 */
public class FileUploadRemoveListener extends FileUploadActionListener {

    private final DefaultListModel filesToUpload;

    private final JList list;

    /**
     * 
     * @param ui
     *            the file upload UI
     * @param model
     *            the default list model
     * @param l
     *            the list
     */
    public FileUploadRemoveListener(FileUploadUI ui, DefaultListModel model,
            JList l) {
        super(ui);
        assert (model != null);
        assert (l != null);

        filesToUpload = model;
        list = l;
    }

    /**
     * called when the remove action is invoked.
     */
    public void actionPerformed(ActionEvent e) {
        int selectedIndex = -1;
        while ((selectedIndex = list.getSelectedIndex()) >= 0) {
            filesToUpload.remove(selectedIndex);
        }
        if (filesToUpload.size() == 0) {
            fileUploadUI.disableUpload();
            fileUploadUI.disableRemove();
        }
    }
}
