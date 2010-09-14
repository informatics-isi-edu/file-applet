package edu.isi.misd.tagfiler.util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import edu.isi.misd.tagfiler.exception.FatalException;
import edu.isi.misd.tagfiler.ui.CustomTagMap;

/**
 * Utility class for generating dataset names and unique file names based on a
 * common dataset name, as well as URL utilities.
 * 
 * @author David Smith
 * 
 */
public class DatasetUtils {

    private static final String UTF_8 = "UTF-8";

    private static final String DATASET_PATH_SEPARATOR = "/";

    private static final String readACL_PROPERTY = "tagfiler.readacl";

    private static final String writeACL_PROPERTY = "tagfiler.writeacl";

    private static final String readACL_tag_PROPERTY = "tagfiler.tag.readacl";

    private static final String writeACL_tag_PROPERTY = "tagfiler.tag.writeacl";

    /**
     * 
     * @return a randomly-generated dataset name
     */
    public static String generateDatasetName() {
        final long randomNumber = (long) (10000000000L * Math.random()) + 1;
        final String datasetName = String.valueOf(randomNumber);
        final StringBuffer ret = new StringBuffer();
        for (int i = (10 - datasetName.length()); i > 0; i--) {
            ret.append("0");
        }
        return ret.append(datasetName).toString();
    }

    /**
     * 
     * @param datasetName
     *            common dataset name
     * @param fileName
     *            name of the file in the dataset
     * @return a dataset name based on a common name, followed by a file path
     */
    public static String generateDatasetPath(String datasetName,
            String baseDirectory, String fileName) {
        assert (datasetName != null && datasetName.length() > 0);
        assert (baseDirectory != null);
        assert (fileName != null && fileName.length() > 0);

        StringBuffer datasetPath = new StringBuffer(datasetName);
        fileName = fileName.replace(baseDirectory, "")
                .replaceAll("\\\\", DATASET_PATH_SEPARATOR)
                .replaceAll("/", DATASET_PATH_SEPARATOR).replaceAll(":", "");
        if (!fileName.startsWith(DATASET_PATH_SEPARATOR)) {
            datasetPath.append(DATASET_PATH_SEPARATOR);
        }
        datasetPath.append(fileName);
        return datasetPath.toString();
    }

    /**
     * 
     * @param url
     *            the string to decode
     * @return the decoded url
     */
    public static String urlDecode(String url)
            throws UnsupportedEncodingException {
        assert (url != null && url.length() > 0);
        url = URLDecoder.decode(url, UTF_8);

        return url;
    }

    /**
     * 
     * @param datasetName
     *            the string to encode
     * @return a URL-safe version of the string
     */
    public static String urlEncode(String datasetName)
            throws UnsupportedEncodingException {
        assert (datasetName != null && datasetName.length() > 0);
        datasetName = URLEncoder.encode(datasetName, UTF_8);

        return datasetName;
    }

    /**
     * 
     * @param datasetName
     *            name of the dataset
     * @param tagFilerServer
     *            URL of the tagfiler server
     * @param customTagMap
     *            map of the custom tags
     * @return the REST URL to create a tagfiler URL upload for the dataset.
     * @thows FatalException if the URL cannot be constructed
     */
    public static final String getDatasetURLUploadQuery(String datasetName,
            String tagFilerServer, CustomTagMap customTagMap)
            throws FatalException {
        String readACL_value = TagFilerProperties.getProperty(readACL_PROPERTY);
        String readACL_tag = TagFilerProperties
                .getProperty(readACL_tag_PROPERTY);
        String writeACL_value = TagFilerProperties
                .getProperty(writeACL_PROPERTY);
        String writeACL_tag = TagFilerProperties
                .getProperty(writeACL_tag_PROPERTY);

        assert (datasetName != null && datasetName.length() > 0);
        assert (tagFilerServer != null && tagFilerServer.length() > 0);
        assert (customTagMap != null);
        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(TagFilerProperties.getProperty("tagfiler.url.fileuri"));
        try {
            restURL.append(DatasetUtils.urlEncode(datasetName))
                    .append("?")
                    .append(DatasetUtils.urlEncode(TagFilerProperties
                            .getProperty("tagfiler.tag.imageset")));

            Set<String> tagNames = customTagMap.getTagNames();
            for (String tagName : tagNames) {
                restURL.append("&")
                        .append(DatasetUtils.urlEncode(tagName))
                        .append("=")
                        .append(DatasetUtils.urlEncode(customTagMap
                                .getValue(tagName)));
            }
            if (readACL_tag != null && readACL_value != null) {
                restURL.append("&").append(DatasetUtils.urlEncode(readACL_tag))
                        .append("=")
                        .append(DatasetUtils.urlEncode(readACL_value));
            }
            if (writeACL_tag != null && readACL_value != null) {
                restURL.append("&")
                        .append(DatasetUtils.urlEncode(writeACL_tag))
                        .append("=")
                        .append(DatasetUtils.urlEncode(writeACL_value));
            }
        } catch (UnsupportedEncodingException e) {
            throw new FatalException(e);
        }
        return restURL.toString();
    }

