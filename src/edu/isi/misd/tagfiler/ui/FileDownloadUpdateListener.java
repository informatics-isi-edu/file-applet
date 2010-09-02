package edu.isi.misd.tagfiler.ui;

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import edu.isi.misd.tagfiler.download.FileDownload;
import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Action listener for the "Update" action, which retrieves file metadata from
 * the tagfiler server of a given dataset.
 * 
 * @author David Smith
 * 
 */
public class FileDownloadUpdateListener extends FileDownloadActionListener {

    private final FileDownload fileDownload;

    private final JTextField controlNumberField;

    private final DefaultListModel filesToDownload;

    /**
     * Constructor
     * 
     * @param ui
     *            the file download UI
     * @param fd
     *            the file download instance
     * @param field
     *            the control number field
     * @param destDirField
     *            the destination directory field
     * @param model
     *            the list model that shows the files to download
     */
    public FileDownloadUpdateListener(FileDownloadUI ui, FileDownload fd,
            JTextField field, DefaultListModel model) {
        super(ui);
        assert (fd != null);
        assert (field != null);
        assert (model != null);

        fileDownload = fd;
        controlNumberField = field;
        filesToDownload = model;
    }

    /**
     * Invoked when the action fires. Has the file download retrieve the meta
     * information, fills in the file list, and enables the download button.
     */
    public void actionPerformed(ActionEvent e) {
        assert (e != null);
        final String controlNumber = controlNumberField.getText().trim();
        if (controlNumber.length() > 0) {
            final List<String> fileList = fileDownload.getFiles(controlNumber);
            if (fileList != null) {
                for (String file : fileList) {
                    filesToDownload.add(filesToDownload.size(), file);
                }
            }
            if (filesToDownload.size() > 0) {
                fileDownloadUI.enableDownload();
            }
        } else {
            JOptionPane
                    .showMessageDialog(
                            fileDownloadUI.getComponent(),
                            TagFilerProperties
                                    .getProperty("tagfiler.dialog.MissingControlNumber"),
                            "", JOptionPane.ERROR_MESSAGE);
        }
    }
}
