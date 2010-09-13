package edu.isi.misd.tagfiler;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Timer;

import javax.swing.JApplet;
import javax.swing.JOptionPane;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import netscape.javascript.JSException;
import netscape.javascript.JSObject;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import edu.isi.misd.tagfiler.security.TagFilerSecurity;
import edu.isi.misd.tagfiler.ui.FileUI;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.JerseyClientUtils;
import edu.isi.misd.tagfiler.util.SessionExpireTimerTask;
import edu.isi.misd.tagfiler.util.TagFilerProperties;
import edu.isi.misd.tagfiler.util.WarnIdleTimerTask;

/**
 * Common parent class for the upload and download applet, which is responsible
 * for session and browser-related responsibilities.
 * 
 * @author David Smith
 * 
 */
public abstract class AbstractTagFilerApplet extends JApplet implements FileUI {

    private Client client = null;

    private static final long serialVersionUID = 1L;

    // schedules a task to abort the page
    private Timer abortTimer = null;

    // schedules a task to warn that the page will be aborted
    private Timer warnIdleTimer = null;

    // tagfiler idle timeout value
    private int tagFilerIdleMins = 0;

    private int warnIdleSeconds = Integer.parseInt(TagFilerProperties
            .getProperty("tagfiler.session.WarnIdleSeconds"));

    // parameter name for the session timeout
    private static final String TAGFILER_IDLE_MINS_PARAM = "tagfiler.idle.mins";

    // parameter name for the tagserver URL
    private static final String TAGFILER_SERVER_URL_PARAM = "tagfiler.server.url";

    private static final String COOKIE_NAME_PROPERTY = "tagfiler.cookie.name";
    // tagfiler server URL specified from the parameter of the applet
    protected String tagFilerServerURL = null;

    // cookie maintainined in the session
    protected Cookie sessionCookie = null;

    private String tagFilerWebauthURL;

    /**
     * Schedules the timers
     */
    private void scheduleSessionTimers() {
        long abortPeriod = tagFilerIdleMins * 60 * 1000;
        long warnPeriod = abortPeriod - (warnIdleSeconds * 1000);

        abortTimer = new Timer();
        abortTimer.schedule(new SessionExpireTimerTask(this), abortPeriod);

        warnIdleTimer = new Timer();
        warnIdleTimer.schedule(new WarnIdleTimerTask(this), warnPeriod);

        System.out.println("session timers set (abort=" + abortPeriod
                + ", warn=" + warnPeriod + ")");
    }

    /**
     * Loads security settings, common parameters, session cookie
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

        // use the tagFilerServerURL to get the webauth URL
        // TODO: pass this as a parameter
        try {
            final URL tagFilerURL = new URL(tagFilerServerURL);
            tagFilerWebauthURL = tagFilerURL.getProtocol() + "://"
                    + tagFilerURL.getHost();
            if (tagFilerURL.getPort() > 0) {
                tagFilerWebauthURL = tagFilerWebauthURL + ":"
                        + tagFilerURL.getPort();
            }
            tagFilerWebauthURL = tagFilerWebauthURL + "/webauthn";
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(tagFilerServerURL
                    + " is not a valid URL for the tagfiler server.");
        }

        String tagFilerIdleMinStr = this.getParameter(TAGFILER_IDLE_MINS_PARAM);
        if (tagFilerIdleMinStr != null && tagFilerIdleMinStr.length() > 0) {
            try {

                tagFilerIdleMins = Integer.parseInt(tagFilerIdleMinStr);
                if (tagFilerIdleMins <= 0) {
                    throw new NumberFormatException();
                }
                System.out.println(TAGFILER_IDLE_MINS_PARAM + "="
                        + tagFilerIdleMins);

            } catch (NumberFormatException e) {
                System.out
                        .println(TAGFILER_IDLE_MINS_PARAM
                                + " was passed in an invalid format, no idle timer will be used");
            }
        } else {
            System.out.println("No idle timer passed to the applet.");
        }
    }

    /**
     * Starts the applet timers
     */
    public void start() {
        client = JerseyClientUtils.createClient();
        scheduleSessionTimers();
    }

    /**
     * Polls the server to keep the session alive and resets its internal timers
     * 
     * @param pollServer
     *            if true then send a poll to the server as well
     */
    public void refreshSession(boolean pollServer) {
        if (pollServer) {
            ClientResponse response = client
                    .resource(DatasetUtils.getSessionPollURL(tagFilerServerURL))
                    .cookie(sessionCookie).get(ClientResponse.class);
            if (response.getStatus() == 200) {
                System.out.println("Refreshed the session, cookie="
                        + sessionCookie);
                sessionCookie = JerseyClientUtils.updateSessionCookie(response,
                        this, sessionCookie);
                getFileTransfer().updateSessionCookie(sessionCookie);
            } else {
                System.out
                        .println("Could not refresh the session in the server, code="
                                + response.getStatus());
            }
            response.close();
        }
        suspendSession();
        scheduleSessionTimers();
    }

    /**
     * Suspends the timers
     */
    public void suspendSession() {
        if (abortTimer != null) {
            abortTimer.cancel();
            abortTimer = null;
        }
        if (warnIdleTimer != null) {
            warnIdleTimer.cancel();
            warnIdleTimer = null;
        }

    }

    /**
     * Ends the session, redirecting to the login page
     */
    public void endSession() {
        String logoutURL = DatasetUtils.getLogoutURL(tagFilerWebauthURL);
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("referer", DatasetUtils.getLoginURL(tagFilerWebauthURL));
        ClientResponse response = client.resource(logoutURL)
                .header("Referer", logoutURL)
                .type(MediaType.APPLICATION_FORM_URLENCODED)
                .cookie(getFileTransfer().getSessionCookie())
                .post(ClientResponse.class, formData);
        System.out.println("logged out, response=" + response.getStatus());
        response.close();

        redirect(DatasetUtils.getLoginURL(tagFilerWebauthURL));
    }

    /**
     * Redirects to an url
     */
    public void redirect(String urlStr) {
        assert (urlStr != null);
        System.out.println("redirect: " + urlStr);
        try {
            final URL url = new URL(urlStr);
            getAppletContext().showDocument(url, "_self");
        } catch (MalformedURLException e) {
            e.printStackTrace();
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
                    AbstractTagFilerApplet.this).getMember("location");

            window.call("reload", new Boolean[] { true });

        } catch (JSException e) {
            // don't throw, but make sure the UI is unuseable
            deactivate();
            JOptionPane.showMessageDialog(this.getComponent(), e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);

        }
    }

    /**
     * Child class must provide a method of accessing the file transfer object
     * 
     * @return the FileTransfer object
     */
    abstract protected FileTransfer getFileTransfer();
}
