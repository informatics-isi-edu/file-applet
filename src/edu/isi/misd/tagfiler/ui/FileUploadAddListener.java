package edu.isi.misd.tagfiler.ui;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;

import edu.isi.misd.tagfiler.upload.FileUpload;

/**
 * Action that is called when the component for adding a directory to the file
 * upload fires.
 * 
 * @author David Smith
 * 
 */
public class FileUploadAddListener extends FileUploadActionListener {

    private final JFileChooser fileChooser;

    private final Container parentContainer;

    private final DefaultListModel filesToUpload;

    private final FileUpload fileUpload;

    /**
     * Excludes "." and ".." from directory lists in case the client is
     * UNIX-based.
     */
    private static final FilenameFilter excludeDirFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return (!name.equals(".") && !name.equals(".."));
        }
    };

    /**
     * 
     * @param ui
     *            the file upload UI
     * @param upload
     *            the file upload instance
     * @param chooser
     *            the file chooser instance
     * @param parent
     *            the container that acts as the parent of any pop-ups
     * @param model
     *            the list model
     */
    public FileUploadAddListener(FileUploadUI ui, FileUpload upload,
            JFileChooser chooser, Container parent, DefaultListModel model) {
        super(ui);
        assert (upload != null);
        assert (chooser != null);
        assert (parent != null);
        assert (model != null);

        fileUpload = upload;
        fileChooser = chooser;
        parentContainer = parent;
        filesToUpload = model;
    }

    /**
     * Called when the user wants to add a directory to the upload.
     */
    public void actionPerformed(ActionEvent e) {
        assert (e != null);
        final int result = fileChooser.showOpenDialog(parentContainer);
        if (JFileChooser.APPROVE_OPTION == result) {
            File selectedDirectory = fileChooser.getSelectedFile();
            fileUpload.setBaseDirectory(selectedDirectory.getAbsolutePath());
            filesToUpload.clear();
            addFilesToList(new File[] { selectedDirectory });
            fileUploadUI.enableUpload();
            fileUploadUI.enableRemove();
            fileUploadUI.getComponent().requestFocusInWindow();
        }
        // clear out the selected files, regardless
        fileChooser.setSelectedFiles(new File[] { new File("") });
    }

    /**
     * Adds the files to the list, if an entry is a directory then all its files
     * are added as well.
     * 
     * @param files
     *            the files to add
     */
    private void addFilesToList(File[] files) {
        assert (files != null);
        final List<File> dirs = new LinkedList<File>();
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                dirs.add(files[i].getAbsoluteFile());
            } else if (files[i].isFile()) {
                filesToUpload.add(filesToUpload.getSize(),
                        files[i].getAbsolutePath());
            }
        }

        // go through any directories
        for (File dir : dirs) {
            final File[] children = dir.listFiles(excludeDirFilter);
            if (children != null) {
                addFilesToList(children);
            }
        }
    }
}