    /**
     * 
     * @param datasetName
     *            name of the dataset
     * @param tagFilerServer
     *            tagfiler server url
     * @return the message body to use for the file URL creation.
     * @thows FatalException if the URL cannot be constructed
     */
    public static final String getDatasetURLUploadBody(String datasetName,
            String tagFilerServer) throws FatalException {
        assert (datasetName != null && datasetName.length() > 0);
        assert (tagFilerServer != null && tagFilerServer.length() > 0);
        final StringBuffer body = new StringBuffer("action=put&url=");
        try {
            body.append(DatasetUtils.urlEncode(getDatasetQuery(datasetName,
                    tagFilerServer)));
        } catch (UnsupportedEncodingException e) {
            throw new FatalException(e);
        }

        return body.toString();
    }

    /**
     * 
     * @param datasetName
     *            name of the dataset
     * @param tagFilerServer
     *            tagfiler server url
     * @param baseDirectory
     *            base directory being used for the upload
     * @param file
     *            file that will be uploaded.
     * @param checksum
     *            checksum computed for the file
     * @return URL for uploading a file to the tagserver
     * @thows FatalException if the URL cannot be constructed
     */
    public static final String getFileUploadQuery(String datasetName,
            String tagFilerServer, String baseDirectory, File file,
            String checksum) throws FatalException {
        String readACL_value = TagFilerProperties.getProperty(readACL_PROPERTY);
        String readACL_tag = TagFilerProperties
                .getProperty(readACL_tag_PROPERTY);
        String writeACL_value = TagFilerProperties
                .getProperty(writeACL_PROPERTY);
        String writeACL_tag = TagFilerProperties
                .getProperty(writeACL_tag_PROPERTY);

        assert (datasetName != null && datasetName.length() > 0);
        assert (tagFilerServer != null && tagFilerServer.length() > 0);
        assert (baseDirectory != null);
        assert (file != null);
        assert (checksum != null);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(TagFilerProperties.getProperty("tagfiler.url.fileuri"));
        try {

            restURL.append(
                    DatasetUtils.urlEncode(DatasetUtils.generateDatasetPath(
                            datasetName, baseDirectory, file.getAbsolutePath())))
                    .append("?")
                    .append(DatasetUtils.urlEncode(TagFilerProperties
                            .getProperty("tagfiler.tag.containment")))
                    .append("=")
                    .append(DatasetUtils.urlEncode(datasetName))
                    .append("&")
                    .append(TagFilerProperties
                            .getProperty("tagfiler.tag.checksum")).append("=")
                    .append(checksum);

            if (readACL_tag != null && readACL_value != null) {
                restURL.append("&").append(DatasetUtils.urlEncode(readACL_tag))
                        .append("=")
                        .append(DatasetUtils.urlEncode(readACL_value));
            }
            if (writeACL_tag != null && readACL_value != null) {
                restURL.append("&")
                        .append(DatasetUtils.urlEncode(writeACL_tag))
                        .append("=")
                        .append(DatasetUtils.urlEncode(writeACL_value));
            }
        } catch (UnsupportedEncodingException e) {
            throw new FatalException(e);
        }

        return restURL.toString();
    }

    /**
     * 
     * @param datasetName
     *            name of the dataset
     * @param tagFilerServer
     *            tagfiler server URL
     * @return URL for querying for all the files in a dataset
     */
    public static final String getDatasetQuery(String datasetName,
            String tagFilerServer) {
        assert (datasetName != null && datasetName.length() > 0);
        assert (tagFilerServer != null && tagFilerServer.length() > 0);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(TagFilerProperties.getProperty("tagfiler.url.queryuri"))
                .append(TagFilerProperties
                        .getProperty("tagfiler.tag.containment")).append("=")
                .append(datasetName);
        return restURL.toString();
    }

    /**
     * 
     * @param datasetName
     *            name of the dataset
     * @param tagFilerServer
     *            tagfiler server URL
     * @return URL for querying for all the files in a dataset
     */
    public static final String getDatasetQueryUrl(String datasetName,
            String tagFilerServer) throws UnsupportedEncodingException {
        assert (datasetName != null && datasetName.length() > 0);
        assert (tagFilerServer != null && tagFilerServer.length() > 0);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(TagFilerProperties.getProperty("tagfiler.url.queryuri"))
                .append(DatasetUtils.urlEncode(TagFilerProperties
                        .getProperty("tagfiler.tag.containment"))).append("=")
                .append(DatasetUtils.urlEncode(datasetName));
        return restURL.toString();
    }

