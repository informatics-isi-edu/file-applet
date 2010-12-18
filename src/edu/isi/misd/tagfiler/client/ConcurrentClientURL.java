/**
 * 
 */
package edu.isi.misd.tagfiler.client;

import java.util.List;
import java.util.Map;

/**
 * @author Serban Voinea
 *
 */
public interface ConcurrentClientURL extends ClientURL{

    /**
     * Set the server base URL where uploads/downloads occur
     * 
     * @param baseURL
     *            the base URL
     */
	public void setBaseURL(String baseURL);
	
    /**
     * Set the size of a chunk block to be transfered
     * 
     * @param size
     *            the size of a chunk block to be transfered
     */
	public void setChunkSize(int size);
	
    /**
     * Set the maximum number of HTTP connections
     * 
     * @param conn
     *            the maximum number of HTTP connections
     */
	public void setMaxConnections(int conn);
	
    /**
     * Set the mode of transferring files
     * 
     * @param mode
     *            if true, the file will be transferred in chunks
     */
	public void setChunked(boolean mode);
	
    /**
     * Upload recursively a directory
     * 
     * @param dir
     *            the directory to be uploaded
     */
	public void uploadDirectory(String dir);
	
    /**
     * Upload a list of files
     * 
     * @param files
     *            the files to be uploaded
     */
	public void upload(List<String> files);
	
    /**
     * Upload a list of files
     * 
     * @param files
     *            the files to be uploaded
     * @param baseDirectory
     *            the base directory to be used for the uploaded files
     * @param checksumMap
     *            the map containing the checksums of the files 
     */
	public void upload(List<String> files, String baseDirectory, Map<String, String> checksumMap);
	
    /**
     * Upload a file
     * 
     * @param file
     *            the file to be uploaded
     */
	public void upload(String filename);
	
    /**
     * Download a file
     * 
     * @param file
     *            the file to be downloaded
     * @param outputDir
     *            the directory where the files will be downloaded
     */
	public void download(String file, String outputDir);
	
    /**
     * Download a file
     * 
     * @param file
     *            the file to be downloaded
     * @param outputDir
     *            the directory where the files will be downloaded
     * @param checksumMap
     *            the map containing the checksums of all files to be downloaded.
     * @param bytesMap
     *            the map containing the bytes of all files to be downloaded
     */
	public void download(String file, String outputDir, Map<String, String> checksumMap, Map<String, Long> bytesMap);
	
    /**
     * Download a list of files
     * 
     * @param files
     *            the files to be downloaded
     * @param outputDir
     *            the directory where the files will be downloaded
     */
	public void download(List<String> files, String outputDir);	
	
    /**
     * Download a list of files
     * 
     * @param files
     *            the files to be downloaded
     * @param outputDir
     *            the directory where the files will be downloaded
     * @param checksumMap
     *            the map containing the checksums of all files to be downloaded.
     * @param bytesMap
     *            the map containing the bytes of all files to be downloaded
     */
	public void download(List<String> files, String outputDir, Map<String, String> checksumMap, Map<String, Long> bytesMap);
	
}
