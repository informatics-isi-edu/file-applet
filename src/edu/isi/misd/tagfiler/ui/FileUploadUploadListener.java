package edu.isi.misd.tagfiler.ui;

import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;

import edu.isi.misd.tagfiler.upload.FileUpload;
import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Action listener that is fired when the upload action is invoked.
 * 
 * @author David Smith
 * 
 */
public class FileUploadUploadListener extends FileUploadActionListener {

    private final DefaultListModel filesToUpload;

    private final FileUpload fileUpload;

    private final ExecutorService threadExecutor;

    /**
     * 
     * @param ui
     *            the file upload UI
     * @param upload
     *            the file upload object
     * @param model
     *            the list model
     */
    public FileUploadUploadListener(FileUploadUI ui, FileUpload upload,
            DefaultListModel model) {
        super(ui);
        assert (upload != null);
        assert (model != null);

        filesToUpload = model;
        fileUpload = upload;
        threadExecutor = Executors.newFixedThreadPool(1);
    }

    /**
     * Called when the user wants to upload. It will disable buttons so that two
     * uploads can't occur simultaneously and run the file upload in a separate
     * thread so that the GUI can be updated while the file upload occurs.
     */
    public void actionPerformed(ActionEvent e) {
        assert (e != null);

        // make sure the fields are valid
        if (fileUploadUI.validateFields() && filesToUpload.size() > 0) {
            fileUploadUI.clearLog();
            fileUploadUI.disableUpload();
            fileUploadUI.disableAdd();
            fileUploadUI.disableRemove();

            fileUploadUI.enableLog();
            // run in a separate thread so that the UI returns
            threadExecutor.execute(new RunUploadTask());
        } else {
            JOptionPane.showMessageDialog(fileUploadUI.getComponent(),
                    TagFilerProperties
                            .getProperty("tagfiler.dialog.FieldsNotFilled"));
        }
    }

    /**
     * Tasks that runs the actual file upload
     * 
     * @author David Smith
     * 
     */
    private class RunUploadTask implements Runnable {
        public void run() {
            List<String> files = new LinkedList<String>();
            for (int i = 0; i < filesToUpload.size(); i++) {
                files.add((String) filesToUpload.get(i));
            }
            fileUpload.postFileData(files);
            filesToUpload.clear();
            fileUploadUI.clearFields();
            fileUploadUI.enableAdd();
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
