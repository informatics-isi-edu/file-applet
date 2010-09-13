package edu.isi.misd.tagfiler;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.ws.rs.core.Cookie;

import edu.isi.misd.tagfiler.download.FileDownload;
import edu.isi.misd.tagfiler.download.FileDownloadImplementation;
import edu.isi.misd.tagfiler.exception.FatalException;
import edu.isi.misd.tagfiler.security.TagFilerSecurity;
import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.ui.CustomTagMapImplementation;
import edu.isi.misd.tagfiler.ui.FileDownloadDownloadListener;
import edu.isi.misd.tagfiler.ui.FileDownloadSelectDestinationDirectoryListener;
import edu.isi.misd.tagfiler.ui.FileDownloadUI;
import edu.isi.misd.tagfiler.ui.FileDownloadUpdateListener;
import edu.isi.misd.tagfiler.upload.FileUploadListener;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.JerseyClientUtils;
import edu.isi.misd.tagfiler.util.TagFilerProperties;
import edu.isi.misd.tagfiler.util.TagFilerPropertyUtils;

/**
 * Applet class that is used for downloading a set of files from a tagfiler
 * server to a specified destination directory.
 * 
 * @param tagfiler
 *            .server.url Required parameter for the applet that specifies the
 *            URL of the tagfiler server and path to connect to (i.e.
 *            https://tagfiler.isi.edu:443/tagfiler)
 * @param tagfiler
 *            .control.number (optional) control number used to retrieve the
 *            files
 * 
 * @author David Smith
 * 
 */
