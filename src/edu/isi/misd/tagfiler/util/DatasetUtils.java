package edu.isi.misd.tagfiler.util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;
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

    private static final String FILE_URI = "/file/";

    private static final String QUERY_URI = "/query/";

    public static final String TAGS_URI = "/tags/";

    private static final String STUDY = "Transmission Number";

    private static final String IMAGE_SET = "Image Set";

    /**
     * 
     * @param datasetName
     *            common dataset name
     * @param baseDirectory
     *            the base directory of the file
     * @param fileName
     *            name of the file in the dataset
     * @return a dataset name based on a common name, followed by a file path
     */
    public static String generateDatasetPath(String datasetName,
            String baseDirectory, String fileName) {
        if (datasetName == null || datasetName.length() == 0 || baseDirectory == null ||
        		fileName == null || fileName.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+baseDirectory+", "+fileName);

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
        if (url == null || url.length() == 0) throw new IllegalArgumentException(url);
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
        if (datasetName == null || datasetName.length() == 0) throw new IllegalArgumentException(datasetName);
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

        if (datasetName == null || datasetName.length() == 0 || customTagMap == null ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer+", "+customTagMap);
        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(FILE_URI);
        try {
            restURL.append(DatasetUtils.urlEncode(datasetName))
                    .append("?")
                    .append(DatasetUtils.urlEncode(IMAGE_SET));

            Set<String> tagNames = customTagMap.getTagNames();
            for (String tagName : tagNames) {
                restURL.append("&")
                        .append(DatasetUtils.urlEncode(tagName))
                        .append("=")
                        .append(DatasetUtils.urlEncode(customTagMap
                                .getValue(tagName)));
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
     *            URL of the tagfiler server
     * @return the REST URL to create a tagfiler URL upload for the dataset.
     * @thows FatalException if the URL cannot be constructed
     */
    public static final String getDatasetURLUploadQuery(String datasetName,
            String tagFilerServer)
            throws FatalException {
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);
        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(TAGS_URI);
        try {
            restURL.append(DatasetUtils.urlEncode(datasetName))
                    .append("/contains");
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
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);
        final StringBuffer body = new StringBuffer("action=put&url=");
        try {
            body.append(DatasetUtils.urlEncode(getDatasetTagsQuery(datasetName,
                    tagFilerServer)))
                    .append("&versioned=False");
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

        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0 ||
        		baseDirectory == null ||
        		file == null ||
        		checksum == null) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer+", "+
        			baseDirectory+", "+file+", "+checksum);
        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(FILE_URI);
        try {

            restURL.append(
                    DatasetUtils.urlEncode(DatasetUtils.generateDatasetPath(
                            datasetName, baseDirectory, file.getAbsolutePath())))
                    .append("?")
                    .append(DatasetUtils.urlEncode(STUDY))
                    .append("=")
                    .append(DatasetUtils.urlEncode(datasetName))
                    .append("&")
                    .append(TagFilerProperties
                            .getProperty("tagfiler.tag.checksum")).append("=")
                    .append(checksum);

        } catch (UnsupportedEncodingException e) {
            throw new FatalException(e);
        }

        return restURL.toString();
    }

    /**
     * 
     * @param datasetName
     *            name of the dataset
     * @param checksum
     *            checksum computed for the file
     * @return URL suffix for uploading a file
     * @throws FatalException if the URL cannot be constructed
     */
    public static final String getUploadQuerySuffix(String datasetName,
            String checksum) throws FatalException {

        if (datasetName == null || datasetName.length() == 0) 
        	throw new IllegalArgumentException(datasetName);

        final StringBuffer restURL = new StringBuffer();
        
        try {
            restURL.append("?")
                    .append(DatasetUtils.urlEncode(STUDY))
                    .append("=")
                    .append(DatasetUtils.urlEncode(datasetName));
            if (checksum != null) {
                restURL.append("&")
	                .append(TagFilerProperties
	                        .getProperty("tagfiler.tag.checksum")).append("=")
	                .append(checksum);
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
     * @return the Base URL for uploading a dataset
     * @throws FatalException if the URL cannot be constructed
     */
    public static final String getBaseUploadQuery(String datasetName,
            String tagFilerServer) throws FatalException {

        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(FILE_URI);
        try {

            restURL.append(
                    DatasetUtils.urlEncode(datasetName));

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
    public static final String getDatasetTagsQuery(String datasetName,
            String tagFilerServer) {
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(TAGS_URI)
                .append(datasetName)
                .append("/contains");
        return restURL.toString();
    }

    /**
     * 
     * @param datasetName
     *            name of the dataset
     * @param tagFilerServer
     *            tagfiler server URL
     * @param files
     *            the list of files to be registered
     * @param baseDirectory
     *            the base directory of the files
     * @return URL for querying for all the files in a dataset
     */
    public static final String getDatasetURLUploadBody(String datasetName,
            String tagFilerServer, List<String> files, String baseDirectory) {
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);

        final StringBuffer restURL = new StringBuffer();
        HashSet <String> array = new HashSet <String>();
        for (String file : files) {
        	StringBuffer buff = new StringBuffer();
		    buff.append(generateDatasetPath(datasetName, baseDirectory, file));
			try {
				array.add(DatasetUtils.urlEncode(buff.toString()));
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        String tags = DatasetUtils.join(array, "&contains=");
        if (tags.trim().length() > 0) {
        	restURL.append("contains=");
        }
        restURL.append(tags);
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
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
        								.append(FILE_URI)
        								.append(DatasetUtils.urlEncode(datasetName));
        return restURL.toString();
    }

    /**
     * 
     * @param datasetName
     *            name of the dataset
     * @param tagFilerServer
     *            tagfiler server URL
     * @return the encoded URL for file
     * @throws UnsupportedEncodingException
     */
    public static final String getBaseDownloadUrl(String datasetName,
            String tagFilerServer)
            throws UnsupportedEncodingException {
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(FILE_URI)
                .append(DatasetUtils.urlEncode(datasetName));
        return restURL.toString();
    }

    /**
     * Get the URL for the dataset tags
     * @param datasetName
     *            name of the dataset
     * @param tagFilerServer
     *            tagfiler server URL
     * @param tags
     *            the list of tags separated by comma
     * @return the encoded URL for dataset tags
     * @throws UnsupportedEncodingException
     */
    public static final String getDatasetTags(String datasetName,
            String tagFilerServer, String tags)
            throws UnsupportedEncodingException {
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(TAGS_URI)
                .append(datasetName).append("?list=")
                .append(tags);
        return restURL.toString();
    }

    /**
     * Get the URL for the files tags
     * @param datasetName
     *            name of the dataset
     * @param tagFilerServer
     *            tagfiler server URL
     * @param tags
     *            the list of tags separated by comma
     * @return the encoded URL for files tags
     * @throws UnsupportedEncodingException
     */
    public static final String getFilesTags(String datasetName,
            String tagFilerServer, String tags)
            throws UnsupportedEncodingException {
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(QUERY_URI)
                .append(DatasetUtils.urlEncode(STUDY))
                .append("=")
                .append(datasetName)
                .append("?list=")
                .append(tags);
        return restURL.toString();
    }

    /**
     * Join the elements of the set
     * 
     * @param strings
     *            the set of elements
     * @param delimiter
     *            the delimiter
     * @return the join string of the set elements
     */
	public static String join(Set<String> strings, String delimiter){
		  if(strings==null || delimiter == null) {
		    return "";
		  }
		 
		  StringBuffer buf = new StringBuffer();
		  boolean first = true;
		  
		  for (String value : strings) {
			  if (first) {
				  first = false;
			  } else {
			      buf.append(delimiter);
			  }
			  buf.append(value);
		  }
		 
		  return buf.toString();
		}

    /**
     * Join the encoded elements of the set
     * 
     * @param strings
     *            the set of elements
     * @param delimiter
     *            the delimiter
     * @return the join string of the set elements
     */
	public static String joinEncode(Set<String> strings, String delimiter){
		  if(strings==null || delimiter == null) {
		    return "";
		  }
		 
		  StringBuffer buf = new StringBuffer();
		  boolean first = true;
		  
		  for (String value : strings) {
			  if (first) {
				  first = false;
			  } else {
			      buf.append(delimiter);
			  }
			  try {
				buf.append(DatasetUtils.urlEncode(value));
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		  }
		 
		  return buf.toString();
		}

    /**
     * Round a double to 2 decimals
     * 
     * @param d
     *            the double
     * @return the double rounded to 2 decimals
     */
	public static double roundTwoDecimals(double d) {
    	DecimalFormat twoDForm = new DecimalFormat("#.##");
    	return Double.valueOf(twoDForm.format(d));
	}
	
    /**
     * Convert a digest value to a hexa string
     * 
     * @param cksum
     *            the byte array of the digest value
     * @return the hexa string of the digest value
     */
	public static String hexChecksum(byte[] cksum) {
	     String result = "";
	     for (int i=0; i < cksum.length; i++) {
	       result +=
	          Integer.toString( ( cksum[i] & 0xff ) + 0x100, 16).substring( 1 );
	      }
	     return result;
	}
}
