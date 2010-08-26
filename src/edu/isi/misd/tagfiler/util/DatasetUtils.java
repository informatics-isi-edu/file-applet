package edu.isi.misd.tagfiler.util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Set;

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
     * @param datasetName
     *            the string to encode
     * @return a URL-safe version of the string
     */
    public static String urlEncode(String datasetName) {
        assert (datasetName != null && datasetName.length() > 0);
        try {
            datasetName = URLEncoder.encode(datasetName, UTF_8);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

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
     */
    public static final String getDatasetURLUploadQuery(String datasetName,
            String tagFilerServer, CustomTagMap customTagMap) {
        assert (datasetName != null && datasetName.length() > 0);
        assert (tagFilerServer != null && tagFilerServer.length() > 0);
        assert (customTagMap != null);
        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(TagFilerProperties.getProperty("tagfiler.url.fileuri"))
                .append(DatasetUtils.urlEncode(datasetName))
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

        return restURL.toString();
    }

    /**
     * 
     * @param datasetName
     *            name of the dataset
     * @param tagFilerServer
     *            tagfiler server url
     * @return the message body to use for the file URL creation.
     */
    public static final String getDatasetURLUploadBody(String datasetName,
            String tagFilerServer) {
        assert (datasetName != null && datasetName.length() > 0);
        assert (tagFilerServer != null && tagFilerServer.length() > 0);
        final StringBuffer body = new StringBuffer("action=put&url=")
                .append(DatasetUtils.urlEncode(getDatasetQuery(datasetName,
                        tagFilerServer)));
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
     */
    public static final String getFileUploadQuery(String datasetName,
            String tagFilerServer, String baseDirectory, File file,
            String checksum) {
        assert (datasetName != null && datasetName.length() > 0);
        assert (tagFilerServer != null && tagFilerServer.length() > 0);
        assert (baseDirectory != null);
        assert (file != null);
        assert (checksum != null);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(TagFilerProperties.getProperty("tagfiler.url.fileuri"))
                .append(DatasetUtils.urlEncode(DatasetUtils
                        .generateDatasetPath(datasetName, baseDirectory,
                                file.getAbsolutePath())))
                .append("?")
                .append(DatasetUtils.urlEncode(TagFilerProperties
                        .getProperty("tagfiler.tag.containment")))
                .append("=")
                .append(DatasetUtils.urlEncode(datasetName))
                .append("&")
                .append(TagFilerProperties.getProperty("tagfiler.tag.checksum"))
                .append("=").append(checksum);

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
}
