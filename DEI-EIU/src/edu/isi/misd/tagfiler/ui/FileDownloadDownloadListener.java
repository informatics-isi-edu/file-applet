package edu.isi.misd.tagfiler.ui;

import java.awt.event.ActionEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JOptionPane;
import javax.swing.JTextField;

import edu.isi.misd.tagfiler.download.FileDownload;
import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Action class for the "Download" operation.
 * 
 * @author David Smith
 * 
 */
public class FileDownloadDownloadListener extends FileDownloadActionListener {

    private final FileDownload fileDownload;

    private final JTextField destinationDirectoryField;

    private final ExecutorService threadExecutor;

    /**
     * Constructor
     * 
     * @param ui
     *            file download UI
     * @param fd
     *            the file download instance
     * @param model
     *            the list of files to download (for clearing)
     * @param destDirField
     *            the destination directory field
     */
    public FileDownloadDownloadListener(FileDownloadUI ui, FileDownload fd,
            JTextField destDirField) {
        super(ui);
        assert (fd != null);
        assert (destDirField != null);

        fileDownload = fd;
        destinationDirectoryField = destDirField;

        threadExecutor = Executors.newFixedThreadPool(1);
    }

    /**
     * Called when the download action fires. The UI buttons are disabled, the
     * file download occurs, the fields are cleared, and the buttons are
     * re-enabled.
     */
    public void actionPerformed(ActionEvent e) {
        assert (e != null);
        int valid = fileDownloadUI.validateFields();
        if (valid == 1) {
            fileDownloadUI.disableDownload();
            fileDownloadUI.disableSelectDirectory();
            fileDownloadUI.disableUpdate();

            threadExecutor.execute(new RunDownloadTask());
        } else if (valid == -1) {
            JOptionPane.showMessageDialog(fileDownloadUI.getComponent(),
                    TagFilerProperties
                            .getProperty("tagfiler.dialog.FieldsNotFilled"));
            fileDownloadUI.getComponent().requestFocusInWindow();
        }
    }

    /**
     * Tasks that runs the actual file download
     * 
     * @author David Smith
     * 
     */
    private class RunDownloadTask implements Runnable {
        public void run() {
            fileDownload.downloadFiles(destinationDirectoryField.getText()
                    .trim());

            fileDownloadUI.clearFields();
            fileDownloadUI.enableUpdate();
            fileDownloadUI.enableSelectDirectory();

            fileDownloadUI.getComponent().requestFocusInWindow();
        }
    }

    /**
     * In case we shutdown, kill the file upload thread.
     */
    protected void finalize() {
        if (threadExecutor != null) {
            threadExecutor.shutdown();
        }
    }
}
