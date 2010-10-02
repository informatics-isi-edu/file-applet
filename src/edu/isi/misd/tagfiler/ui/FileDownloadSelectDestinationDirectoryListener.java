package edu.isi.misd.tagfiler.ui;

import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JTextField;

/**
 * Action listener for the select destination directory button.
 * 
 * @author David Smith
 * 
 */
public class FileDownloadSelectDestinationDirectoryListener extends
        FileDownloadActionListener {

    private StringBuffer destinationDirectoryField;

    private final JFileChooser fileChooser;

    /**
     * Constructor
     * 
     * @param ui
     *            the file download UI
     * @param field
     *            the destination directory field
     * @param chooser
     *            the file chooser
     */
    public FileDownloadSelectDestinationDirectoryListener(FileDownloadUI ui, StringBuffer field,
            JFileChooser chooser) {
        super(ui);
        assert (chooser != null);
        assert (field != null);

        fileChooser = chooser;
        destinationDirectoryField = field;
    }

    /**
     * Called when the action fires. Opens a file dialog window and writes the
     * result to the UI.
     */
    public void actionPerformed(ActionEvent e) {
        assert (e != null);
        final int result = fileChooser.showOpenDialog(fileDownloadUI
                .getComponent());
        if (JFileChooser.APPROVE_OPTION == result) {
            File selectedDirectory = fileChooser.getSelectedFile();
            destinationDirectoryField.append(selectedDirectory.getAbsolutePath());
            if (destinationDirectoryField.toString().trim().length() > 0) {
            	fileDownloadUI.enableDownload();
            }
            fileDownloadUI.getComponent().requestFocusInWindow();
        }
        // clear out the selected files, regardless
        fileChooser.setSelectedFiles(new File[] { new File("") });
    }
}
