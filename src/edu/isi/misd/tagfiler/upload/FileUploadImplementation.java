package edu.isi.misd.tagfiler.upload;

import java.applet.Applet;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import edu.isi.misd.tagfiler.exception.FatalException;
import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.JerseyClientUtils;
import edu.isi.misd.tagfiler.util.LocalFileChecksum;
import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Default implementation of the {@link edu.isi.misd.tagfiler.upload.FileUpload}
 * interface.
 * 
 * @author David Smith
 * 
 */
public class FileUploadImplementation implements FileUpload {

    // tagfiler server URL
    private final String tagFilerServerURL;

    // listener for file upload progress
    private final FileUploadListener fileUploadListener;

    // client used to connect with the tagfiler server
    private final Client client;

    // map containing the checksums of all files to be uploaded.
    private final Map<String, String> checksumMap = new HashMap<String, String>();

    // total amount of bytes to be transferred
    private long datasetSize = 0;

    // base directory to use
    private String baseDirectory = "";

    // custom tags that are used
    private final CustomTagMap customTagMap;

    // the session cookie
    private Cookie cookie = null;

    // the applet
    private Applet applet = null;

    /**
     * Constructs a new file upload
     * 
     * @param url
     *            tagfiler server url
     * @param l
     *            listener for file upload progress
     * @param tagMap
     *            map of the custom tags
     * @param c
     *            session cookie
     */
    public FileUploadImplementation(String url, FileUploadListener l,
				    CustomTagMap tagMap, Cookie c, Applet a) {
        assert (url != null && url.length() > 0);
        assert (l != null);
        assert (tagMap != null);

        tagFilerServerURL = url;
        fileUploadListener = l;
        client = JerseyClientUtils.createClient();
        customTagMap = tagMap;
        cookie = c;
	applet = a;
    }

    /**
     * Sets the base directory of the upload.
     * 
     * @param baseDir
     *            base directory
     */
    public void setBaseDirectory(String baseDir) {
        assert (baseDir != null);
        baseDirectory = baseDir;
    }

    /**
     * Performs the file upload.
     * 
     * @param files
     *            list of absolute file names.
     */
    public boolean postFileData(List<String> files) {
        assert (files != null);
        boolean result = false;
        try {
            result = postFileData(files, getTransmitNumber());
        } catch (Exception e) {
            e.printStackTrace();
            fileUploadListener.notifyError(e);
            result = false;
        }
        return result;
    }

    /**
     * Makes a web server access to get a control number.
     */
    private String getTransmitNumber() throws FatalException {
        String ret = "";
        String query = tagFilerServerURL + "/transmitnumber";
        ClientResponse response = client.resource(query)
                .type(MediaType.APPLICATION_OCTET_STREAM).cookie(cookie)
                .post(ClientResponse.class, "");

	cookie = JerseyClientUtils.updateSessionCookie(response, applet, cookie);

        if (200 == response.getStatus()) {
            ret = response.getLocation().toString();
        } else {
            fileUploadListener
                    .notifyLogMessage("Error getting a control number (code="
                            + response.getStatus() + ")");
            throw new FatalException(
                    TagFilerProperties
                            .getProperty("tagfiler.message.upload.ControlNumberError"));
        }

        response.close();

        return ret;
    }

    /**
     * Convenience method for computing checksums and totaling the file transfer
     * size.
     * 
     * @param files
     *            list of the files to transfer.
     * @throws FatalException
     *             if a fatal exception occurs when computing checksums
     */
    private void buildTotalSize(List<String> files)
            throws FatalException {
        assert (files != null);

        File file = null;
        long fileSize = 0;
        datasetSize = 0;
        for (String filename : files) {
            file = new File(filename);
            if (file.exists() && file.canRead()) {

                if (file.isFile()) {
                    fileSize = file.length();
                    datasetSize += fileSize;
                } else if (file.isDirectory()) {
                    // do nothing
                } else {
                    fileUploadListener.notifyLogMessage("File '" + filename
                            + "' is not a regular file -- skipping.");
                }
            } else {
                fileUploadListener.notifyLogMessage("File '" + filename
                        + "' is not readible or does not exist.");
            }
        }
    }

    /**
     * Convenience method for computing checksums and totaling the file transfer
     * size.
     * 
     * @param files
     *            list of the files to transfer.
     * @throws FatalException
     *             if a fatal exception occurs when computing checksums
     */
    private void buildTotalAndChecksum(List<String> files)
            throws FatalException {
        assert (files != null);

        checksumMap.clear();
        buildAndTotalChecksumHelper(files);
    }

    /**
     * Helper method for computing checksums and totaling the file transfer
     * size.
     * 
     * @param files
     *            list of the files to transfer.
     * @throws FatalException
     *             if a fatal error occurs when computing the checksums.
     */
    private void buildAndTotalChecksumHelper(List<String> files)
            throws FatalException {
        assert (files != null);

        File file = null;
        String checksum = null;
        long fileSize = 0;

        for (String filename : files) {
            file = new File(filename);
            if (file.exists() && file.canRead()) {

                if (file.isFile()) {
                    fileSize = file.length();
                    checksum = LocalFileChecksum.computeFileChecksum(file);
                    checksumMap.put(filename, checksum);
                    fileUploadListener.notifyLogMessage("File=" + filename
                            + ", size=" + fileSize + ", checksum=" + checksum);
                } else if (file.isDirectory()) {
                    // do nothing
                } else {
                    fileUploadListener.notifyLogMessage("File '" + filename
                            + "' is not a regular file -- skipping.");
                }
            } else {
                fileUploadListener.notifyLogMessage("File '" + filename
                        + "' is not readible or does not exist.");
            }
        }
    }

