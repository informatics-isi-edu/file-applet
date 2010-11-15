package edu.isi.misd.tagfiler.download;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import edu.isi.misd.tagfiler.AbstractFileTransferSession;
import edu.isi.misd.tagfiler.AbstractTagFilerApplet;
import edu.isi.misd.tagfiler.TagFilerDownloadApplet;
import edu.isi.misd.tagfiler.client.ClientURLListener;
import edu.isi.misd.tagfiler.client.ClientURLResponse;
import edu.isi.misd.tagfiler.client.ConcurrentClientURL;
import edu.isi.misd.tagfiler.client.ConcurrentJakartaClient;
import edu.isi.misd.tagfiler.ui.CustomTagMap;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.ClientUtils;
import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
 * Default implementation of the
 * {@link edu.isi.misd.tagfiler.download.FileDownload} interface.
 * 
 * @author Serban Voinea
 * 
 */
public class FileDownloadImplementation extends AbstractFileTransferSession
        implements FileDownload, ClientURLListener {

    // tagfiler server URL
    private final String tagFilerServerURL;

    // listener for file download progress
    private final FileDownloadListener fileDownloadListener;

    // client used to connect with the tagfiler server
    protected ConcurrentClientURL client;

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

    // the dataset transmission number
    private String controlNumber;

    // custom tags that are used
    private final CustomTagMap customTagMap;

    // the applet
    private TagFilerDownloadApplet applet = null;

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
    public FileDownloadImplementation(String url, FileDownloadListener l,
				      String c, CustomTagMap tagMap, TagFilerDownloadApplet a) {
        assert (url != null && url.length() > 0);
        assert (l != null);
        assert (c != null);
        assert (tagMap != null);

        tagFilerServerURL = url;
        fileDownloadListener = l;
        cookie = c;
        customTagMap = tagMap;
	applet = a;
	boolean allowChunks = ((AbstractTagFilerApplet) applet).allowChunksTransfering();
    client = new ConcurrentJakartaClient(allowChunks ? ((AbstractTagFilerApplet) applet).getMaxConnections() : 2, ((AbstractTagFilerApplet) applet).getSocketBufferSize(), this);
    client.setChunked(allowChunks);
    client.setChunkSize(((AbstractTagFilerApplet) applet).getChunkSize());
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
    	fileDownloadListener.notifyUpdateStart(controlNumber);
    	boolean success = false;
        String errMsg = null;

        try {
            fileNames = new ArrayList<String>();
            checksumMap = new HashMap<String, String>();
            encodeMap = new HashMap<String, String>();
            bytesMap = new HashMap<String, Long>();
            datasetSize = 0;

            setCustomTags();
            getDataSet();
            success = true;
        } catch (Exception e) {
            e.printStackTrace();
            fileDownloadListener.notifyError(e);
            errMsg = e.getMessage();
        }
        finally {
        	if (success) {
            	fileDownloadListener.notifyUpdateComplete(controlNumber);
        	} else {
        		fileDownloadListener.notifyFailure(controlNumber, errMsg);
        	}
        }
    	
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
        try {
			client.setBaseURL(DatasetUtils.getBaseDownloadUrl(controlNumber, tagFilerServerURL));
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

        boolean success = true;
        fileDownloadListener.notifyStart(controlNumber, datasetSize);
        client.download(fileNames, destDir, checksumMap, bytesMap);

        return success;
    }

    /**
     * Sets the values for the custom tags of the dataset to be downloaded.
     * 
     * @throws UnsupportedEncodingException
     */
    private void setCustomTags() throws UnsupportedEncodingException {
        Set<String> tags = customTagMap.getTagNames();
	StringBuffer buffer = new StringBuffer();
        for (String tag : tags) {
            String value = getTagValue("", tag);
	    buffer.append(tag).append("<br/>").append(value).append("<br/>");
            customTagMap.setValue(tag, value);
        }
	String values = buffer.toString().replaceAll("'", "\\\\'");
	applet.eval("setTags", values);
    }

    /**
     * Gets the files to be downloaded.
     */
    private boolean getDataSet() throws Exception {
        boolean result = false;

        try {
            // get the files list
            String url = DatasetUtils.getDatasetQueryUrl(controlNumber,
                    tagFilerServerURL);
            String prefix = DatasetUtils.getDatasetUrl(controlNumber,
                    tagFilerServerURL);

            ClientURLResponse response = client.getDataSet(url, cookie);

	    cookie = client.updateSessionCookie(applet, cookie);

	    int status = response.getStatus();
	    if (status != 200) {
	    	response.release();
            fileDownloadListener.notifyFailure(controlNumber, "Get Files List returned status " + status);
        	throw new Exception("Status Code: " + status);
	    }
            String textEntity = response.getEntityString();
            textEntity = textEntity.replace(prefix, "");
            response.release();

            // get the files maps
            StringTokenizer tokenizer = new StringTokenizer(textEntity, "\n");
            fileDownloadListener.notifyRetrieveStart(tokenizer.countTokens());
            while (tokenizer.hasMoreTokens()) {
                // get the file name
                String file = tokenizer.nextToken();
                String name = DatasetUtils.urlDecode(file).substring(1);
                fileDownloadListener.notifyFileRetrieveComplete(name);
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
            fileDownloadListener.notifyError(e);
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
        ClientURLResponse response = client.getTagValue(query, cookie);

	cookie = client.updateSessionCookie(applet, cookie);

        if (response.getStatus() != 200)
        {
        	// if status is 404, the tag might have been deleted
        	if (response.getStatus() != 404) {
            	fileDownloadListener.notifyFailure(controlNumber, response.getStatus());
        	}
        	response.release();
        	return "";
        }
		String value = response.getEntityString();
        value = DatasetUtils.urlDecode(value.substring(value.indexOf('=') + 1));
        response.release();

        return value;
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
        ClientURLResponse response = null;
        try {
        	response = client.verifyValidControlNumber(DatasetUtils.getDatasetUrl(controlNumber, tagFilerServerURL), cookie);
            int status = response.getStatus();
            if ((status == 200 || status == 303) && response.checkResponseHeaderPattern(
            		ClientUtils.LOCATION_HEADER_NAME, 
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
            cookie = client.updateSessionCookie(applet, cookie);
        } catch (UnsupportedEncodingException e) {
            valid = false;
            e.printStackTrace();
            fileDownloadListener.notifyError(e);
        } finally {
        	response.release();
        }
        return valid;
    }

    /**
     * Callback to get the cookie.
     * 
     * @return the cookie or null if cookies are not used
     */
	public String getCookie() {
		// TODO Auto-generated method stub
		return cookie;
	}

	/**
	 * Get the URL parameters for uploads/downloads, if any
	 * In DIRC necessary for Transmission Number and checksum
	 * 
	 * @param file
	 *            the file to be uploaded/downloaded
	 * @return the URL parameters or null if None
	 */
	public String getURLParameters(String file) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Callback to notify a chunk block transfer completion during the upload/download process
	 * 
	 * @param size
	 *            the chunk size
	 */
	public void notifyChunkTransfered(long size) {
		// TODO Auto-generated method stub
		fileDownloadListener.notifyChunkTransfered(false, size);
	}

	/**
	 * Callback to notify an error during the upload/download process
	 * 
	 * @param err
	 *            the error message
	 * @param e
	 *            the exception
	 */
	public void notifyError(String err, Exception e) {
		// TODO Auto-generated method stub
		fileDownloadListener.notifyFailure(controlNumber, err);
	}

	/**
	 * Callback to notify a failure during the upload/download process
	 * 
	 * @param err
	 *            the error message
	 */
	public void notifyFailure(String err) {
		// TODO Auto-generated method stub
		fileDownloadListener.notifyFailure(controlNumber, err);
	}

	/**
	 * Callback to notify a file transfer completion during the upload/download process
	 * 
	 * @param size
	 *            the chunk size
	 */
	public void notifyFileTransfered(long size) {
		// TODO Auto-generated method stub
		fileDownloadListener.notifyChunkTransfered(true, size);
	}

	/**
	 * Callback to notify success for the entire upload/download process
	 * 
	 */
	public void notifySuccess() {
		// TODO Auto-generated method stub
		fileDownloadListener.notifySuccess(controlNumber);
	}

	/**
	 * Callback to update the session cookie. Should have an empty body if cookies are not used.
	*/
	public void updateSessionCookie() {
		// TODO Auto-generated method stub
        cookie = client.updateSessionCookie(applet, cookie);
	}
}

