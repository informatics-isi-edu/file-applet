package edu.isi.misd.tagfiler.util;

/* 
 * Copyright 2010 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    public static final String QUERY_URI = "/query/";

    public static final String TAGS_URI = "/tags/";

    public static final String STUDY_URI = "/study/name=";

    private static final String NAME = "name=";

    private static final String NAME_REGEXP = "name:regexp:";

    private static final String VNAME = "vname=";
    
    public static final String KEY = "key=";
    
    public static final String VERSION = "version=";
    
    public static final String ANY_VERSION = "?versions=any";
    
    private static final String LATEST_VERSION = "?versions=latest";
    
    private static final String VCONTAINS = "(vcontains)/";
    
    private static final String VCONTAINS_PROPERTY = "vcontains=";
    
    private static final String TAGS_LIST = "?list=";
    
    private static final String IMAGE_SET = "Image Set";
    
    private static final String STUDY_TYPE = "Study Type";
    
    private static final String UPLOAD_BODY = "action=put&type=url&url=";
    
    private static final String LIMIT_NONE = "&limit=none";
    

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
     * @param key
     *            the dataset key
     * @param tagFilerServer
     *            URL of the tagfiler server
     * @param customTagMap
     *            map of the custom tags
     * @return the REST URL to create a tagfiler URL upload for the dataset.
     * @thows FatalException if the URL cannot be constructed
     */
    public static final String getDatasetURLUploadQuery(String datasetName, String key,
            String tagFilerServer, CustomTagMap customTagMap, String studyType)
            throws FatalException {

        if (datasetName == null || datasetName.length() == 0 || customTagMap == null ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer+", "+customTagMap);
        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(FILE_URI).append(NAME);
        try {
            restURL.append(DatasetUtils.urlEncode(datasetName))
                    .append("?")
                    .append(DatasetUtils.urlEncode(IMAGE_SET))
                    .append("&")
                    .append(KEY)
                    .append(key)
                    .append("&")
                    .append(TagFilerProperties.getProperty("tagfiler.tag.incomplete"));

            if (studyType != null) {
            	restURL.append("&")
            	.append(DatasetUtils.urlEncode(STUDY_TYPE))
            	.append("=")
            	.append(DatasetUtils.urlEncode(studyType));
            }
            Set<String> tagNames = customTagMap.getTagNames();
            for (String tagName : tagNames) {
            	String value = customTagMap.getValue(tagName);
            	if (value != null && value.length() > 0) {
                	restURL.append("&")
                    .append(DatasetUtils.urlEncode(tagName))
                    .append("=")
                    .append(DatasetUtils.urlEncode(value));
            	}
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
     * @param version
     *            the dataset version
     * @param tagFilerServer
     *            URL of the tagfiler server
     * @param tag
     *            the tag used as projection
     * @return the REST URL to create a tagfiler URL upload for the dataset.
     * @thows FatalException if the URL cannot be constructed
     */
    public static final String getDatasetURLUploadQuery(String datasetName, int version,
            String tagFilerServer, String tag)
            throws FatalException {
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);
        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(TAGS_URI);
        try {
            restURL.append(NAME).append(DatasetUtils.urlEncode(datasetName))
            		.append(version != 0 ? ";"+VERSION+version : "");
            if (tag != null) {
            	restURL.append("(")
            	.append(DatasetUtils.urlEncode(tag))
            	.append(")");
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
     * @return the REST URL for the dataset.
     * @thows FatalException if the URL cannot be constructed
     */
    public static final String getDatasetQuery(String datasetName,
            String tagFilerServer)
            throws FatalException {
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);
        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(STUDY_URI);
        try {
            restURL.append(DatasetUtils.urlEncode(datasetName));
        } catch (UnsupportedEncodingException e) {
            throw new FatalException(e);
        }
        return restURL.toString();
    }

    /**
     * 
     * @param datasetId
     *            the dataset id
     * @param tagFilerServer
     *            tagfiler server url
     * @return the message body to use for the file URL creation.
     * @thows FatalException if the URL cannot be constructed
     */
    public static final String getDatasetURLUploadBody(String datasetId,
            String tagFilerServer) throws FatalException {
        if (datasetId == null || datasetId.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetId+", "+tagFilerServer);
        final StringBuffer body = new StringBuffer(UPLOAD_BODY);
        try {
            body.append(DatasetUtils.urlEncode(getDatasetTagsQuery(datasetId,
                    tagFilerServer)));
        } catch (UnsupportedEncodingException e) {
            throw new FatalException(e);
        }

        return body.toString();
    }

    /**
     * 
     * @param checksum
     *            checksum computed for the file
     * @return URL suffix for uploading a file
     * @throws FatalException if the URL cannot be constructed
     */
    public static final String getUploadQuerySuffix(String tag, String value) throws FatalException {

        final StringBuffer restURL = new StringBuffer();
        
        if (tag != null) {
            try {
				restURL.append("&")
				.append(DatasetUtils.urlEncode(tag));
				if (value != null && value.trim().length() > 0) {
					restURL.append("=")
						.append(DatasetUtils.urlEncode(value));
				}
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        
        return restURL.toString();
    }

    /**
     * 
     * @param checksum
     *            checksum computed for the file
     * @return URL suffix for uploading a file
     * @throws FatalException if the URL cannot be constructed
     */
    public static final String getUploadQueryCheckPoint(long offset) throws FatalException {

        final StringBuffer restURL = new StringBuffer();
        
        try {
			restURL.append("?")
			.append(DatasetUtils.urlEncode(TagFilerProperties.getProperty("tagfiler.tag.checkpoint.offset")))
			.append("=")
			.append(offset);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
                .append(FILE_URI).append(NAME);
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
     * @param datasetId
     *            the dataset id
     * @param tagFilerServer
     *            tagfiler server URL
     * @return URL for querying for all the files in a dataset
     */
    public static final String getDatasetTagsQuery(String datasetId,
            String tagFilerServer) {
        if (datasetId == null || datasetId.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetId+", "+tagFilerServer);

        StringBuffer restURL = null;
		try {
			restURL = new StringBuffer(tagFilerServer)
			        .append(QUERY_URI)
			        .append(KEY)
			        .append(DatasetUtils.urlEncode(datasetId))
			        .append(ANY_VERSION);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
     * @param versionMap
     *            the files versions map
     * @param baseDirectory
     *            the base directory of the files
     * @return URL for querying for all the files in a dataset
     */
    public static final String getDatasetURLUploadBody(String datasetName,
            String tagFilerServer, List<String> files,  Map<String, Integer> versionMap,String baseDirectory) {
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);

        final StringBuffer restURL = new StringBuffer();
        HashSet <String> array = new HashSet <String>();
        for (String file : files) {
        	StringBuffer buff = new StringBuffer();
		    buff.append(generateDatasetPath(datasetName, baseDirectory, file)).append("@").append(versionMap.get(file));
			try {
				array.add(DatasetUtils.urlEncode(buff.toString()));
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        String tags = DatasetUtils.join(array, "&"+VCONTAINS_PROPERTY);
        if (tags.trim().length() > 0) {
        	restURL.append(VCONTAINS_PROPERTY);
        }
        restURL.append(tags);
        return restURL.toString();
    }

    /**
     * 
     * @param datasetName
     *            name of the dataset
     * @param version
     *            version of the dataset
     * @param tagFilerServer
     *            tagfiler server URL
     * @return the encoded URL for dataset
     * @throws UnsupportedEncodingException
     */
    public static final String getDatasetUrl(String datasetName, int version,
            String tagFilerServer) throws UnsupportedEncodingException {
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
        								.append(FILE_URI)
        								.append(NAME).append(DatasetUtils.urlEncode(datasetName))
        								.append(version != 0 ? ";"+VERSION+version : "");
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
                .append(FILE_URI).append(NAME)
                .append(DatasetUtils.urlEncode(datasetName));
        return restURL.toString();
    }

    /**
     * Get the URL for the dataset tags
     * @param datasetName
     *            name of the dataset
     * @param version
     *            version of the dataset
     * @param tagFilerServer
     *            tagfiler server URL
     * @param tags
     *            the list of tags separated by comma
     * @return the encoded URL for dataset tags
     * @throws UnsupportedEncodingException
     */
    public static final String getDatasetTags(String datasetName, int version,
            String tagFilerServer, String tags)
            throws UnsupportedEncodingException {
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(TAGS_URI)
                .append(NAME).append(DatasetUtils.urlEncode(datasetName))
                .append(version != 0 ? ";"+VERSION+version : "")
                .append(TAGS_LIST)
                .append(tags);
        return restURL.toString();
    }

    /**
     * Get the URL for the files tags
     * @param datasetName
     *            name of the dataset
     * @param version
     *            the dataset version
     * @param tagFilerServer
     *            tagfiler server URL
     * @param tags
     *            the list of tags separated by comma
     * @return the encoded URL for files tags
     * @throws UnsupportedEncodingException
     */
    public static final String getFilesTags(String datasetName, int version,
            String tagFilerServer, String tags)
            throws UnsupportedEncodingException {
        if (datasetName == null || datasetName.length() == 0 ||
        		tagFilerServer == null || tagFilerServer.length() == 0) 
        	throw new IllegalArgumentException(""+datasetName+", "+tagFilerServer);

        final StringBuffer restURL = new StringBuffer(tagFilerServer)
                .append(QUERY_URI)
                .append(version != 0 ? VNAME : NAME)
                .append(DatasetUtils.urlEncode(datasetName+(version != 0 ? "@"+version : "")))
                .append(VCONTAINS)
                .append("(")
                .append(tags)
                .append(")")
                .append(version == 0 ? LATEST_VERSION : ANY_VERSION)
                .append(LIMIT_NONE);
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
                .append(NAME_REGEXP)
                .append(DatasetUtils.urlEncode("^"+datasetName+"/"))
                .append("(")
                .append(tags)
                .append(")")
                .append(ANY_VERSION)
                .append(LIMIT_NONE);
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
		  if(strings == null || delimiter == null) {
		    return "";
		  }
		  
		  return DatasetUtils.joinEncode(strings.toArray(new String[0]), delimiter);
		}

    /**
     * Join the encoded elements of the array
     * 
     * @param strings
     *            the set of elements
     * @param delimiter
     *            the delimiter
     * @return the join string of the set elements
     */
	public static String joinEncode(String strings[], String delimiter){
		  if(strings == null || delimiter == null) {
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
	
    /**
     * Get the file name relative to the base directory
     * @param fileName
     *            name of the file relative to the base directory
     * @param baseDirectory
     *            the base directory
     * @return the name of the file 
     */
	public static String getBaseName(String filename, String baseDirectory) {
		String baseName = filename;
        if (baseDirectory != null) {
        	baseName = filename.replace(baseDirectory, "");
        }
        baseName = baseName.replaceAll("\\\\", "/")
        .replaceAll(":", "");
        
        if (!baseName.startsWith("/")) {
        	baseName = "/" + baseName;
        }
        
        return baseName;
	}
	
    /**
     * Get the file version from the url
     * @param url
     *            the url that created the file
     * @return the file version
     */
	public static int getVersion(String url) {
        if (url == null) throw new IllegalArgumentException("url is NULL");
		int res = 0;
		int index = url.lastIndexOf(VERSION);
		if (index != -1) {
			index += VERSION.length();
			String version = url.substring(index);
			index = version.indexOf("?");
			if (index != -1) {
				// we have query options
				version = version.substring(0, index);
			}
			res = Integer.parseInt(version);
		}
        
        return res;
	}
	
}
