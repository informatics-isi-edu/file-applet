package edu.isi.misd.tagfiler.client;

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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import edu.isi.misd.tagfiler.exception.FatalException;
import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.FileWrapper;
import edu.isi.misd.tagfiler.util.LocalFileChecksum;
import edu.isi.misd.tagfiler.util.TagFilerProperties;


/**
 * Class for managing parallel transfer files
 * 
 * @author Serban Voinea
 *
 */
public class ConcurrentJakartaClient extends JakartaClient implements ConcurrentClientURL {

	// the server base URL where uploads/downloads occur
	private String baseURL;
	
    // the directory for uploads
	private String baseDirectory;
	
    // the listener where the client receives callback notifications
	private ClientURLListener listener;
	
    // the maximum number of HTTP connections used by the client
	private int connections;
	
    // the queue for the HTTP requests
	private LinkedBlockingQueue<FileChunk> WorkerQueue = new LinkedBlockingQueue<FileChunk>();
	
    // the queue for passing elements to the Worker Queue
	private LinkedBlockingQueue<FileChunk> TransmissionQueue = new LinkedBlockingQueue<FileChunk>();
	
    // the map with the files transfer in progress
	private HashMap<String, FileItem> filesCompletion = new HashMap<String, FileItem>();
	
	private Hashtable<String, Long> downloadCheckPoint = new Hashtable<String, Long>();
	
    // the total number of files to be uploaded or downloaded
	private int totalFiles;
	
    // the chunk size of a block to be transfered
	private int chunkSize;
	
    // if true, the file will be transferred in chunks
	private boolean allowChunks;
	
    // object used for threads synchronization on listener actions
	private Object listenerLock = new Object();
	
    // object used for threads synchronization on request actions
	private Object requestLock = new Object();
	
    // flag to cancel the requests
	private boolean cancel;
	
    // true if a failure occurred
	private boolean failure;
	
    // flag to mark a download process
	private boolean isDownload;
	
    // flag to mark a checksum computation
	private boolean enableChecksum;
	
    // the Dataset Id
    protected String datasetId;

    // wrapper for WorkerQueue
	private QueueWrapper workerWrapper = new QueueWrapper();
	
    // map containing the checksums of all files to be transferred.
	private Map<String, String> checksumMap;

    // map containing the versions of all files to be transferred.
	private Map<String, Integer> versionMap;
	
    // directory for writing the download check point file
	private String checkPointDir;

