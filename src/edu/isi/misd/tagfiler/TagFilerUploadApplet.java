package edu.isi.misd.tagfiler;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
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

import netscape.javascript.JSException;
import netscape.javascript.JSObject;
import edu.isi.misd.tagfiler.security.TagFilerSecurity;
import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.ui.CustomTagMapImplementation;
import edu.isi.misd.tagfiler.ui.FileUploadAddListener;
import edu.isi.misd.tagfiler.ui.FileUploadLogListener;
import edu.isi.misd.tagfiler.ui.FileUploadStartOverListener;
import edu.isi.misd.tagfiler.ui.FileUploadUI;
import edu.isi.misd.tagfiler.ui.FileUploadUploadListener;
import edu.isi.misd.tagfiler.upload.FileUpload;
import edu.isi.misd.tagfiler.upload.FileUploadImplementation;
import edu.isi.misd.tagfiler.upload.FileUploadListener;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.JerseyClientUtils;
import edu.isi.misd.tagfiler.util.TagFilerProperties;
import edu.isi.misd.tagfiler.util.TagFilerPropertyUtils;

/**
 * Applet class that is used for uploading a directory of files from an end
 * user's browser to a tagfiler servlet with custom tags associated with it. The
 * dataset name is first generated as a random 10-digit number and is created as
 * a URL (representing the dataset query) in the dataset with the custom tags
 * assigned to it. Each file that is uploaded in the dataset has its checksum
 * stored in an attribute and is given a name containing the dataset name and
 * the path relative to the directory that was chosen for uploading from the
 * client machine. An attribute that contains the dataset name is also stored so
 * that the collection of files can be easily queried.
 * 
 * @param tagfiler
 *            .server.url Required parameter for the applet that specifies the
 *            URL of the tagfiler server and path to connect to (i.e.
 *            https://tagfiler.isi.edu:443/tagfiler)
 * 
 * @author David Smith
 * 
 */
public final class TagFilerUploadApplet extends JApplet implements FileUploadUI {

    private static final long serialVersionUID = 2134123;

    // parameter name for the tagserver URL
    private static final String TAGFILER_SERVER_URL_PARAM = "tagfiler.server.url";

    // parameters referenced from the applet.properties file
    private static final String FONT_NAME_PROPERTY = "tagfiler.font.name";

    private static final String FONT_STYLE_PROPERTY = "tagfiler.font.style";

    private static final String FONT_SIZE_PROPERTY = "tagfiler.font.size";

    private static final String FONT_COLOR_PROPERTY = "tagfiler.font.color";

    private static final String COOKIE_NAME_PROPERTY = "tagfiler.cookie.name";

    // buttons used by the applet UI
    private JButton uploadBtn = null;

    private JButton addBtn = null;

    private JButton startOverBtn = null;

    private JButton logBtn = null;

    private JList list = null;

    private JLabel statusLabel = null;

    private JTextField controlNumberField = null;

    private DefaultListModel filesToUpload = null;

    private JFileChooser fileChooser = null;

    // does the work of the file upload
    private FileUpload fileUpload = null;

    // font, color used in the applet
    private Color fontColor;

    private Font font;

    // map containing the names and values of custom tags
    private CustomTagMap customTagMap = null;

    // progress bar used for uploading files
    private JProgressBar progressBar = null;

    // tagfiler server URL specified from the parameter of the applet
    private String tagFilerServerURL = null;

    // contains the logging information for an upload session
    private StringBuffer logBuffer = new StringBuffer();

    // cookie maintainined in the session
    private Cookie sessionCookie = null;

