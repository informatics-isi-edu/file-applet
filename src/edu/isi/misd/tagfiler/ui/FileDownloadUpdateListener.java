package edu.isi.misd.tagfiler.ui;

import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
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

    private class EventTimerTask extends TimerTask {
    	
    	JButton button; 
    	
    	EventTimerTask(JButton b) {
    		button = b;
    	}

    	public void run() {
    		button.doClick();
    	}
    }
    
    private Timer eventTimer;

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
     *            the transmission number field
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
        eventTimer = new Timer(true);
    }

    /**
     * Invoked when the action fires. Has the file download retrieve the meta
     * information, fills in the file list, and enables the download button.
     */
    public void actionPerformed(ActionEvent e) {
        assert (e != null);
        final String controlNumber = controlNumberField.getText().trim();
        if (controlNumber.length() > 0) {
        	if (filesToDownload.size() > 0) {
            	fileDownloadUI.clearFields();
            	controlNumberField.setText(controlNumber);
            	eventTimer.schedule(new EventTimerTask((JButton)e.getSource()), 1000);
            	return;
        	}

            // make sure the transmission number exists
        	StringBuffer errorMessage = new StringBuffer();
        	StringBuffer status = new StringBuffer();
            if (fileDownload.verifyValidControlNumber(controlNumber, status, errorMessage)) {
                final List<String> fileList = fileDownload
                        .getFiles(controlNumber);
                if (fileList != null) {
                    for (String file : fileList) {
                        filesToDownload.add(filesToDownload.size(), file);
                    }
                }
                if (filesToDownload.size() > 0) {
                    fileDownloadUI.enableSelectDirectory();
                    fileDownloadUI.enableDestinationDirectory();
                }

                // start the session in the applet again after the files have
                // loaded
                //fileDownloadUI.refreshSession(false);
            } else {
                // start the session in the applet without waiting for a
                // response
                // from the prompt window
                //fileDownloadUI.refreshSession(false);
                JOptionPane
                        .showMessageDialog(
                                fileDownloadUI.getComponent(),
                        TagFilerProperties.getProperty(
                                "tagfiler.dialog.InvalidControlNumber",
                                new String[] { controlNumber, errorMessage.toString() }),
                                status.toString(), JOptionPane.ERROR_MESSAGE);
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