	/**
     * Excludes "." and ".." from directory lists in case the client is
     * UNIX-based.
     */
    private static final FilenameFilter excludeDirFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return (!name.equals(".") && !name.equals(".."));
        }
    };

    /**
     * Constructor
     * 
     * @param connections
     *            the maximum number of HTTP connections
     * @param listener
     *            the listener to receive callbacks
     */
	public ConcurrentJakartaClient (int connections, int socketBufferSize, ClientURLListener listener) {
		super(connections, socketBufferSize);
		this.connections = connections;
		this.listener = listener;
		workerWrapper.maxThreads = this.connections;
	}
	
    /**
     * Set the server base URL where uploads/downloads occur
     * 
     * @param baseURL
     *            the base URL
     */
	public void setBaseURL(String baseURL) {
		this.baseURL = baseURL;
	}

    /**
     * Set the size of a chunk block to be transfered
     * 
     * @param size
     *            the size of a chunk block to be transfered
     */
	public void setChunkSize(int size) {
		chunkSize = size;
	}
	
    /**
     * Set the maximum number of HTTP connections
     * 
     * @param conn
     *            the maximum number of HTTP connections
     */
	public void setMaxConnections(int conn) {
		connections = conn;
	}
	
    /**
     * Set the mode of transferring files
     * 
     * @param mode
     *            if true, the file will be transferred in chunks
     */
	public void setChunked(boolean mode) {
		allowChunks = mode;
	}
	
    /**
     * Upload recursively a directory
     * 
     * @param dir
     *            the directory to be uploaded
     */
	public void uploadDirectory(String dir) {
        if (dir == null) throw new IllegalArgumentException(dir);
		upload(getFiles(new File(dir)));
	}
	
    /**
     * Upload a list of files
     * 
     * @param files
     *            the files to be uploaded
     */
	public void upload(List<FileWrapper> files) {
		enableChecksum = listener.isEnableChecksum();
		datasetId = listener.getDatasetId();
		totalFiles = files.size();
		for (FileWrapper file : files) {
			uploadFile(file);
		}
		Thread thread = new DispatcherThread();
		thread.start();
	}
	
    /**
     * Upload a list of files
     * 
     * @param files
     *            the files to be uploaded
     * @param baseDirectory
     *            the base directory to be used for the uploaded files
     * @param checksumMap
     *            the checksum Map for the uploaded files
     * @param versionMap
     *            the version Map for the uploaded files
     */
	public void upload(List<FileWrapper> files, String baseDirectory, Map<String, String> checksumMap, Map<String, Integer> versionMap) {
		// TODO Auto-generated method stub
		this.baseDirectory = baseDirectory;
		this.checksumMap = checksumMap;
		this.versionMap = versionMap;
		
		// this avoids a deadlock in case an error arrives before finishing to load the queue
		synchronized (requestLock) {
			upload(files);
		}
	}
	
    /**
     * Upload a file
     * 
     * @param file
     *            the file to be uploaded
     */
	public void upload(FileWrapper fileWrapper) {
		enableChecksum = listener.isEnableChecksum();
		datasetId = listener.getDatasetId();
		totalFiles = 1;
		uploadFile(fileWrapper);
		Thread thread = new DispatcherThread();
		thread.start();
	}
	
	
    /**
     * Download a file
     * 
     * @param file
     *            the file to be downloaded
     * @param outputDir
     *            the directory where the files will be downloaded
     */
	public void download(String file, String outputDir) {
		// HTTP for the file length
		download(file, outputDir, null, null, null);
	}
	
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
     * @param versionMap
     *            the version Map for the uploaded files
     */
	public void download(String file, String outputDir, Map<String, String> checksumMap, Map<String, Long> bytesMap, Map<String, Integer> versionMap) {
        if (file == null || outputDir == null) throw new IllegalArgumentException(file+", "+outputDir);
		enableChecksum = listener.isEnableChecksum();
		datasetId = listener.getDatasetId();
		totalFiles = 1;
		isDownload = true;
		checkPointDir = outputDir;
        downloadFile(new FileWrapper(file, 0, versionMap.get(file), bytesMap.get(file)), outputDir, checksumMap, bytesMap, versionMap);
	}
	
    /**
     * Download a list of files
     * 
     * @param files
     *            the files to be downloaded
     * @param outputDir
     *            the directory where the files will be downloaded
     */
	public void download(List<String> files, String outputDir) {
        List<FileWrapper> filesList = new ArrayList<FileWrapper>();
        for (String file : files) {
			filesList.add(new FileWrapper(file, 0, 0, 0));
        }
		download(filesList, outputDir, null, null, null);
	}
	
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
     * @param versionMap
     *            the version Map for the uploaded files
     */
	public void download(List<FileWrapper> files, String outputDir, Map<String, String> checksumMap, Map<String, Long> bytesMap, Map<String, Integer> versionMap) {
        if (files == null || outputDir == null) throw new IllegalArgumentException(""+files+", "+outputDir);
		enableChecksum = listener.isEnableChecksum();
		datasetId = listener.getDatasetId();
		totalFiles = files.size();
		isDownload = true;
		checkPointDir = outputDir;

		for (FileWrapper file : files) {
			downloadCheckPoint.put(file.getName(), file.getOffset());
		}
		synchronized (requestLock) {
			for (FileWrapper file : files) {
				downloadFile(file, outputDir, checksumMap, bytesMap, versionMap);
			}
		}
	}
	
    /**
     * Upload a file
     * 
     * @param fileWrapper
     *            the wrapper of the file to be uploaded
     */
	private void uploadFile(FileWrapper fileWrapper) {
		String filename = fileWrapper.getName();
		File file = new File(filename);
		long length = file.length();
		FileChecksum fileChecksum = null;
		
		// mark file to be uploaded
		FileItem fi = new FileItem(filename, length-fileWrapper.getOffset());
		filesCompletion.put(filename, fi);
		FileChunk fc = null;
		
		if (!allowChunks || length <= chunkSize) {
			// a single chunk - put it directly into the Completion Queue
			fc = new FileChunk(filename, 0, length, length);
			fc.setLastChunk(true);
		} else {
			// the files will be sent in chunks
			// put the first chunk into Worker Queue
			if (fileWrapper.getOffset() > 0) {
				// file partial uploaded - resume from the check point offset
				long size = chunkSize;
				long remaining = length - fileWrapper.getOffset();
				if (remaining < (long) chunkSize) {
					size = remaining;
				}
				fc = new FileChunk(filename, fileWrapper.getOffset(), size, length);
				fc.setVersion(fileWrapper.getVersion());
				fc.setFirstChunk(true);
				if (remaining <= (long) chunkSize) {
					fc.setLastChunk(true);
				}
				fi.setLastCheckPoint((int) (fileWrapper.getOffset()/chunkSize));
				if (enableChecksum) {
					// re-compute the checksum up to the check point offset
					fileChecksum = initChecksum(fileWrapper);
					fc.setFileChecksum(fileChecksum);
				}
			} else {
				// entire file to be uploaded
				fc = new FileChunk(filename, 0, chunkSize, length);
			}
		}
		if (enableChecksum && fileChecksum == null) {
			// files without check point offset
			fc.setFileChecksum(new FileChecksum(filename, length, connections-1));
		}
		// load the queue with the first chunk
		workerWrapper.put(fc);
	}
	
    /**
     * Initialize the checksum for resuming a file transfer 
     * 
     * @param fileWrapper
     *            the wrapper of the file to be transferred 
     * @return the file checksum wrapper
     */
	private FileChecksum initChecksum(FileWrapper fileWrapper) {
		String filename = fileWrapper.getName();
		String file;
		long fileLength;
		if (isDownload) {
			file = checkPointDir + File.separator + filename;
			fileLength = fileWrapper.getFileLength();
		} else {
			file = filename;
			fileLength = (new File(filename)).length();
		}
		// initialize the checksum
		FileChecksum fileChecksum = new FileChecksum(filename, fileLength, connections-1);
		// Read the chunks already uploaded
		try {
			FileInputStream fis = new FileInputStream(file);
	        BufferedInputStream bis = new BufferedInputStream(fis, chunkSize);
			long offset = fileWrapper.getOffset();
			long length = offset;
			int slot = 0;
			while (length > 0) {
				// read the chunk and update the checksum
				int size = length >= chunkSize ? chunkSize : (int) length;
				byte[] chunk = read(bis, size);
				fileChecksum.put(chunk, size, slot++);
				length -= size;
			}
			if (offset != offset/chunkSize*chunkSize) {
				// we have an offset that it is not multiple of chunkSize
				// decrement the expected chunk as offset/chunkSize will be the first slot sent
				fileChecksum.decSlots();
			}
			bis.close();
			fis.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return fileChecksum;
	}
	
    /**
     * Read from a file 
     * 
     * @param bis
     *            the BufferedInputStream of the file 
     * @param length
     *            the number of bytes to be read from the file 
     * @return an array with the read bytes of the chunk
     */
	private byte[] read(BufferedInputStream bis, int length) {
        byte[] ret = new byte[length];
        int remaining = length;
        int offset = 0;
        int res;
        try {
			while ((res = bis.read(ret, offset, remaining)) != -1) {
			    remaining -= res;
			    offset += res;
			    if (remaining == 0) {
			    	break;
			   }
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return ret;
	}
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
     * @param versionMap
     *            the version Map for the uploaded files
     */
	private void downloadFile(FileWrapper fileWrapper, String outputDir, Map<String, String> checksumMap, Map<String, Long> bytesMap, Map<String, Integer> versionMap) {
        if (fileWrapper == null || outputDir == null) throw new IllegalArgumentException(fileWrapper+", "+outputDir);
		String file = fileWrapper.getName();
        long totalLength = 0;
        
		if (bytesMap != null) {
			totalLength = bytesMap.get(file);
		} else {
			// get the file length from the HEAD request
			String cookie = getCookie();
			ClientURLResponse response = null;
			
			// set the file download request URL
			StringBuffer url = new StringBuffer();
			if (baseURL != null) {
				url.append(baseURL);
			}
			try {
				url.append(URLEncoder.encode("/"+file, "UTF-8"));
			} catch (UnsupportedEncodingException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			int version = versionMap.get(file);
			url.append("@").append(version);
			if (browser) {
				System.out.println("Sending HEAD request: url: "+url+", File: '"+file+"'");
			}
			response = getFileLength(url.toString(), cookie);
			if (response == null) {
				notifyFailure("Failure in getting the length of the file \"" + file + "\" of dataset \"" + listener.getDataset() + "\".\\n\\n" +
						TagFilerProperties.getProperty("tagfiler.connection.lost"), true);
				return;
			}
			if (200 == response.getStatus()) {
				totalLength = response.getResponseSize();
			} else {
				String err = ConcurrentJakartaClient.getStatusMessage(response);
				response.release();
    			notifyFailure("<p>Failure in downloading the file \"" + file + "\".<p>Can not get the length of the file.<p>Status " + err);
			}
		}
		
		// mark file to be downloaded
		filesCompletion.put(file, new FileItem(file, totalLength - fileWrapper.getOffset()));
		
		// put all the chunks into the HTTP request queue
		long position = 0;
		FileChecksum fileChecksum = null;
		if (allowChunks && fileWrapper.getOffset() > 0) {
			// file partial downloaded - resume from the check point offset
			position = fileWrapper.getOffset();
			filesCompletion.get(file).setLastCheckPoint((int) (fileWrapper.getOffset()/chunkSize));
			if (enableChecksum) {
				// re-compute the checksum up to the check point offset
				fileChecksum = initChecksum(fileWrapper);
			}
		}
		if (fileChecksum == null && checksumMap != null && checksumMap.get(file) != null && enableChecksum) {
			fileChecksum = new FileChecksum(file, totalLength, connections-1);
		}
		while (position < totalLength || totalLength == 0) {
			long size = allowChunks ? chunkSize : totalLength;
			if (position+size > totalLength) {
				size = totalLength - position;
			}
			FileChunk fc = new FileChunk(file, position, size, totalLength, outputDir);
			if (checksumMap != null && checksumMap.get(file) != null && enableChecksum) {
				fc.setFileChecksum(fileChecksum);
				fc.setChecksum(checksumMap.get(file));
			}
			fc.setVersion(versionMap.get(file));
			workerWrapper.put(fc);
			position += size;
			if (totalLength == 0) {
				break;
			}
		}
	}
	
    /**
     * Get recursively the files of a directory
     * 
     * @param dir
     *            the directory
     * @return the list with the files names
     */
    private List<FileWrapper> getFiles(File dir) {
        if (dir == null) throw new IllegalArgumentException(""+dir);
        List<FileWrapper> files = new ArrayList<FileWrapper>();
        File[] children = dir.listFiles(excludeDirFilter);
        
        for (int i = 0; i < children.length; i++) {
            if (children[i].isDirectory()) {
            	files.addAll(getFiles(children[i]));
            } else if (children[i].isFile()) {
            	files.add(new FileWrapper(children[i].getAbsolutePath(), 0, 0, children[i].length()));
            }
        }
        
        return files;
    }
    
    /**
     * Terminate the threads from the pools
     * Put in the queue elements to mark threads termination
     */
	private void terminateThreads() {
		synchronized (this) {
			cancel = true;
		}
		if (!browser) {
			System.out.print("\nHTTP Connections: " + connections);
		}
 		workerWrapper.terminateThreads();
		try {
			TransmissionQueue.put(new FileChunk("", 0, 0, 0));
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (!browser) {
			System.out.println(".");
		}
	}
	
    /**
     * Upload or download completed successfully. 
     * Terminate the threads from the pools
     * Notify listener about operation completion
     */
	private void notifySuccess() {
		// wait until the request feed ended to avoid possible deadlocks 
		synchronized (requestLock) {
			terminateThreads();
		}
		synchronized (listenerLock) {
			listener.notifySuccess();
		}
	}
	
    /**
     * Upload or download has failed. 
     * Terminate the threads from the pools
     * Notify listener about failure
     */
	private void notifyFailure(String err) {
		notifyFailure(err, false);
	}
	
    /**
     * Upload or download has failed. 
     * Terminate the threads from the pools
     * Notify listener about failure
     */
	private void notifyFailure(String err, boolean connectionBroken) {
		// wait until the request feed ended to avoid possible deadlocks 
		synchronized (requestLock) {
			if (failure) {
				return;
			}
			failure = true;
			terminateThreads();
			// write the download check point file
			writeCheckPoint();
		}
		
		synchronized (listenerLock) {
			listener.notifyFailure(err, connectionBroken);
		}
	}
	
    /**
     * Write the check point for download failure. 
     * It is written only once: at notifyFailure or when the applet exits unexpected:
     *	- user exits applet page or the browser was closed or the browser crashed
     */
	public void writeCheckPoint() {
		// wait until the request feed ended to avoid possible deadlocks 
		if (downloadCheckPoint != null && downloadCheckPoint.size() > 0) {
	    	String filename = checkPointDir + File.separator + TagFilerProperties.getProperty("tagfiler.checkpoint.file");
	    	try {
				FileOutputStream fos = new FileOutputStream(filename);
				ObjectOutputStream out = new ObjectOutputStream(fos);
				out.writeObject(downloadCheckPoint);
				out.close();
				fos.close();
				System.out.println("Check Points Written: "+downloadCheckPoint);
				downloadCheckPoint = null;
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
    /**
     * Checks for and saves updated session cookie
     * The request will be performed by the listener
     */
	private void updateSessionCookie() {
		synchronized (listenerLock) {
			listener.updateSessionCookie();
		}
	}
	
    /**
     * Get the cookie from the listener
     */
	private String getCookie() {
		synchronized (listenerLock) {
			return listener.getCookie();
		}
	}
	
    /**
     * Notify the listener that a file transfer completed
     * @param filename
     *            the file name
     * @param size
     *            the chunk size transferred
     */
	private void notifyFileTransfered(String filename, long size) {
		synchronized (listenerLock) {
			listener.notifyFileTransfered(size);
		}
		
		synchronized (this) {
			filesCompletion.remove(filename);
			if (--totalFiles == 0 && !isDownload) {
				notifySuccess();
			}
		}
	}
	
    /**
     * Notify the listener that a chunk transfer completed
     * @param size
     *            the chunk size transferred
     */
	private void notifyChunkTransfered(long size) {
		synchronized (listenerLock) {
			listener.notifyChunkTransfered(size);
		}
	}
	
    /**
     * Send an upload request 
     * Open the file at the specified offset
     * Execute the upload HTTP request
     * If this is the first chunk of a sequence, then generate the rest of the upload requests
     * @param file
     *            the file to be uploaded
     */
	private void sendUpload(FileChunk file) {
		
		// check if the request will be cancelled due to a previous failure
		boolean cancel = false;
		synchronized (this) {
			cancel = this.cancel;
		}
		if (cancel) {
			return;
		}
		
		try {
			int status =0;
			String cookie = getCookie();
			
			// set the file upload request URL
			StringBuffer url = new StringBuffer();
			if (baseURL != null) {
				url.append(baseURL);
			}
			try {
				url.append(URLEncoder.encode(DatasetUtils.getBaseName(file.getName(), baseDirectory), "UTF-8"));
				int fileVersion = file.getVersion();
				if (fileVersion > 0) {
					url.append(";"+DatasetUtils.VERSION).append(fileVersion);
				}
			} catch (UnsupportedEncodingException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			
			FileItem fi = filesCompletion.get(file.getName());

			// if this is the last chunk, Dataset Name and Checksum parameters will be added
			String cksum = null;
			byte ret[] = null;
			long slotOffset = 0;
			int slot = 0;
			long slotUpperBound = 0;
			if (file.getLength() == file.getTotalLength()) {
				if (enableChecksum) {
					try {
						cksum = LocalFileChecksum.computeFileChecksum(new File(file.getName()));
						notifyChunkTransfered(file.getLength());
					} catch (FatalException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} else {
				// Read the chunk to be uploaded
				FileInputStream fis = new FileInputStream(file.getName());
				try {
					fis.skip(file.getOffset());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                BufferedInputStream bis = new BufferedInputStream(fis, chunkSize);
                ret = new byte[(int) file.getLength()];
                int remaining = (int) file.getLength();
				long writeOffset = file.getOffset();
                int offset = 0;
                int res;
                try {
					while ((res = bis.read(ret, offset, remaining)) != -1) {
					    remaining -= res;
					    offset += res;
					    if (remaining == 0) {
					    	if (enableChecksum) {
						        file.getFileChecksum().put(ret, (int) file.getLength(), (int) (writeOffset/chunkSize));
					    	} 
					    	slot = (int) (writeOffset/chunkSize);
					    	slotUpperBound = writeOffset + file.getLength();
					    	slotOffset = fi.nextCheckPoint(slot, slotUpperBound);
					    	break;
					   }
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				try {
					bis.close();
					fis.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				if (file.isLastChunk() && enableChecksum) {
					cksum = file.getFileChecksum().getDigest();
				}
			}
			
			String params = null;
			if (file.getLength() == file.getTotalLength() || file.isLastChunk()) {
				try {
					if (enableChecksum) {
						checksumMap.put(DatasetUtils.getBaseName(file.getName(), baseDirectory), cksum);
						params = DatasetUtils.getUploadQueryCheckPoint(file.getTotalLength());
						params += DatasetUtils.getUploadQuerySuffix(cksum);
					}
				} catch (FatalException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				try {
					if (slotOffset != -1) {
						params = DatasetUtils.getUploadQueryCheckPoint(slotOffset);
					} 
				} catch (FatalException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			
			if (params != null) {
				url.append(params);
			}
			
			// Execute the HTTP request
			ClientURLResponse response = null;
			if (browser) {
				System.out.println("Sending upload: url: "+url+", File: "+file);
			}
			if (file.getLength() == file.getTotalLength()) {
				// small file; upload the entire file
				response = postFile(url.toString(), new File(file.getName()), cookie);
			} else {
				response = postFile(url.toString(), ret, file.getLength(), file.getOffset(), file.getTotalLength(), cookie);
			}
			
			if (response == null) {
				notifyFailure("Failure in uploading the file \"" + file + "\" of dataset \"" + listener.getDataset() + "\".\\n\\n" +
						TagFilerProperties.getProperty("tagfiler.connection.lost"), true);
				return;
			}
			
			// Check result
			status = response.getStatus();
			updateSessionCookie();
			FileChunk fc = null;
			if (201 == status || 204 == status) {
				if (params != null) {
					// a check point was set
					fi.resetBusy(slot, slotUpperBound, slotOffset);
				} else {
					fi.updateCheckPoint(slot, slotUpperBound);
				}
				if (file.getOffset() == 0) {
					// set the file version
					int version = DatasetUtils.getVersion(response.getLocationString());
					versionMap.put(file.getName(), version);
				}
				long size = fi.update(file.getLength());
				if (size > 0) {
					long position = file.getTotalLength() - size;
					int version = DatasetUtils.getVersion(response.getLocationString());
					if (size <= chunkSize) {
						// put the last chunk into the Transmission queue
						fc = new FileChunk(file.getName(), position, size, file.getTotalLength());
						if (enableChecksum) {
							fc.setFileChecksum(file.getFileChecksum());
						}
						fc.setLastChunk(true);
						fc.setVersion(version);
						synchronized (this) {
							try {
								TransmissionQueue.put(fc);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					} else if (file.getOffset() == 0 || file.isFirstChunk()) {
						// first chunk was completed
						// put the rest of chunks but the last into the Transmission Queue
						position = file.getOffset() + chunkSize;
						long filesize = file.getTotalLength();
						synchronized (this) {
							while (position + chunkSize < filesize) {
								try {
									fc = new FileChunk(file.getName(), position, chunkSize, filesize);
									if (enableChecksum) {
										fc.setFileChecksum(file.getFileChecksum());
									}
									fc.setVersion(version);
									TransmissionQueue.put(fc);
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								position += chunkSize;
							}
						}
					}
				}
			} else {
				String err = ConcurrentJakartaClient.getStatusMessage(response);
				response.release();
				response = null;
				notifyFailure("<p>Failure in uploading the file \"" + file + "\".<p>Status " + err);
			}
			if (response != null) {
				response.release();
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
    /**
     * Send a download request 
     * Execute the download HTTP request
     * Put the response into the post request processing queue
     * @param file
     *            the file to be uploaded
     */
	private void sendDownload(FileChunk file, WorkerThread thread) {
		// check if the request will be cancelled due to a previous failure
		boolean cancel = false;
		synchronized (this) {
			cancel = this.cancel;
		}
		if (cancel) {
			return;
		}
		String cookie = getCookie();
		
		// set the file download request URL
		StringBuffer url = new StringBuffer();
		if (baseURL != null) {
			url.append(baseURL);
		}
		try {
			url.append(URLEncoder.encode("/"+file.getName(), "UTF-8"));
			if (file.getVersion() > 0) {
				url.append(";"+DatasetUtils.VERSION).append(file.getVersion());
			}
		} catch (UnsupportedEncodingException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		// Execute the HTTP request
		ClientURLResponse response = null;
		if (browser) {
			System.out.println("Sending download: url: "+url+", File: "+file);
		}
		if (file.getLength() == file.getTotalLength()) {
			// small file; upload the entire file
			response = downloadFile(url.toString(), cookie);
		} else {
			response = downloadFile(url.toString(), file.getLength(), file.getOffset(), cookie);
		}
		if (response == null) {
			notifyFailure("Failure in downloading the file \"" + file + "\" of dataset \"" + listener.getDataset() + "\".\\n\\n" +
					TagFilerProperties.getProperty("tagfiler.connection.lost"), true);
			return;
		}
		
		// Check result
		int status = response.getStatus();
		updateSessionCookie();
		if (200 == status || 206 == status) {
			// place response into the post request processing queue
			file.setResponse(response);
			processDownloadResult(file, thread);
		} else {
			String err = ConcurrentJakartaClient.getStatusMessage(response);
			response.release();
			notifyFailure("<p>Failure in downloading the file \"" + file + "\".<p>Status " + err);
		}
	}
	
    /**
     * Execute post processing of a download request 
     * Write the response content into the local system file
     * @param file
     *            the file to be uploaded
     */
	private void processDownloadResult(FileChunk file, WorkerThread thread) {
		try {
		    String localFile = file.getName().replace('/', File.separatorChar);
			FileItem fi = filesCompletion.get(file.getName());
		    
		    // create intermediate directories if necessary
            File dir = new File(file.getDownloadDir());
            int index = localFile.lastIndexOf(File.separatorChar);
            if (index != -1) {
                dir = new File(file.getDownloadDir() + File.separatorChar + localFile.substring(0, index));
                boolean OK = true;
                synchronized (this) {
                    if (!dir.isDirectory() && !dir.mkdirs()) {
                    	OK = false;
                    }
                }
                if (!OK) {
        			notifyFailure("<p>Failure in downloading the file \"" + file + "\".<p>Can not make directory \"" + dir + "\".");
                	return;
                }
            }

		    // write the chunk into the local file 
            RandomAccessFile raf = thread.getFileHandle(file.getName());
            if (raf == null) {
    			raf = new RandomAccessFile(file.getDownloadDir() + File.separatorChar + localFile,"rw");
    			thread.addFile(file.getName(), raf, file.getChecksum(), file.getFileChecksum());
            }
			try {
				raf.seek(file.getOffset());
				
				// read the response content and write it into the local file
				long writeOffset = file.getOffset();
                byte ret[] = new byte[chunkSize];
                InputStream is = file.getResponse().getEntityInputStream();
                BufferedInputStream bis = new BufferedInputStream(is, chunkSize);
                int remaining = chunkSize;
                int offset = 0;
                int res;
                while ((res = bis.read(ret, offset, remaining)) != -1) {
                    remaining -= res;
                    offset += res;
                    if (remaining == 0) {
                        raf.write(ret, 0, offset);
                        if (enableChecksum && file.getFileChecksum() != null) {
                            file.getFileChecksum().put(ret, chunkSize, (int) (writeOffset/chunkSize));
                        }
                        fi.updateDownloadCheckPoint((int) (writeOffset/chunkSize), writeOffset+chunkSize);
                        offset = 0;
                        writeOffset += chunkSize;
                        ret = new byte[chunkSize];
                        remaining = chunkSize;
                    }
                }
                
                if (offset > 0) {
                    // remaining chunk
                	raf.write(ret, 0, offset);
                	if (enableChecksum && file.getFileChecksum() != null) {
                        file.getFileChecksum().put(ret, offset, (int) (writeOffset/chunkSize));
                	}
                    fi.updateDownloadCheckPoint((int) (writeOffset/chunkSize), writeOffset+offset);
               }
                
                // release the open resources
                bis.close();
                file.getResponse().release();
				
                // verify checksum if download file completed
				if (fi.update(file.getLength()) == 0) {
					thread.setEOF(file.getName());
					synchronized (ConcurrentJakartaClient.this) {
						if (totalFiles == 0) {
							terminateThreads();
						}
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.out.println("IOException in download: " + e.getMessage());
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
    /**
     * Attached a reason of HTTP status error
     * @param code
     *            the status code
     * @return a message containing the code error followed by the reason
     */
	public static String getStatusMessage(ClientURLResponse response) {
		int code = response.getStatus();
		StringBuffer buffer = (new StringBuffer()).append(code).append(" (");
		switch (code) {
		case 400:
			buffer.append("Bad Request");
			break;
			
		case 401:
			buffer.append("Unauthorized");
			break;
			
		case 403:
			buffer.append("Forbidden");
			break;
			
		case 404:
			buffer.append("Not Found");
			break;
			
		case 405:
			buffer.append("Method Not Allowed");
			break;
			
		case 406:
			buffer.append("Not Acceptable");
			break;
			
		case 407:
			buffer.append("Proxy Authentication Required");
			break;
			
		case 408:
			buffer.append("Request Timeout");
			break;
			
		case 409:
			buffer.append("Conflict");
			break;
			
		case 410:
			buffer.append("Gone");
			break;
			
		case 411:
			buffer.append("Length Required");
			break;
			
		case 412:
			buffer.append("Precondition Failed");
			break;
			
		case 413:
			buffer.append("Request Entity Too Large");
			break;
			
		case 414:
			buffer.append("Request-URI Too Long");
			break;
			
		case 415:
			buffer.append("Unsupported Media Type");
			break;
			
		case 416:
			buffer.append("Requested Range Not Satisfiable");
			break;
			
		case 417:
			buffer.append("Expectation Failed");
			break;
			
		case 500:
			buffer.append("Internal Server Error");
			break;
			
		case 501:
			buffer.append("Not Implemented");
			break;
			
		case 502:
			buffer.append("Bad Gateway");
			break;
			
		case 503:
			buffer.append("Service Unavailable");
			break;
			
		case 504:
			buffer.append("Gateway Timeout");
			break;
			
		case 505:
			buffer.append("HTTP Version Not Supported");
			break;
			
		}
		
		buffer.append(")");
		String err = response.getErrorMessage();
		if (err != null && err.trim().length() > 0) {
			buffer.append(": " + err);
		}
		
		return buffer.toString();
	}
	
	/**
	 * Class to represent the incrementally checksum computation of a file
	 * 
	 */
	private class FileChecksum {
		// object to compute incrementally the checksum of a file
		private MessageDigest messageDigest;
		
		// chunks ready to be processed
		private HashMap <Integer, byte[]> slots = new HashMap <Integer, byte[]>();
		
		// the size of the chunks ready to be processed
		private HashMap <Integer, Integer> bytesMap = new HashMap <Integer, Integer>();
		
		// maximum number of chunks waiting to be processed
		int maxChunks;
		
		// the total file length
		long totalFileLength;
		
		// the file length that was already processed (cksum computed)
		long fileLength;
		
		// the chunk expected to be processed
		int expectedChunk;
		
		// the file name
		String name;
		
		FileChecksum(String name, long fileLength, int chunks) {
			maxChunks = chunks;
			this.totalFileLength = fileLength;
			this.name = name;
			// initialize the message digest object
			try {
				messageDigest = MessageDigest.getInstance("SHA-256");
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	    /**
	     * Decrement the expected slot
	     */
		void decSlots() {
			expectedChunk--;
		}
	    /**
	     * Process available chunks
	     */
		private void processSlots() {
			while (slots.get(expectedChunk) != null) {
				byte[] chunk = slots.remove(expectedChunk);
				int length = bytesMap.remove(expectedChunk);
				messageDigest.update(chunk, 0, length);
				fileLength += length;
				notifyChunkTransfered(length);
				expectedChunk++;
			}
		}
	    /**
	     * Provide a new chunk for checksum computation
	     * @param chunk
	     *            the chunk byte array
	     * @param len
	     *            the chunk size
	     * @param slot
	     *            the chunk slot
	     */
		synchronized void put(byte[] chunk, int len, int slot) {
			boolean ready = false;
			while (!ready) {
				if (slot == expectedChunk) {
					// process the expected chunk
					messageDigest.update(chunk, 0, len);
					notifyChunkTransfered(len);
					fileLength += len;
					expectedChunk++;
					// process any available expected chunk
					processSlots();
					ready = true;
				} else if (slots.size() < maxChunks) {
					// put the chunk and continue to process anothe request
					slots.put(slot, chunk);
					bytesMap.put(slot, len);
					ready = true;
				} else {
					try {
						// the chunk is not yet expected and can not be placed in the HashMap
						// wait until the expected chunk will be processed
						wait();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			// notify a chunk process occurred
			notifyAll();
		}
		
	    /**
	     * Get the checksum of the file
	     * @return the file checksum
	     */
		String getDigest() {
			// complete the digest
			boolean ready = false;
			while (!ready) {
				synchronized(this) {
					if (fileLength != totalFileLength) {
						// process any available expected chunk
						processSlots();
					}
					if (fileLength != totalFileLength) {
						// activate waiting threads
						notifyAll();
					} else {
						ready = true;
					}
				}
				if (!ready) {
					synchronized(this) {
						if (fileLength != totalFileLength) {
							try {
								// wait for activated threads to complete
								wait();
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						} else {
							ready = true;
						}
					}
				}
			}
			
			byte[] value = messageDigest.digest();
			// convert to a hexa string
			String res = DatasetUtils.hexChecksum(value);
			return res;
		}
	}
	
	/**
	 * Class to represent the elements in the HTTP requests and post process queues
	 * 
	 */
	private class FileChunk {
		// the file name
		private String name;
		
		// the chunk length
		private long length;
		
		// the offset of the block
		private long offset;
		
		// the file length
		private long totalLength;
		
		// the download directory
		private String downloadDir;
		
		// the checksum of the file
		private String checksum;
		
		// the HTTP response
		private ClientURLResponse response;
		
		// the associated object to compute incrementally the file checksum
		private FileChecksum fileChecksum;
		
		// flag to mark the last chunk
		private boolean lastChunk;
		
		// flag to mark the last chunk
		private boolean firstChunk;
		
		// the file version
		private int version;
		
		FileChunk(String fileName, long first, long len, long total) {
			setName(fileName);
			setLength(len);
			setOffset(first);
			setTotalLength(total);
		}

		FileChunk(String fileName, long first, long len, long total, String outputDir) {
			this(fileName, first, len, total);
			this.setDownloadDir(outputDir);
		}

		/**
		 * Setter and Getter Methods
		 * 
		 */
		public void setName(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setLength(long length) {
			this.length = length;
		}

		public long getLength() {
			return length;
		}

		public void setOffset(long offset) {
			this.offset = offset;
		}

		public long getOffset() {
			return offset;
		}

		public void setTotalLength(long totalLength) {
			this.totalLength = totalLength;
		}

		public long getTotalLength() {
			return totalLength;
		}

		public void setDownloadDir(String downloadDir) {
			this.downloadDir = downloadDir;
		}

		public String getDownloadDir() {
			return downloadDir;
		}

		public void setChecksum(String checksum) {
			this.checksum = checksum;
		}

		public String getChecksum() {
			return checksum;
		}
		
		public void setResponse(ClientURLResponse response) {
			this.response = response;
		}

		public ClientURLResponse getResponse() {
			return response;
		}
		
		public int getVersion() {
			return version;
		}

		public void setVersion(int version) {
			this.version = version;
		}

		// String representation
		public String toString() {
			return name + " (Offset: "+offset+", Chunk Size: "+length+", File Size: "+totalLength+")";
		}

		public void setLastChunk(boolean lastChunk) {
			this.lastChunk = lastChunk;
		}

		public boolean isLastChunk() {
			return lastChunk;
		}

		public boolean isFirstChunk() {
			return firstChunk;
		}

		public void setFirstChunk(boolean firstChunk) {
			this.firstChunk = firstChunk;
		}

		public FileChecksum getFileChecksum() {
			return fileChecksum;
		}

		public void setFileChecksum(FileChecksum fileChecksum) {
			this.fileChecksum = fileChecksum;
		}
	}
	
	/**
	 * Class to represent the file transfer progress
	 * 
	 */
	private class FileItem {
		// the number of bytes to be tranfered
		private long bytes = 0;
		
		// the file length to be transferred
		private long length;
		
		// the file name
		private String name;
		
		// the check point for slot
		private Hashtable<Integer, Long> slots = new Hashtable<Integer, Long>();
		
		// true while a check point update is performed
		boolean busy;
		
		// the las check point set
		int lastCheckPoint;
		
		FileItem(String name, long len) {
			length = len;
			this.name = name;
		}
		
		public void setLastCheckPoint(int lastCheckPoint) {
			this.lastCheckPoint = lastCheckPoint;
		}

		public void resetBusy(int slot, long slotUpperBound, long slotOffset) {
			if (slotUpperBound != slotOffset) {
				slots.put(slot, slotUpperBound);
			}
			synchronized (this) {
				busy = false;
			}
		}
		
		void updateCheckPoint(int slot, long offset) {
			slots.put(slot, offset);
		}
		
		long nextCheckPoint(int slot, long offset) {
			synchronized (this) {
				if (busy) {
					return -1;
				}
				long ret = -1;
				// check for available compact written slots
				while (lastCheckPoint < slot) {
					Long checkpoint = slots.remove(lastCheckPoint);
					if (checkpoint == null) {
						break;
					}
					lastCheckPoint++;
					ret = checkpoint;
				}
				if (lastCheckPoint == slot) {
					// current slot is the latest compact one
					ret = offset;
					lastCheckPoint++;
				}
				if (ret != -1) {
					// a check point will be set
					busy = true;
				}
				return ret;
			}
		}
		
		void updateDownloadCheckPoint(int slot, long offset) {
			slots.put(slot, offset);
			synchronized (this) {
				long ret = -1;
				while (lastCheckPoint <= slot) {
					Long checkpoint = slots.remove(lastCheckPoint);
					if (checkpoint == null) {
						break;
					}
					lastCheckPoint++;
					ret = checkpoint;
				}
				if (ret != -1) {
					downloadCheckPoint.put(name, ret);
				}
			}
		}
		
		/**
		 * Mark the file transfer progress: file transfer completed or chunk transfer completed
		 * 
		 * @return the remaining bytes to be transfered
		 */
		synchronized long update(long size) {
			bytes += size;
			if (bytes == length) {
				notifyFileTransfered(name, size);
				return 0;
			} else {
				notifyChunkTransfered(size);
				return length - bytes;
			}
		}
	}
	
	/**
	 * This thread passes the elements from to the Transmission Queue to the Worker Queue
	 * 
	 */
	private class DispatcherThread extends Thread {
		
	    /**
	     * Thread execution
	     * 
	     */
		public void run() {
			boolean ready = false;
			while (!ready) {
				FileChunk file = null;
				try {
					file = TransmissionQueue.take();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if (file == null || file.getName().length() == 0) {
					break;
				}
				workerWrapper.put(file);
			}
		}
	}
	
	/**
	 * Class to handle HTTP responses
	 * Gets an element from the post processing Queue and processes it
	 * 
	 */
	private class WorkerThread extends Thread {
		
		// files handles map
		private HashMap<String, RandomAccessFile> filesHandle = new HashMap<String, RandomAccessFile>();
		
		// files handles map
		private HashMap<String, FileChecksum> filesChecksum = new HashMap<String, FileChecksum>();
		
		// files checksum map
		private HashMap<String, String> checksum = new HashMap<String, String>();
				
		
	    /**
	     * Thread execution
	     * 
	     */
		public void run() {
			boolean ready = false;
			while (!ready) {
				FileChunk file = workerWrapper.get();
				if (file == null || file.getName().length() == 0) {
					break;
				}
				if (file.getDownloadDir() == null) {
					sendUpload(file);
				} else {
					sendDownload(file, this);
				}
			}
			if (isDownload) {
				workerWrapper.deregisterThread();
			}
		}
		
	    /**
	     * Add the properties of a new downloaded file
	     * @param name
	     *            the file name
	     * @param checkSum
	     *            the checksum of the file
	     * @param handle
	     *            the handle of the file
	     */
		private void addFile(String name, RandomAccessFile handle, String checkSum, FileChecksum fileChecksum) {
			filesHandle.put(name, handle);
			if (checkSum != null) {
				checksum.put(name, checkSum);
				filesChecksum.put(name, fileChecksum);
			}
		}
		
	    /**
	     * Mark the termination of the download of file
	     * @param name
	     *            the file name
	     */
		private void setEOF(String name) {
			String cksum = null;
			try {
				filesHandle.get(name).close();
				filesHandle.remove(name);
				cksum = checksum.remove(name);
				if (cksum != null && enableChecksum) {
					FileChecksum fileChecksum = filesChecksum.remove(name);
					String fileCksum = fileChecksum.getDigest();
			        if (fileCksum == null || !fileCksum.equals(cksum)) {
			        	System.out.println("Failure in downloading the file \"" + name +
			        			"\". Checksum failed. Checksum tag: "+cksum+". Checksum computed: "+fileCksum+".");
	        			notifyFailure("<p>Failure in downloading the file \"" + name + "\".<p>Checksum failed.");
			        }
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	    /**
	     * Get the handle of a file to be downloaded
	     * @param name
	     *            the file name
	     */
		private RandomAccessFile getFileHandle(String name) {
			return filesHandle.get(name);
		}
	}
	
	/**
	 * Class to wrapp a queue
	 * Monitors the threads processing queue 
	 * 
	 */
	private class QueueWrapper {
	    // the number of active threads 
		private int activeThreads = 0; 
		
	    // the number of waiting threads
		private int waitingThreads = 0; 
		
	    // the total number of threads
		private int maxThreads; 
		
	    // the list of threads performing HTTP requests
		private ArrayList<Thread> threads = new ArrayList<Thread>();
		
	    /**
	     * Put a FileChunk to be processed
	     * 
	     * @param fc
	     *            the FileChunk representing the file
	     */
		void put(FileChunk fc) {
			try {
				WorkerQueue.put(fc);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			// create a new HTTP request thread if all others are busy and the threads pool is not full
			synchronized (this) {
				if (waitingThreads == 0 && activeThreads < maxThreads) {
					Thread thread = new WorkerThread();
					threads.add(thread);
					activeThreads++;
					thread.start();
				}
			}
		}
		
	    /**
	     * Get the handle of a file to be downloaded
	     * @param name
	     *            the file name
	     */
		synchronized private void deregisterThread() {
			if (--activeThreads == 0 && !failure) {
				if (isDownload) {
					String filename = checkPointDir + File.separator + TagFilerProperties.getProperty("tagfiler.checkpoint.file");
					if ((new File(filename)).delete()) {
						System.out.println("Deleted the check point file \""+filename+"\"");
					}
					downloadCheckPoint = null;
				}
				synchronized (listenerLock) {
					listener.notifySuccess();
				}
			}
			 
		}
		
	    /**
	     * Get a FileChunk to be post processed
	     * 
	     * @return FileChunk to be post processed 
	     */
		FileChunk get() {
			FileChunk fc;
			try {
				synchronized (this) {
					waitingThreads++;
				}
				fc = WorkerQueue.take();
				synchronized (this) {
					waitingThreads--;
				}
				return fc;
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			} catch (IllegalMonitorStateException e) {
				if (!cancel) {
					e.printStackTrace();
				}
				return null;
			}
		}
		
	    /**
	     * Terminate the threads from the pools
	     * Put in the queue elements to mark threads termination
	     */
		private void terminateThreads() {
			if (!browser) {
				System.out.print(", Total threads: " + threads.size());
			}
			for (int i=0; i<threads.size(); i++) {
				try {
					WorkerQueue.put(new FileChunk("", 0, 0, 0));
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}
	}

}
