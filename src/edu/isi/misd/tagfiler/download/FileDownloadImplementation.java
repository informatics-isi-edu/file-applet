package edu.isi.misd.tagfiler.download;

import java.applet.Applet;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;

import edu.isi.misd.tagfiler.AbstractFileTransferSession;
import edu.isi.misd.tagfiler.exception.FatalException;
import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.upload.FileUploadListener;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.JerseyClientUtils;
import edu.isi.misd.tagfiler.util.LocalFileChecksum;
import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Default implementation of the
 * {@link edu.isi.misd.tagfiler.download.FileDownload} interface.
 * 
 * @author Serban Voinea
 * 
 */
public class FileDownloadImplementation extends AbstractFileTransferSession
        implements FileDownload {

    // tagfiler server URL
    private final String tagFilerServerURL;

    // listener for file download progress
    private final FileUploadListener fileUploadListener;

    // client used to connect with the tagfiler server
    private final Client client;

    // list containing the files names to be downloaded.
    private List<String> fileNames = new ArrayList<String>();

    // map containing the checksums of all files to be downloaded.
    private Map<String, String> checksumMap = new HashMap<String, String>();

    // map containing the encoded files names to be downloaded.
    private Map<String, String> encodeMap = new HashMap<String, String>();

    // map containing the bytes of all files to be downloaded.
    private Map<String, Long> bytesMap = new HashMap<String, Long>();

    // total amount of bytes to be downloaded
    private long datasetSize = 0;

    // base directory to use
    private String baseDirectory = "";

    // the dataset transmission number
    private String controlNumber;

    // custom tags that are used
    private final CustomTagMap customTagMap;

    // the applet
    private Applet applet = null;

    /**
     * Constructs a new file download
     * 
     * @param url
     *            tagfiler server url
     * @param l
     *            listener for file upload progress
     * @param c
     *            session cookie
     * @param tagMap
     *            map of the custom tags
     */
    public FileDownloadImplementation(String url, FileUploadListener l,
				      Cookie c, CustomTagMap tagMap, Applet a) throws FatalException {
        assert (url != null && url.length() > 0);
        assert (l != null);
        assert (c != null);
        assert (tagMap != null);

        tagFilerServerURL = url;
        fileUploadListener = l;
        client = JerseyClientUtils.createClient();
        cookie = c;
        customTagMap = tagMap;
	applet = a;
    }

    /**
     * Returns the total number of bytes to be downloaded.
     */
    public long getSize() {
        return datasetSize;
    }

    /**
     * Returns the list of the file names to be downloaded.
     * 
     * @param dataset
     *            the dataset of the files
     */
    public List<String> getFiles(String dataset) {
        assert (dataset != null && dataset.length() > 0);
        controlNumber = dataset;
    	fileUploadListener.notifyUpdateStart(controlNumber);

        try {
            fileNames = new ArrayList<String>();
            checksumMap = new HashMap<String, String>();
            encodeMap = new HashMap<String, String>();
            bytesMap = new HashMap<String, Long>();
            datasetSize = 0;

            setCustomTags();
            getDataSet();
        } catch (Exception e) {
            e.printStackTrace();
            fileUploadListener.notifyError(e);
        }
    	fileUploadListener.notifyUpdateComplete(controlNumber);
    	
        return fileNames;
    }

    /**
     * Performs the dataset download.
     * 
     * @param destDir
     *            destination directory for the download
     */
    public boolean downloadFiles(String destDir) {
        assert (destDir != null && destDir.length() > 0);
        String errMsg = null;
        baseDirectory = destDir;

        Set<String> files = encodeMap.keySet();
        boolean success = true;
        fileUploadListener.notifyStart(controlNumber, datasetSize);
        try {
            for (String file : files) {
            	downloadFile(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
            fileUploadListener.notifyError(e);
            errMsg = e.getMessage();
            success = false;
        }
        finally {
        	if (success) {
        		fileUploadListener.notifySuccess(controlNumber);
        	} else {
        		fileUploadListener.notifyFailure(controlNumber, errMsg);
        	}
        }

        return success;
    }

    /**
     * Sets the values for the custom tags of the dataset to be downloaded.
     * 
     * @throws UnsupportedEncodingException
     */
    private void setCustomTags() throws UnsupportedEncodingException {
        Set<String> tags = customTagMap.getTagNames();
        for (String tag : tags) {
            String value = getTagValue("", tag);
            customTagMap.setValue(tag, value);
        }
    }

    /**
     * Gets the files to be downloaded.
     */
    private boolean getDataSet() {
        boolean result = false;

        try {
            // get the files list
            String url = DatasetUtils.getDatasetQueryUrl(controlNumber,
                    tagFilerServerURL);
            String prefix = DatasetUtils.getDatasetUrl(controlNumber,
                    tagFilerServerURL);

            ClientResponse response = client.resource(url)
                    .accept("text/uri-list")
                    .type(MediaType.APPLICATION_OCTET_STREAM).cookie(cookie)
                    .get(ClientResponse.class);

	    cookie = JerseyClientUtils.updateSessionCookie(response, applet, cookie);

            String textEntity = response.getEntity(String.class);
            textEntity = textEntity.replace(prefix, "");
            response.close();

            // get the files maps
            StringTokenizer tokenizer = new StringTokenizer(textEntity, "\n");
            while (tokenizer.hasMoreTokens()) {
                // get the file name
                String file = tokenizer.nextToken();
                String name = DatasetUtils.urlDecode(file).substring(1);
                fileNames.add(name);
                encodeMap.put(name, file);

                // get the bytes
                long bytes = Long.parseLong(getTagValue(file, "bytes"));
                datasetSize += bytes;
                bytesMap.put(name, bytes);

                // get the checksum
                String checksum = getTagValue(file,
                        TagFilerProperties.getProperty("tagfiler.tag.checksum"));
                checksumMap.put(name, checksum);
            }

            result = true;
        } catch (Exception e) {
            e.printStackTrace();
            fileUploadListener.notifyError(e);
            result = false;
        }

        return result;
    }

    /**
     * Get the value of a file tag.
     * 
     * @param file
     *            the file name as received from the FileList.
     * @param tag
     *            the tag name.
     * @throws UnsupportedEncodingException
     */
    private String getTagValue(String file, String tag)
            throws UnsupportedEncodingException {
        String query = DatasetUtils.getFileTag(controlNumber,
                tagFilerServerURL, file, tag);
        ClientResponse response = client.resource(query)
                .type(MediaType.APPLICATION_OCTET_STREAM).cookie(cookie)
                .get(ClientResponse.class);

	cookie = JerseyClientUtils.updateSessionCookie(response, applet, cookie);

        if (response.getStatus() != 200)
        {
        	response.close();
        	fileUploadListener.notifyFailure(controlNumber, response.getStatus());
        	return "";
        }
		String value = response.getEntity(String.class);
        value = DatasetUtils.urlDecode(value.substring(value.indexOf('=') + 1));
        response.close();

        return value;
    }

    /**
     * Performs the file download.
     * 
     * @param file
     *            the file name.
     */
    private boolean downloadFile(String file) throws Exception{
        assert (file != null && encodeMap.get(file) != null);
        boolean result = false;
        try {
            // get the file content
            String encodeName = encodeMap.get(file);
            String url = DatasetUtils.getFileUrl(controlNumber,
                    tagFilerServerURL, encodeName);

	    ClientResponse response = client.resource(url)
                    .type(MediaType.APPLICATION_OCTET_STREAM).cookie(cookie)
                    .get(ClientResponse.class);

	    if (response.getStatus() != 200) {
        	throw new Exception("Status Code: " + response.getStatus());
	    }
	    InputStream in = response.getEntityInputStream();

	    cookie = JerseyClientUtils.updateSessionCookie(response, applet, cookie);

	    // write the file into the destination
            File dir = new File(baseDirectory);

	    String localFile = file.replace('/', File.separatorChar);

            int index = localFile.lastIndexOf(File.separatorChar);
            if (index != -1) {
                dir = new File(baseDirectory + File.separatorChar + localFile.substring(0, index));
            }

            if (dir.isDirectory() || dir.mkdirs()) {
                fileUploadListener.notifyFileTransferStart(baseDirectory + File.separatorChar
                        + localFile);
                FileOutputStream fos = new FileOutputStream(baseDirectory + File.separatorChar
                        + localFile);
                while (true) {
                    int count = in.available();
                    if (count == 0) {
                        count = 1;
                    }
                    byte ret[] = new byte[count];
                    int res = in.read(ret);
                    if (res == -1) {
                        break;
                    }
                    fos.write(ret);
                }
                in.close();
                fos.close();

                // verify checksum
                File downloadFile = new File(baseDirectory + File.separatorChar + localFile);
                String checksum = LocalFileChecksum
                        .computeFileChecksum(downloadFile);
                if (!checksum.equals(checksumMap.get(file))) {
                    throw new Exception(
                            "Checksum failed for downloading the file: " + file);
                }
                fileUploadListener.notifyFileTransferComplete(baseDirectory
                        + File.separatorChar + localFile, bytesMap.get(file));
            } else {
                in.close();
                throw new Exception("Can not make directory: "
                        + dir.getAbsolutePath());
            }

        } catch (Exception e) {
            e.printStackTrace();
            fileUploadListener.notifyError(e);
            throw e;
        }
        return result;
    }

    /**
     * Checks with the tagfiler server to verify that a dataset by the control
     * number already exists
     * 
     * @param controlNumber
     *            the transmission number to check
     * @param status
     *            the status returned by the HTTP response 
     * @param errorMessage
     *            the error message to be displayed
     * @return true if a dataset with the particular transmission number exists,
     *         false otherwise
     */
    public boolean verifyValidControlNumber(String controlNumber, StringBuffer code, StringBuffer errorMessage) {
        assert (controlNumber != null && controlNumber.length() != 0);
        boolean valid = false;
        ClientResponse response = null;
        try {
            response = client
                    .resource(
                            DatasetUtils.getDatasetUrl(controlNumber,
                                    tagFilerServerURL)).accept("text/uri-list")
                    .type(MediaType.APPLICATION_OCTET_STREAM).cookie(cookie)
                    .head();
            int status = response.getStatus();
            if ((status == 200 || status == 303)
                    && JerseyClientUtils
                            .checkResponseHeaderPattern(
                                    response,
                    JerseyClientUtils.LOCATION_HEADER_NAME, 
                    tagFilerServerURL
                            + TagFilerProperties
                                    .getProperty("tagfiler.url.queryuri")
                            + TagFilerProperties
                                    .getProperty("tagfiler.tag.containment")
                            + "=" + controlNumber)) {
                valid = true;
            }
            else {
                System.out.println("transmission number verification failed, code="
                        + response.getStatus());

            	code.append("Status ").append(status);
                switch (status) {
                case 404:
                	errorMessage.append("Not Found");
                	break;
                case 403:
                	errorMessage.append("Forbidden");
                	break;
                case 401:
                	errorMessage.append("Unauthorized");
                	break;
                case 400:
                	errorMessage.append("Bad Request");
                	break;
                case 409:
                	errorMessage.append("Conflict");
                	break;
                case 500:
                	errorMessage.append("Internal Server Error");
                	break;
                default:
                	errorMessage.append("Unmatched Response Header Pattern");
                }
            }
            cookie = JerseyClientUtils.updateSessionCookie(response, applet,
                    cookie);
        } catch (UnsupportedEncodingException e) {
            valid = false;
            e.printStackTrace();
            fileUploadListener.notifyError(e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return valid;
    }
}