    /**
     * 
     * @param datasetName
     *            name of the dataset
     * @param tagFilerServer
     *            tagfiler server URL
     * @return the encoded URL for dataset
     * @throws UnsupportedEncodingException
     */
    public static final String getDatasetUrl(String datasetName,
            String tagFilerServer) throws UnsupportedEncodingException {
        assert (datasetName != null && datasetName.length() > 0);
        assert (tagFilerServer != null && tagFilerServer.length() > 0);

        final StringBuffer restURL = new StringBuffer(tagFilerServer).append(
                TagFilerProperties.getProperty("tagfiler.url.fileuri")).append(
                DatasetUtils.urlEncode(datasetName));
        return restURL.toString();
    }

    /**
     * 
     * @param datasetName
     *            name of the dataset
     * @param tagFilerServer
     *            tagfiler server URL
     * @param file
     *            the URL encoded file name
     * @return the encoded URL for file
     * @throws UnsupportedEncodingException
     */
    public static final String getFileUrl(String datasetName,
            String tagFilerServer, String file)
            throws UnsupportedEncodingException {
        assert (datasetName != null && datasetName.length() > 0);
        assert (tagFilerServer != null && tagFilerServer.length() > 0);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(TagFilerProperties.getProperty("tagfiler.url.fileuri"))
                .append(DatasetUtils.urlEncode(datasetName)).append(file);
        return restURL.toString();
    }

    /**
     * 
     * @param datasetName
     *            name of the dataset
     * @param tagFilerServer
     *            tagfiler server URL
     * @param file
     *            the URL encoded file name
     * @param tag
     *            the tag name
     * @return the encoded URL for file tag
     * @throws UnsupportedEncodingException
     */
    public static final String getFileTag(String datasetName,
            String tagFilerServer, String file, String tag)
            throws UnsupportedEncodingException {
        assert (datasetName != null && datasetName.length() > 0);
        assert (tagFilerServer != null && tagFilerServer.length() > 0);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(TagFilerProperties.getProperty("tagfiler.url.taguri"))
                .append(datasetName).append(file).append("/")
                .append(DatasetUtils.urlEncode(tag));
        return restURL.toString();
    }

    /**
     * Retrieves the URL to poll a tagfiler server to keep the session alive
     * 
     * @param tagFilerServer
     *            the tagfiler server URL to poll
     * @return the full url
     */
    public static final String getSessionPollURL(String tagFilerWebauthURL) {
        assert (tagFilerWebauthURL != null && tagFilerWebauthURL.length() > 0);
        return new StringBuffer(tagFilerWebauthURL).append(
                TagFilerProperties.getProperty("tagfiler.url.SessionPoll"))
                .toString();

    }

    /**
     * Retrieves the URL to poll a tagfiler server to keep the session alive
     * 
     * @param tagFilerServer
     *            the tagfiler server URL to poll
     * @return the full url
     */
    public static final String getExtendSessionPollURL(String tagFilerWebauthURL) {
        assert (tagFilerWebauthURL != null && tagFilerWebauthURL.length() > 0);
        return new StringBuffer(tagFilerWebauthURL).append(
                TagFilerProperties.getProperty("tagfiler.url.SessionPoll"))
                .append("?action=extend")
                .toString();

    }

    /**
     * Retrieves the URL to logout a user from the tagfiler server
     * 
     * @param webauthServer
     *            the URL of the webauth server
     * @return the full url
     */
    public static final String getLogoutURL(String webauthServer) {
        assert (webauthServer != null && webauthServer.length() > 0);
        final String logout = new StringBuffer(webauthServer).append(
                TagFilerProperties.getProperty("tagfiler.url.Logout"))
                .toString();
        return logout;
    }

    /**
     * Retrieves the URL to login to a tagfiler server
     * 
     * @param webauthServer
     *            the webauth server URL
     * @return the full URL
     */
    public static final String getLoginURL(String webauthServer) {
        assert (webauthServer != null && webauthServer.length() > 0);
        return new StringBuffer(webauthServer).append(
                TagFilerProperties.getProperty("tagfiler.url.Login"))
                .toString();
    }

    /**
     * Retrieves the URL of the status page
     * 
     * @param webauthServer
     *            the URL of the webauth server
     * @return the full URL
     */
    public static final String getStatusPageURL(String webauthServer) {
        assert (webauthServer != null && webauthServer.length() > 0);
        return new StringBuffer(webauthServer).append(
                TagFilerProperties.getProperty("tagfiler.url.Status"))
                .toString();
    }
    
    /**
     * Build a Date based on its string representation
     * 
     * @param value
     *            the string representation of the data
     * @return the Date
     */
    public synchronized static Date getDate(String value) throws ParseException {
    	SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
    	sdf.setLenient(false);
    	Date d = sdf.parse(value);
    	
    	return d;
    }

}
