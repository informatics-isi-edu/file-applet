package edu.isi.misd.tagfiler.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Window component that displays the current log. It does not update without
 * reopening.
 * 
 * @author David Smith
 * 
 */
public class LogDialog extends JDialog implements ActionListener {

    private static final long serialVersionUID = 21321313;

    private static String value = "";

    private static LogDialog dialog = null;

    /**
     * 
     * @param frameComponent
     *            the parent frame component
     * @param title
     *            title for the dialog window
     * @param logContents
     *            the log contents to display in the window.
     * @return
     */
    public static String showDialog(Component frameComponent, String title,
            String logContents) {
        assert (frameComponent != null);
        assert (title != null);
        assert (logContents != null);

        final Frame frame = JOptionPane.getFrameForComponent(frameComponent);
        dialog = new LogDialog(frame, title, logContents);
        dialog.setVisible(true);
        return value;
    }

    /**
     * Private constructor
     * 
     * @param frame
     *            the parent frame component
     * @param title
     *            title for the dialog window
     * @param logContents
     *            the log contents
     */
    private LogDialog(Frame frame, String title, String logContents) {
        super(frame, title, true);
        final JButton closeBtn = new JButton(
                TagFilerProperties.getProperty("tagfiler.button.CloseLog"));
        closeBtn.addActionListener(this);
        getRootPane().setDefaultButton(closeBtn);

        final JTextArea textArea = new JTextArea(20, 50);
        textArea.setBackground(Color.white);
        textArea.setText(logContents);

        final JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBackground(Color.white);
        scrollPane.setAutoscrolls(true);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollPane.setPreferredSize(new Dimension(500, 250));

        final JPanel scrollPanel = new JPanel();
        scrollPanel.setBackground(Color.white);
        scrollPanel.setLayout(new BoxLayout(scrollPanel, BoxLayout.X_AXIS));
        scrollPanel.add(scrollPane, BorderLayout.CENTER);

        final JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(Color.white);
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.add(closeBtn, BorderLayout.CENTER);

        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.white);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        panel.add(scrollPanel);
        panel.add(buttonPanel);

        final Container contentPane = getContentPane();
        contentPane.add(panel, BorderLayout.CENTER);
        pack();
    }

    /**
     * Closes the window
     */
    public void actionPerformed(ActionEvent e) {
        assert (e != null);
        // close the dialog
        dialog.setVisible(false);
        dialog.getParent().requestFocusInWindow();
    }
}