    /**
     * Initializes the applet by reading parameters, polling the tagfiler
     * servlet to retrieve any authentication requests, and constructing the
     * applet UI.
     */
    public void init() {

        // load any security settings
        TagFilerSecurity.loadSecuritySettings();

        sessionCookie = JerseyClientUtils.getCookieFromBrowser(this,
                TagFilerProperties.getProperty(COOKIE_NAME_PROPERTY));

        // arguments
        tagFilerServerURL = this.getParameter(TAGFILER_SERVER_URL_PARAM);
        if (tagFilerServerURL == null || tagFilerServerURL.length() == 0) {
            throw new IllegalArgumentException(TAGFILER_SERVER_URL_PARAM
                    + " must be" + " specified as a parameter to the applet.");
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

    /**
     * Create the applet UI.
     */
    private void createUI() {

        fontColor = TagFilerPropertyUtils.renderColor(FONT_COLOR_PROPERTY);
        font = TagFilerPropertyUtils.renderFont(FONT_NAME_PROPERTY,
                FONT_STYLE_PROPERTY, FONT_SIZE_PROPERTY);

        uploadBtn = new JButton(
                TagFilerProperties.getProperty("tagfiler.button.Upload"));
        uploadBtn.setEnabled(false);

        addBtn = new JButton(
                TagFilerProperties.getProperty("tagfiler.button.Browse"));

        startOverBtn = new JButton(
                TagFilerProperties.getProperty("tagfiler.button.StartOver"));

        logBtn = new JButton(
                TagFilerProperties.getProperty("tagfiler.button.ViewLog"));
        logBtn.setEnabled(false);
        filesToUpload = new DefaultListModel();

        list = new JList(filesToUpload);
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

        controlNumberField = new JTextField(
                TagFilerProperties
                        .getProperty("tagfiler.label.DefaultControlNumber"));
        controlNumberField.setBackground(Color.white);

        controlNumberField.setFont(new Font(font.getFontName(),
                font.getStyle(), font.getSize() + 2));
        controlNumberField.setAlignmentX(Component.CENTER_ALIGNMENT);
        controlNumberField.setForeground(fontColor);
        controlNumberField.setHorizontalAlignment(SwingConstants.CENTER);
        controlNumberField.setEditable(false);
        controlNumberField.setBorder(BorderFactory.createEmptyBorder());

        final JLabel lbl = createLabel(TagFilerProperties
                .getProperty("tagfiler.label.SelectDirectoryToUpload"));

        final JPanel top = createPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setAlignmentX(Component.CENTER_ALIGNMENT);
        top.setAlignmentY(Component.TOP_ALIGNMENT);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        addBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        // top.add(controlNumberLabel);
        top.add(controlNumberField);
        top.add(lbl, Component.CENTER_ALIGNMENT);
        top.add(addBtn, Component.CENTER_ALIGNMENT);
        top.validate();
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
        tagAndTitlePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
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
            customTagPanel.add(customTagMap.getComponent(customTagName));
        }
        final JLabel tagLabel = createLabel(TagFilerProperties
                .getProperty("tagfiler.label.FillCustomTags"));
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
        JLabel fileUploadLabel = createLabel(TagFilerProperties
                .getProperty("tagfiler.label.FilesToUpload"));
        fileUploadLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightTop.add(fileUploadLabel, Component.CENTER_ALIGNMENT);
        rightTop.add(scrollPane);

        final JPanel rightButtonPanel = createPanel();
        rightButtonPanel.setLayout(new BoxLayout(rightButtonPanel,
                BoxLayout.X_AXIS));
        rightButtonPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        rightButtonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightButtonPanel.add(uploadBtn);
        rightButtonPanel.add(Box.createHorizontalGlue());
        rightButtonPanel.add(startOverBtn);

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
                .getProperty("tagfiler.label.DefaultUploadStatus"));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setFont(new Font(statusLabel.getFont().getFontName(),
                Font.PLAIN, statusLabel.getFont().getSize()));

        bottomTop.add(statusLabel);
        bottomTop.add(progressBar);

        logBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        bottomTop.add(logBtn);
        final JPanel bottomMiddle = createPanel();
        bottomMiddle.add(logBtn, Component.CENTER_ALIGNMENT);

        bottom.add(bottomTop);
        bottom.add(bottomMiddle);
        leftHalf.add(bottom);

        // end bottom panel -----------------------
        // file chooser window
        fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(TagFilerProperties
                .getProperty("tagfiler.filedialog.SelectDirectoryToUpload"));
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);

        // the file uploader itself
        // TODO: create container for cookie so that the object reference
        // remains intact when
        // they are replaced
        fileUpload = new FileUploadImplementation(tagFilerServerURL,
                new TagFilerAppletUploadListener(), customTagMap, sessionCookie);

        // listeners
        uploadBtn.addActionListener(new FileUploadUploadListener(this,
                fileUpload, filesToUpload));
        addBtn.addActionListener(new FileUploadAddListener(this, fileUpload,
                fileChooser, getContentPane(), filesToUpload));

        startOverBtn.addActionListener(new FileUploadStartOverListener(this));

        logBtn.addActionListener(new FileUploadLogListener(this,
                getContentPane()));

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
     * Allows the upload action to be invoked.
     */
    public void enableUpload() {
        uploadBtn.setEnabled(true);
    }

    /**
     * Disallows the upload action to be invoked.
     */
    public void disableUpload() {
        uploadBtn.setEnabled(false);
    }

    /**
     * Allows the adding of a directory to be invoked.
     */
    public void enableAdd() {
        addBtn.setEnabled(true);
    }

    /**
     * Disallows the adding of a directory to be invoked.
     */
    public void disableAdd() {
        addBtn.setEnabled(false);
    }

    /**
     * Allows the log view action to be invoked.
     */
    public void enableLog() {
        logBtn.setEnabled(true);
    }

    /**
     * Disallows the log view action to be invoked.
     */
    public void disableLog() {
        logBtn.setEnabled(false);
    }

