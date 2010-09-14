package edu.isi.misd.tagfiler.util;

import java.util.TimerTask;

import edu.isi.misd.tagfiler.ui.FileUI;

/**
 * Timer task that will expire the UI session when it runs.
 * 
 * @author David Smith
 * 
 */
public class SessionExpireTimerTask extends TimerTask {

    private final FileUI fileUI;

    /**
     * Constructor
     * 
     * @param ui
     *            the UI that will expire
     */
    public SessionExpireTimerTask(FileUI ui) {
        assert (ui != null);
        fileUI = ui;
    }

    /**
     * Ends the UI's session
     */
    @Override
    public void run() {
        fileUI.endSession(false);
    }

}