public final class TagFilerDownloadApplet extends JApplet implements
        FileDownloadUI {

    private static final long serialVersionUID = 2134123;

    // parameter name for the tagserver URL
    private static final String TAGFILER_SERVER_URL_PARAM = "tagfiler.server.url";

    private static final String TAGFILER_CONTROL_NUM_PARAM = "tagfiler.server.controlnum";

    // parameters referenced from the applet.properties file
    private static final String FONT_NAME_PROPERTY = "tagfiler.font.name";

    private static final String FONT_STYLE_PROPERTY = "tagfiler.font.style";

    private static final String FONT_SIZE_PROPERTY = "tagfiler.font.size";

    private static final String FONT_COLOR_PROPERTY = "tagfiler.font.color";

    private static final String COOKIE_NAME_PROPERTY = "tagfiler.cookie.name";

    // buttons used by the applet UI
    private JButton downloadBtn = null;

    private JButton selectDirBtn = null;

    private JButton updateBtn = null;

    private JTextField controlNumberField = null;

    private JTextField destinationDirectoryField = null;

    private String defaultControlNumber = null;

    private JList list = null;

    private JLabel statusLabel = null;

    private DefaultListModel filesToDownload = null;

    private JFileChooser fileChooser = null;

    // does the work of the file download
    private FileDownload fileDownload = null;

    // font, color used in the applet
    private Color fontColor;

    private Font font;

    // map containing the names and values of custom tags
    private CustomTagMap customTagMap = null;

    // progress bar used for uploading files
    private JProgressBar progressBar = null;

    // tagfiler server URL specified from the parameter of the applet
    private String tagFilerServerURL = null;

    // cookie maintainined in the session
    private Cookie sessionCookie = null;
    
    private Timer filesTimer;

    private class EventTimerTask extends TimerTask {
    	
    	public void run() {
    		updateBtn.doClick();
    		disableUpdate();
    	}
    }
    
    /**
     * Initializes the applet by reading parameters, polling the tagfiler
     * servlet to retrieve any authentication requests, and constructing the
     * applet UI.
     */
    public void init() {

        // load security settings
        TagFilerSecurity.loadSecuritySettings();

        sessionCookie = JerseyClientUtils.getCookieFromBrowser(this,
                TagFilerProperties.getProperty(COOKIE_NAME_PROPERTY));

        // arguments
        tagFilerServerURL = this.getParameter(TAGFILER_SERVER_URL_PARAM);
        if (tagFilerServerURL == null || tagFilerServerURL.length() == 0) {
            throw new IllegalArgumentException(TAGFILER_SERVER_URL_PARAM
                    + " must be" + " specified as a parameter to the applet.");
        }
        defaultControlNumber = this.getParameter(TAGFILER_CONTROL_NUM_PARAM);
        if (defaultControlNumber == null) {
            defaultControlNumber = "";
        }

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    createUI();

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this.getComponent(), e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

	public void start() {
    	
        	if (defaultControlNumber.length() > 0)
        	{
            	enableSelectDirectory();
            	destinationDirectoryField.setEnabled(true);
            	controlNumberField.setEnabled(false);
            	filesTimer = new Timer(true);
            	filesTimer.schedule(new EventTimerTask(), 1000);
        	}
    }
    /**
     * Create the applet UI.
     */
    private void createUI() {

        fontColor = TagFilerPropertyUtils.renderColor(FONT_COLOR_PROPERTY);
        font = TagFilerPropertyUtils.renderFont(FONT_NAME_PROPERTY,
                FONT_STYLE_PROPERTY, FONT_SIZE_PROPERTY);

        downloadBtn = new JButton(
                TagFilerProperties.getProperty("tagfiler.button.Download"));
        downloadBtn.setEnabled(false);

        selectDirBtn = new JButton(
                TagFilerProperties
                        .getProperty("tagfiler.button.SelectDirectory"));

        disableSelectDirectory();
        updateBtn = new JButton(
                TagFilerProperties.getProperty("tagfiler.button.Update"));

        filesToDownload = new DefaultListModel();

        list = new JList(filesToDownload);
        list.setVisibleRowCount(10);
        final JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setAutoscrolls(true);

        // progress bar
        progressBar = new JProgressBar(JProgressBar.HORIZONTAL);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        final Dimension progressBarDimension = new Dimension(
                Integer.parseInt(TagFilerProperties
                        .getProperty("tagfiler.progressbar.MaxWidth")),
                Integer.parseInt(TagFilerProperties
                        .getProperty("tagfiler.progressbar.MaxHeight")));
        progressBar.setMaximumSize(progressBarDimension);

        final JLabel enterControlNumberLabel = createLabel(TagFilerProperties
                .getProperty("tagfiler.label.EnterControlNumber"));
        enterControlNumberLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        enterControlNumberLabel.setAlignmentY(Component.TOP_ALIGNMENT);

        controlNumberField = new JTextField("", 12);
        controlNumberField
                .setMaximumSize(controlNumberField.getPreferredSize());
        controlNumberField.setText(defaultControlNumber);
        controlNumberField.setHorizontalAlignment(JTextField.CENTER);

        final JPanel top = createPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setAlignmentX(Component.CENTER_ALIGNMENT);
        top.setAlignmentY(Component.TOP_ALIGNMENT);

        updateBtn.setAlignmentX(CENTER_ALIGNMENT);

        top.add(enterControlNumberLabel);
        top.add(controlNumberField);
        top.add(updateBtn);
        // top.validate();
        // begin middle panel --------------------

        final JPanel middle = createPanel();
        middle.setLayout(new BoxLayout(middle, BoxLayout.X_AXIS));
        middle.setAlignmentY(Component.TOP_ALIGNMENT);

        // begin left middle --------------------------
        final JPanel leftHalf = createPanel();
        leftHalf.setLayout(new BoxLayout(leftHalf, BoxLayout.Y_AXIS));
        leftHalf.setAlignmentY(Component.TOP_ALIGNMENT);
        leftHalf.setAlignmentX(Component.LEFT_ALIGNMENT);

        // create the custom tags
        final JPanel tagAndTitlePanel = createPanel();
        tagAndTitlePanel.setLayout(new BoxLayout(tagAndTitlePanel,
                BoxLayout.Y_AXIS));

        final JPanel dirAndLabelPanel = createPanel();
        dirAndLabelPanel.setLayout(new BoxLayout(dirAndLabelPanel,
                BoxLayout.Y_AXIS));
        dirAndLabelPanel.setAlignmentX(CENTER_ALIGNMENT);
        dirAndLabelPanel.setAlignmentY(TOP_ALIGNMENT);

        final JLabel selectDestinationLabel = createLabel(TagFilerProperties
                .getProperty("tagfiler.label.SelectDestinationDir"));
        destinationDirectoryField = new JTextField("", 30);
        destinationDirectoryField.setMaximumSize(new Dimension(1000, 25));
        destinationDirectoryField.setBackground(Color.white);
        destinationDirectoryField.setEnabled(false);
        final JPanel destDirPanel = createPanel();
        destDirPanel.setLayout(new BoxLayout(destDirPanel, BoxLayout.X_AXIS));
        destDirPanel.setAlignmentX(CENTER_ALIGNMENT);
        destDirPanel.add(destinationDirectoryField);
        destDirPanel.add(selectDirBtn);

        // tagAndTitlePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dirAndLabelPanel.add(selectDestinationLabel);
        dirAndLabelPanel.add(destDirPanel);
        tagAndTitlePanel.add(dirAndLabelPanel);

        customTagMap = new CustomTagMapImplementation();
        Set<String> customTagNames = customTagMap.getTagNames();
        final JPanel customTagPanel = createPanel();
        customTagPanel.setLayout(new GridLayout(customTagNames.size(), 2));
        customTagPanel.setMaximumSize(new Dimension(customTagPanel
                .getMaximumSize().width, 22 * customTagNames.size()));
        final Font tagFont = new Font(font.getName(), Font.PLAIN,
                font.getSize());

        final Color customTagLabelColor = TagFilerPropertyUtils
                .renderColor("tagfiler.tag.label.font.color");
        for (String customTagName : customTagNames) {
            final JLabel tagLabel = createLabel(customTagName);
            tagLabel.setBorder(BorderFactory.createLineBorder(Color.gray, 1));
            tagLabel.setHorizontalAlignment(SwingConstants.LEFT);
            tagLabel.setFont(tagFont);
            tagLabel.setBackground(customTagLabelColor);
            tagLabel.setOpaque(true);
            customTagPanel.add(tagLabel);
            final Component tagComponent = customTagMap
                    .getComponent(customTagName);
            ((JTextField) tagComponent).setEditable(false);
            ((JTextField) tagComponent).setBackground(Color.white);
            customTagPanel.add(tagComponent);
        }
        final JLabel tagLabel = createLabel(TagFilerProperties
                .getProperty("tagfiler.label.CustomTags"));
        tagAndTitlePanel.add(tagLabel);
        tagAndTitlePanel.add(customTagPanel);

        leftHalf.add(tagAndTitlePanel);

        final JPanel rightHalf = createPanel();
        rightHalf.setLayout(new BoxLayout(rightHalf, BoxLayout.Y_AXIS));
        rightHalf.setAlignmentY(Component.TOP_ALIGNMENT);

        // begin top of right middle ------------------------
        final JPanel rightTop = createPanel();
        rightTop.setLayout(new BoxLayout(rightTop, BoxLayout.Y_AXIS));
        rightTop.setAlignmentY(Component.TOP_ALIGNMENT);
        rightTop.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel fileDownloadLabel = createLabel(TagFilerProperties
                .getProperty("tagfiler.label.FilesToDownload"));
        fileDownloadLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightTop.add(fileDownloadLabel, Component.CENTER_ALIGNMENT);
        rightTop.add(scrollPane);

        final JPanel rightButtonPanel = createPanel();
        rightButtonPanel.setLayout(new BoxLayout(rightButtonPanel,
                BoxLayout.X_AXIS));
        rightButtonPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        rightButtonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightButtonPanel.add(downloadBtn);
        // rightButtonPanel.add(Box.createHorizontalGlue());
        // rightButtonPanel.add(removeBtn);

        rightHalf.add(rightTop);
        rightHalf.add(rightButtonPanel);

        middle.add(leftHalf);
        middle.add(rightHalf);

        // end middle panel -------------------
        // begin bottom panel ------------------
        final JPanel bottom = createPanel();
        bottom.setLayout(new GridLayout(2, 1));

        final JPanel bottomTop = createPanel();
        bottomTop.setLayout(new BoxLayout(bottomTop, BoxLayout.Y_AXIS));
        bottomTop.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomTop.setPreferredSize(new Dimension(Integer.MAX_VALUE, 50));

        statusLabel = createLabel(TagFilerProperties
                .getProperty("tagfiler.label.DefaultDownloadStatus"));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setFont(new Font(statusLabel.getFont().getFontName(),
                Font.PLAIN, statusLabel.getFont().getSize()));

        bottomTop.add(statusLabel);
        bottomTop.add(progressBar);

        bottom.add(bottomTop);
        leftHalf.add(bottom);

        // end bottom panel -----------------------
        // file chooser window
        fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(TagFilerProperties
                .getProperty("tagfiler.filedialog.SelectDestinationDirectory"));
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);

        // the file uploader itself
        // TODO: create container for cookie so that the object reference
        // remains intact when
        // they are replaced
        try {
            fileDownload = new FileDownloadImplementation(tagFilerServerURL,
                    new TagFilerAppletUploadListener(), sessionCookie,
							  customTagMap, this);
        } catch (FatalException e) {
            e.printStackTrace();
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.message.upload.Error", new String[] { e
                            .getClass().getCanonicalName() }));
        }
        // listeners
        updateBtn.addActionListener(new FileDownloadUpdateListener(this,
                fileDownload, controlNumberField, filesToDownload));

        selectDirBtn
                .addActionListener(new FileDownloadSelectDestinationDirectoryListener(
                        this, destinationDirectoryField, fileChooser));

        downloadBtn.addActionListener(new FileDownloadDownloadListener(this,
                fileDownload, destinationDirectoryField));

        // begin main panel -----------------------
        final JPanel main = createPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(BorderFactory.createLineBorder(Color.gray, 2));
        main.add(top);
        main.add(middle);
        main.add(bottom);

        getContentPane().setBackground(Color.white);
        getContentPane().add(main);
        // end main panel ---------------------------
    }

    /**
     * Creates a label
     * 
     * @param str
     *            text of the label
     * @return new JLabel with the default styling
     */
    private JLabel createLabel(String str) {
        final JLabel label = new JLabel(str);
        label.setBackground(Color.white);
        label.setForeground(fontColor);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setFont(font);
        // label.setBorder(BorderFactory.createLineBorder(Color.red, 2));
        return label;
    }

    /**
     * Creates a panel
     * 
     * @return a new JPanel with the default styling
     */
    private JPanel createPanel() {

        final JPanel panel = new JPanel();
        panel.setBackground(Color.white);
        panel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        // panel.setBorder(BorderFactory.createLineBorder(Color.blue, 2));
        return panel;
    }

    /**
     * Enables the destination directory
     */
    public void enableDestinationDirectory() {
    	destinationDirectoryField.setEnabled(true);
    }

    /**
     * Disables the destination directory
     */
    public void disableDestinationDirectory() {
    	destinationDirectoryField.setEnabled(false);
    }

    /**
     * Enables the download button
     */
    public void enableDownload() {
        downloadBtn.setEnabled(true);
    }

    /**
     * Disables the download button
     */
    public void disableDownload() {
        downloadBtn.setEnabled(false);
    }

    /**
     * Enables the select directory button
     */
    public void enableSelectDirectory() {
        selectDirBtn.setEnabled(true);
    }

    /**
     * Disallows the adding of a directory to be invoked.
     */
    public void disableSelectDirectory() {
        selectDirBtn.setEnabled(false);
    }

    /**
     * Disables the update button
     */
    public void disableUpdate() {
        updateBtn.setEnabled(false);
    }

    /**
     * Enables the update button
     */
    public void enableUpdate() {
        updateBtn.setEnabled(true);
    }

    /**
     * Private class that listens for progress from the file download process
     * and takes action on the applet UI based on this progress.
     * 
     * @author David Smith
     * 
     */
    private class TagFilerAppletUploadListener implements FileUploadListener {

        private int filesCompleted = 0;

        private int totalFiles = 0;

        private long totalBytes = 0;

        private long bytesTransferred = 0;

        private int unit = 1;

        /**
         * Called when a dataset is complete.
         */
        public void notifySuccess(String datasetName) {
            System.out.println(TagFilerProperties.getProperty(
                    "tagfiler.message.download.DatasetSuccess",
                    new String[] { datasetName }));

            progressBar.setValue((int) totalBytes / unit);

            final StringBuffer buff = new StringBuffer(tagFilerServerURL)
            	.append(TagFilerProperties.getProperty(
                    "tagfiler.url.DownloadSuccess",
                    new String[] { datasetName }));
            redirect(buff.toString());
        }

        public void notifyFailure(String datasetName, String err) {
            String message = TagFilerProperties
            	.getProperty("tagfiler.message.download.DatasetFailure", new String[] {
                        datasetName });
		    if (err != null) {
		    	message += " " + err + ".";
		    }
		    try {
		        message = DatasetUtils.urlEncode(message);
		    } catch (UnsupportedEncodingException e) {
		        // just pass the unencoded message
		    }
		    final StringBuffer buff = new StringBuffer(tagFilerServerURL)
		            .append(TagFilerProperties.getProperty(
		                    "tagfiler.url.DownloadFailure", new String[] {
		                            datasetName, message }));
		    redirect(buff.toString());
        }

        public void notifyFailure(String datasetName, int code) {
            assert (datasetName != null && datasetName.length() > 0);
            String message = TagFilerProperties
                    .getProperty("tagfiler.message.download.DatasetFailure", new String[] {
                            datasetName });
            if (code != -1) {
            	message += " (Status Code: " + code + ").";
            }
            try {
                message = DatasetUtils.urlEncode(message);
            } catch (UnsupportedEncodingException e) {
                // just pass the unencoded message
            }
            final StringBuffer buff = new StringBuffer(tagFilerServerURL)
                    .append(TagFilerProperties.getProperty(
                            "tagfiler.url.DownloadFailure", new String[] {
                                    datasetName, message }));
            redirect(buff.toString());

        }

        public void notifyFailure(String datasetName) {
        	notifyFailure(datasetName, -1);
        }

        public void notifyLogMessage(String message) {
            System.out.println(message);
        }

        public void notifyStart(String datasetName, long totalSize) {

            totalFiles = filesToDownload.size();
            totalBytes = totalSize;
            filesCompleted = 0;

            // if the size of the transfer is beyond the integer max value,
            // make sure we divide the total and increments so that they fit in
            // the progress bar's integer units
            while ((totalBytes / unit) >= Integer.MAX_VALUE) {
                unit *= 10;
            }
            progressBar.setValue(0);
            progressBar.setMaximum((int) totalBytes / unit);

        }

        /**
         * Called when a control number update starts
         */
        public void notifyUpdateStart(String filename) {
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.label.UpdateDownloadStatus",
                    new String[] { }));
            System.out.println("Updating control number " + filename + "...");
        }

        /**
         * Called when a control number update completes
         */
        public void notifyUpdateComplete(String filename) {
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.label.DefaultDownloadStatus",
                    new String[] { }));
            System.out.println("Completed updating control number " + filename + "...");
        }

        /**
         * Called when a file transfer starts
         */
        public void notifyFileTransferStart(String filename) {
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.message.download.FileTransferStatus",
                    new String[] { Integer.toString(filesCompleted + 1),
                            Integer.toString(totalFiles) }));
            System.out.println("Transferring " + filename + "...");
        }

        /**
         * Called when a file transfer completes
         */
        public void notifyFileTransferComplete(String filename, long size) {
            filesCompleted++;
            bytesTransferred += size;
            progressBar.setValue((int) bytesTransferred / unit);
            if (filesCompleted < totalFiles) {
                updateStatus(TagFilerProperties.getProperty(
                        "tagfiler.message.download.FileTransferStatus",
                        new String[] { Integer.toString(filesCompleted + 1),
                                Integer.toString(totalFiles) }));
            }
        }

        /**
         * Called if a file is skipped and not transferred
         */
        public void notifyFileTransferSkip(String filename) {
            filesCompleted++;
        }

        public void notifyError(Throwable e) {
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.message.download.Error", new String[] { e
                            .getClass().getCanonicalName() }));
        }
    }

    /**
     * Convenience method for updating the status label
     * 
     * @param status
     */
    private void updateStatus(String status) {
    	statusLabel.setText(status);
    	statusLabel.paintImmediately(statusLabel.getVisibleRect());
    }

    /**
     * @return 1 if the custom fields that are editable by the user are all valid
     *         0 if the destination directory is not empty and the user has canceled the download.
     *        -1 if some fields are not filled.
     */
    public int validateFields() {
        int valid = 1;

        if (destinationDirectoryField.getText().trim().length() == 0
                || fileDownload.getSize() == 0) {
            valid = -1;
        }
        else {
        	// check the directory is empty to prevent overwriting
        	File dir = new File(destinationDirectoryField.getText().trim());
        	if (dir.list().length > 0) {
        		int res = JOptionPane.showConfirmDialog(getComponent(),
        			    "The destination directory is not empty.\n"
        			    + "It might overwrite some files.\n"
        			    + "Do you want to continue?",
        	            "Warning", JOptionPane.YES_NO_OPTION);
        		valid = (res == JOptionPane.YES_OPTION) ? 1 : 0;
        	}
        }
        return valid;
    }

    /**
     * @return the component representing this UI
     */
    public Component getComponent() {
        return getContentPane();
    }

    /**
     * Clears all user-editable fields
     */
    public void clearFields() {
        filesToDownload.clear();
        customTagMap.clearValues();
        destinationDirectoryField.setText("");
        controlNumberField.setText("");
    }
    
    /**
     * Redirects to an url
     */
    public void redirect(String urlStr) {
        assert (urlStr != null);
        try {
            final URL url = new URL(urlStr);
            getAppletContext().showDocument(url, "_self");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }
}
