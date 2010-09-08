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
public class FileDownloadImplementation implements FileDownload {

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

    // the dataset control number
    private String controlNumber;

    // the session cookie
    private Cookie cookie = null;

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

        try {
            fileNames = new ArrayList<String>();
            checksumMap = new HashMap<String, String>();
            encodeMap = new HashMap<String, String>();
            bytesMap = new HashMap<String, Long>();

            setCustomTags();
            getDataSet();
        } catch (Exception e) {
            e.printStackTrace();
            fileUploadListener.notifyError(e);
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
        baseDirectory = destDir;

        Set<String> files = encodeMap.keySet();
        boolean result = false;
        fileUploadListener.notifyStart(controlNumber, datasetSize);
        try {
            for (String file : files) {
                downloadFile(file);
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

        String value = response.getEntity(String.class);
        value = value.substring(value.indexOf('=') + 1);
        response.close();

        return value;
    }

    /**
     * Performs the file download.
     * 
     * @param file
     *            the file name.
     */
    private boolean downloadFile(String file) {
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

            InputStream in = response.getEntityInputStream();

	    cookie = JerseyClientUtils.updateSessionCookie(response, applet, cookie);

            // write the file into the destination
            File dir = new File(baseDirectory);
            int index = file.lastIndexOf(File.separatorChar);
            if (index != -1) {
                dir = new File(baseDirectory + "/" + file.substring(0, index));
            }

            if (dir.isDirectory() || dir.mkdirs()) {
                fileUploadListener.notifyFileTransferStart(baseDirectory + "/"
                        + file);
                FileOutputStream fos = new FileOutputStream(baseDirectory + "/"
                        + file);
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
                File downloadFile = new File(baseDirectory + "/" + file);
                String checksum = LocalFileChecksum
                        .computeFileChecksum(downloadFile);
                if (!checksum.equals(checksumMap.get(file))) {
                    throw new Exception(
                            "Checksum failed for downloading the file: " + file);
                }
                fileUploadListener.notifyFileTransferComplete(baseDirectory
                        + "/" + file, bytesMap.get(file));
            } else {
                in.close();
                throw new Exception("Can not make directory: "
                        + dir.getAbsolutePath());
            }

        } catch (Exception e) {
            e.printStackTrace();
            fileUploadListener.notifyError(e);
            result = false;
        }
        return result;
    }

}
