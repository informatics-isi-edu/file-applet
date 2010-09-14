package edu.isi.misd.tagfiler.util;

import java.util.TimerTask;

import javax.swing.JOptionPane;

import edu.isi.misd.tagfiler.ui.FileUI;

/**
 * Timer task that will ask the user whether or not to extend their session when
 * it is run. If they choose to extend, it will refresh the UI session as well
 * as the tagfiler server session.
 * 
 * @author David Smith
 * 
 */
public class WarnIdleTimerTask extends TimerTask {

    private final FileUI fileUI;

    /**
     * Constructor
     * 
     * @param ui
     *            the file UI to warn
     */
    public WarnIdleTimerTask(FileUI ui) {
        assert (ui != null);
        fileUI = ui;
    }

    /**
     * Prompts the user whether they want to extend their session (i.e. stay
     * logged in)
     */
    @Override
    public void run() {
        long untilTime = fileUI.pollServerSession(false);
        
        if (untilTime <= 0) {
        	fileUI.endSession();
        }
        else if (untilTime <= fileUI.getWarnIdle()) {
            int result = JOptionPane
            .showConfirmDialog(
                    fileUI.getComponent(),
                    TagFilerProperties
                            .getProperty(
                                    "tagfiler.dialog.WarnIdleMessage",
                                    new String[] { TagFilerProperties
                                            .getProperty("tagfiler.session.WarnIdleSeconds") }),
                    TagFilerProperties
                            .getProperty("tagfiler.dialog.WarnIdleTitle"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		    if (result == JOptionPane.YES_OPTION) {
		        // reset the task
		        fileUI.refreshSession(true);
		    } else {
		        // TODO: Force the session to timeout immediately? Right now the UI
		        // will handle it on the normal timeout, but it seems to conflict
		        // with the browser if trying to force it.
		    	// just ignore
		    }
        }
        else {
        	fileUI.scheduleSessionTimers(untilTime);
        }
    }
}