    /**
     * Private class that listens for progress from the file upload process and
     * takes action on the applet UI based on this progress.
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
            addLogMessage(TagFilerProperties
                    .getProperty("tagfiler.message.upload.DatasetSuccess"));

            progressBar.setValue((int) totalBytes / unit);
            try {
                JSObject window = JSObject.getWindow(TagFilerUploadApplet.this);
                window.call(
                        "setDatasetLink",
                        new String[] {
                                TagFilerProperties
                                        .getProperty("tagfiler.div.link.id"),
                                DatasetUtils.getDatasetQuery(datasetName,
                                        tagFilerServerURL) });

            } catch (JSException e) {
                // don't throw
                addLogMessage("Missing div tag for "
                        + TagFilerProperties
                                .getProperty("tagfiler.div.link.id")
                        + " in html body, cannot display link.");
            }
            updateStatus(TagFilerProperties
                    .getProperty("tagfiler.message.upload.DatasetSuccess"));
        }

        public void notifyFailure(String datasetName) {
            addLogMessage(TagFilerProperties
                    .getProperty("tagfiler.message.upload.DatasetFailure"));
            try {
                JSObject window = JSObject.getWindow(TagFilerUploadApplet.this);
                window.call(
                        "setDatasetLink",
                        new String[] {
                                TagFilerProperties
                                        .getProperty("tagfiler.div.link.id"),
                                DatasetUtils.getDatasetQuery(datasetName,
                                        tagFilerServerURL) });
            } catch (JSException e) {
                // don't throw
                addLogMessage("Missing div tag for "
                        + TagFilerProperties
                                .getProperty("tagfiler.div.link.id")
                        + " in html body, cannot display link.");
            }
            updateStatus(TagFilerProperties
                    .getProperty("tagfiler.message.upload.DatasetFailure"), Color.red);
        }

        public void notifyLogMessage(String message) {
            addLogMessage(message);
            System.out.println(message);
        }

        public void notifyStart(String datasetName, long totalSize) {

            controlNumberField.setText(TagFilerProperties.getProperty(
                    "tagfiler.label.ControlNumberLabel",
                    new String[] { datasetName }));

            totalFiles = filesToUpload.size();
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
         * Called when a file transfer starts
         */
        public void notifyFileTransferStart(String filename) {
            updateStatus(TagFilerProperties.getProperty(
                    "tagfiler.message.upload.FileTransferStatus",
                    new String[] { Integer.toString(filesCompleted + 1),
                            Integer.toString(totalFiles) }));
            addLogMessage("Transferring " + filename + "...");
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
                        "tagfiler.message.upload.FileTransferStatus",
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
                    "tagfiler.message.upload.Error", new String[] { e
                            .getClass().getCanonicalName() }), Color.red);
        }
    }

    /**
     * Convenience method for updating the status label
     * 
     * @param status
     */
    private void updateStatus(String status) {
    	updateStatus(status, fontColor);
        statusLabel.setText(status);
    }

    /**
     * Convenience method for updating the status label with a font color
     * 
     * @param status
     * @param c
     * 		the font color
     */
    private void updateStatus(String status, Color c) {
        statusLabel.setForeground(c);
        statusLabel.setText(status);
    }

    /**
     * Convenience method for adding a message to the log
     * 
     * @param message
     */
    private void addLogMessage(String message) {

        if (logBuffer.length() > 0) {
            logBuffer.append('\n');
        }
        logBuffer.append(message);
    }

    /**
     * @return a string representation of the log that occurred for a file
     *         transfer session
     */
    public String getLog() {
        return logBuffer.toString();
    }

    /**
     * Clears the log
     */
    public void clearLog() {
        logBuffer = new StringBuffer();
    }

    /**
     * @return true if the custom fields that are editable by the user are all
     *         valid.
     */
    public boolean validateFields() {
        boolean valid = true;

        // make sure the custom tags all have values
        Set<String> customTagNames = customTagMap.getTagNames();
        for (String customTagName : customTagNames) {
            final String value = customTagMap.getValue(customTagName);
            if (value.length() == 0) {
                valid = false;
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
        customTagMap.clearValues();
    }

    /**
     * Puts the UI in a state where it is no longer active
     */
    public void deactivate() {
        addBtn.setEnabled(false);
        uploadBtn.setEnabled(false);
        Set<String> customTagNames = customTagMap.getTagNames();
        for (String customTagName : customTagNames) {
            customTagMap.getComponent(customTagName).setEnabled(false);
        }
    }

    /**
     * Reloads the UI
     */
    public void reload() {
        this.stop();
        this.destroy();

        try {
            JSObject window = (JSObject) JSObject.getWindow(
                    TagFilerUploadApplet.this).getMember("location");

            window.call("reload", new Boolean[] { true });

        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
            deactivate();
            JOptionPane.showMessageDialog(this.getComponent(), e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);

        }
    }

    /**
     * Destroys the applet
     */
    public void destroy() {
        super.destroy();
    }
}