    /**
     * Uploads a set of given files with a specified dataset name.
     * 
     * @param files
     *            list of files to upload.
     * @param datasetName
     *            defined name of the dataset.
     * @return true if the file transfer was a success
     */
    public boolean postFileData(List<String> files, String datasetName) {
        assert (files != null);
        assert (datasetName != null && datasetName.length() > 0);

        boolean success = true;
        ClientResponse response = null;

        // retrieve the amount of total bytes, checksums for each file
        fileUploadListener
                .notifyLogMessage("Computing size and checksum of files...");
        try {
        	buildTotalSize(files);
            fileUploadListener.notifyStart(datasetName, datasetSize);
        	buildTotalAndChecksum(files);
            fileUploadListener.notifyLogMessage(datasetSize
                    + " total bytes will be transferred");

            fileUploadListener
                    .notifyLogMessage("Beginning transfer of dataset '"
                            + datasetName + "'...");

            // first create the dataset url entry
            final String datasetURLQuery = DatasetUtils
                    .getDatasetURLUploadQuery(datasetName, tagFilerServerURL,
                            customTagMap);
            final String datasetURLBody = DatasetUtils.getDatasetURLUploadBody(
                    datasetName, tagFilerServerURL);
            fileUploadListener.notifyLogMessage("Creating dataset URL entry");
            fileUploadListener.notifyLogMessage("Query: " + datasetURLQuery
                    + " Body:" + datasetURLBody);

            // TODO: get the cookie and pass it here to the call

            // WebResource webResource =
            // JerseyClientUtils.createWebResource(client,
            // datasetURLQuery, null);

            // need to capture builder result of cookie() and invoke request on
            // it
            // or cookie is lost!
            response = client.resource(datasetURLQuery)
                    .type(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                    .cookie(cookie).post(ClientResponse.class, datasetURLBody);

	    cookie = JerseyClientUtils.updateSessionCookie(response, applet, cookie);

            // successful tagfiler POST issues 303 redirect to result page
            if (200 == response.getStatus() || 303 == response.getStatus()) {
                try {
                    fileUploadListener
                            .notifyLogMessage("Dataset URL entry created successfully.");
                    success = postFileDataHelper(files, datasetName);
                } catch (Exception e) {
                    e.printStackTrace();
                    success = false;
                } finally {
                    if (success) {
                        fileUploadListener.notifySuccess(datasetName);
                    } else {
                        fileUploadListener.notifyFailure(datasetName);
                    }
                }
            } else {
                fileUploadListener
                        .notifyLogMessage("Error creating the dataset URL entry (code="
                                + response.getStatus() + ")");
                success = false;
                fileUploadListener.notifyFailure(datasetName, response.getStatus());
            }
        } catch (Exception e) {
            // notify the UI of any uncaught errors
            e.printStackTrace();
            fileUploadListener.notifyError(e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return success;
    }

    /**
     * Helper method for transferring files.
     * 
     * @param files
     *            list of the files
     * @param datasetName
     *            name of the dataset
     * @return true if the file tranfer was a success
     * @throws FatalException
     *             if an error occurred in one of the file transfers
     */
    private boolean postFileDataHelper(List<String> files, String datasetName)
            throws FatalException {
        assert (files != null);
        assert (datasetName != null && datasetName.length() > 0);

        boolean success = true;
        WebResource webResource = null;
        ClientResponse response = null;
        File file = null;
        for (String fileName : files) {
            file = new File(fileName);

            // make sure file exists
            if (file.exists() && file.canRead()) {
                if (file.isFile()) {
                    final String fileUploadQuery = DatasetUtils
                            .getFileUploadQuery(datasetName, tagFilerServerURL,
                                    baseDirectory, file,
                                    checksumMap.get(file.getAbsolutePath()));
                    fileUploadListener.notifyFileTransferStart(file
                            .getAbsolutePath());
                    fileUploadListener.notifyLogMessage("Transferring file '"
                            + file.getAbsolutePath() + "'");
                    fileUploadListener.notifyLogMessage("Query: "
                            + fileUploadQuery);

                    // TODO: get the cookie and pass it to this call
                    // webResource = JerseyClientUtils.createWebResource(client,
                    // fileUploadQuery, cookie);

                    // must capture builder result from cookie() and do request
                    // on it!
                    response = client.resource(fileUploadQuery)
                            .type(MediaType.APPLICATION_OCTET_STREAM)
                            .cookie(cookie).put(ClientResponse.class, file);

		    cookie = JerseyClientUtils.updateSessionCookie(response, applet, cookie);

                    if (201 == response.getStatus()) {
                        fileUploadListener.notifyFileTransferComplete(
                                file.getAbsolutePath(), file.length());
                        fileUploadListener.notifyLogMessage("File '"
                                + file.getAbsolutePath()
                                + "' transferred successfully.");
                    } else {
                        fileUploadListener
                                .notifyLogMessage("Error transferring file '"
                                        + file.getAbsolutePath() + "' (code="
                                        + response.getStatus() + ")");
                        success = false;
                    }
                    response.close();
                } else if (file.isDirectory()) {
                    // do nothing -- contents were expanded in the list already
                    fileUploadListener.notifyFileTransferSkip(file
                            .getAbsolutePath());
                } else {
                    fileUploadListener.notifyFileTransferSkip(file
                            .getAbsolutePath());
                    fileUploadListener.notifyLogMessage("File "
                            + file.getAbsolutePath()
                            + " is not a regular file -- skipping.");
                }

            } else {
                fileUploadListener.notifyLogMessage("File "
                        + file.getAbsolutePath()
                        + " is not readible or does not exist.");
                success = false;
            }
        }
        return success;
    }
}
