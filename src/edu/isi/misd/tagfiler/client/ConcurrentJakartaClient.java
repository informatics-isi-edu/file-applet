/**
 * 
 */
package edu.isi.misd.tagfiler.client;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import edu.isi.misd.tagfiler.exception.FatalException;
import edu.isi.misd.tagfiler.util.LocalFileChecksum;


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
	
    // the total number of files to be uploaded or downloaded
	private int totalFiles;
	
    // the chunk size of a block to be transfered
	private int chunkSize;
	
    // if true, the file will be transferred in chunks
	private boolean allowChunks;
	
    // object used for threads synchronization on listener actions
	private Object listenerLock = new Object();
	
    // flag to cancel the requests
	private boolean cancel;
	
    // true if a failure occurred
	private boolean failure;
	
    // flag to verify the checksum of a file transfer
	private boolean verifyTransfer;
	
    // wrapper for WorkerQueue
	private QueueWrapper workerWrapper = new QueueWrapper();
	
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
		assert(connections >= 2);
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
        assert (dir != null);
		upload(getFiles(new File(dir)));
	}
	
    /**
     * Upload a list of files
     * 
     * @param files
     *            the files to be uploaded
     */
	public void upload(List<String> files) {
		totalFiles = files.size();
		Thread thread = new DispatcherThread();
		thread.start();
		for (String file : files) {
			uploadFile(file);
		}
	}
	
    /**
     * Upload a list of files
     * 
     * @param files
     *            the files to be uploaded
     * @param baseDirectory
     *            the base directory to be used for the uploaded files
     */
	public void upload(List<String> files, String baseDirectory) {
		// TODO Auto-generated method stub
		this.baseDirectory = baseDirectory;
		upload(files);
	}
	
    /**
     * Upload a file
     * 
     * @param file
     *            the file to be uploaded
     */
	public void upload(String filename) {
		totalFiles = 1;
		Thread thread = new DispatcherThread();
		thread.start();
		uploadFile(filename);
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
		download(file, outputDir, null, null);
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
     */
	public void download(String file, String outputDir, Map<String, String> checksumMap, Map<String, Long> bytesMap) {
        assert (file != null && outputDir != null);
		totalFiles = 1;
		verifyTransfer = true;
        downloadFile(file, outputDir, checksumMap, bytesMap);
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
		download(files, outputDir, null, null);
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
     */
	public void download(List<String> files, String outputDir, Map<String, String> checksumMap, Map<String, Long> bytesMap) {
        assert (files != null && outputDir != null);
		totalFiles = files.size();
		verifyTransfer = true;
		for (String file : files) {
			downloadFile(file, outputDir, checksumMap, bytesMap);
		}
	}
	
    /**
     * Upload a file
     * 
     * @param file
     *            the file to be uploaded
     */
	private void uploadFile(String filename) {
		File file = new File(filename);
		long length = file.length();
		
		// mark file to be uploaded
		filesCompletion.put(filename, new FileItem(filename, length));
		
		if (!allowChunks || length <= chunkSize) {
			// a single chunk - put it directly into the Completion Queue
			FileChunk fc = new FileChunk(filename, 0, length, length);
			fc.setLastChunk(true);
			workerWrapper.put(fc);
		} else {
			// the files will be sent in chunks
			// put the first chunk into Worker Queue
			workerWrapper.put(new FileChunk(filename, 0, chunkSize, length));
		}
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
     */
	private void downloadFile(String file, String outputDir, Map<String, String> checksumMap, Map<String, Long> bytesMap) {
        assert (file != null && outputDir != null);
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
			if (browser) {
				System.out.println("Sending HEAD request: url: "+url+", File: '"+file+"'");
			}
			response = getFileLength(url.toString(), cookie);
			if (200 == response.getStatus()) {
				totalLength = response.getResponseSize();
			} else {
				notifyFailure("Error " + ConcurrentJakartaClient.getStatusMessage(response) +" in getting the length of the file '" + file + "'");
			}
		}
		
		// mark file to be downloaded
		filesCompletion.put(file, new FileItem(file, totalLength));
		
		// put all the chunks into the HTTP request queue
		long position = 0;
		while (position < totalLength) {
			long size = allowChunks ? chunkSize : totalLength;
			if (position+size > totalLength) {
				size = totalLength - position;
			}
			FileChunk fc = new FileChunk(file, position, size, totalLength, outputDir);
			if (checksumMap != null) {
				fc.setChecksum(checksumMap.get(file));
			}
			workerWrapper.put(fc);
			position += size;
		}
	}
	
    /**
     * Get recursively the files of a directory
     * 
     * @param dir
     *            the directory
     * @return the list with the files names
     */
    private List<String> getFiles(File dir) {
        assert (dir != null);
        List<String> files = new ArrayList<String>();
        File[] children = dir.listFiles(excludeDirFilter);
        
        for (int i = 0; i < children.length; i++) {
            if (children[i].isDirectory()) {
            	files.addAll(getFiles(children[i]));
            } else if (children[i].isFile()) {
            	files.add(children[i].getAbsolutePath());
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
		terminateThreads();
		
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
		failure = true;
		terminateThreads();
		
		synchronized (listenerLock) {
			listener.notifyFailure(err);
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
			if (--totalFiles == 0 && !verifyTransfer) {
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
				url.append(URLEncoder.encode(getBaseName(file.getName()), "UTF-8"));
			} catch (UnsupportedEncodingException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			
			FileItem fi = filesCompletion.get(file.getName());

			// if this is the last chunk, Transmission Number and Checksum parameters will be added
			String params = null;
			if (file.getLength() == file.getTotalLength() || file.isLastChunk()) {
				params = listener.getURLParameters(file.getName());
				if (params != null) {
					url.append(params);
				}
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
				// Read the chunk to be uploaded
				FileInputStream fis = new FileInputStream(file.getName());
				try {
					fis.skip(file.getOffset());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				response = postFile(url.toString(), fis, file.getLength(), file.getOffset(), file.getTotalLength(), cookie);
				try {
					fis.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			
			if (response == null) {
				notifyFailure("Error: NULL response in uploading file " + file);
				return;
			}
			
			// Check result
			status = response.getStatus();
			updateSessionCookie();
			if (201 == status || 204 == status) {
				long size = fi.update(file.getLength());
				if (size > 0) {
					long position = file.getTotalLength() - size;
					if (size <= chunkSize) {
						// put the last chunk into the Transmission queue
						FileChunk fc = new FileChunk(file.getName(), position, size, file.getTotalLength());
						fc.setLastChunk(true);
						try {
							TransmissionQueue.put(fc);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} else if (file.getOffset() == 0) {
						// first chunk was completed
						// put the rest of chunks but the last into the Transmission Queue
						position = chunkSize;
						long filesize = file.getTotalLength();
						while (position + chunkSize < filesize) {
							try {
								TransmissionQueue.put(new FileChunk(file.getName(), position, chunkSize, filesize));
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							position += chunkSize;
						}
					}
				}
			} else {
				notifyFailure("Error " + ConcurrentJakartaClient.getStatusMessage(response) +" in uploading file " + file);
			}
			response.release();
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
			notifyFailure("Error: NULL response in downloading file " + file);
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
			notifyFailure("Error " + ConcurrentJakartaClient.getStatusMessage(response) +" in downloading file " + file);
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
                	notifyFailure("Can not make directory \"" + dir + "\".");
                	return;
                }
            }

		    // write the chunk into the local file 
            RandomAccessFile raf = thread.getFileHandle(file.getName());
            if (raf == null) {
    			raf = new RandomAccessFile(file.getDownloadDir() + File.separatorChar + localFile,"rw");
    			thread.addFile(file.getName(), raf, file.getChecksum(), file.getDownloadDir());
            }
			try {
				raf.seek(file.getOffset());
				
				// read the response content and write it into the local file
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
                        offset = 0;
                        remaining = chunkSize;
                    }
                }
                
                if (offset > 0) {
                    // remaining chunk
                	raf.write(ret, 0, offset);
               }
                
                // release the open resources
                bis.close();
				file.getResponse().release();
				FileItem fi = filesCompletion.get(file.getName());
				
                // verify checksum if download file completed
				if (fi.update(file.getLength()) == 0) {
					thread.checkEOF(file.getName());
					synchronized (ConcurrentJakartaClient.this) {
						if (totalFiles == 0) {
							terminateThreads();
						}
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
    /**
     * Verify the checksum of the downloaded file
     * @param name
     *            the file name
     * @param outputDir
     *            the output directory
     * @param fileChecksum
     *            the checksum of the file
     */
	private void verifyCheckSum(String name, String outputDir, String fileChecksum) {
	    String localFile = name.replace('/', File.separatorChar);
        File downloadFile = new File(outputDir + File.separatorChar + localFile);
        String checksum = null;
		try {
			checksum = LocalFileChecksum
			        .computeFileChecksum(downloadFile);
		} catch (FatalException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        if (checksum == null || !checksum.equals(fileChecksum)) {
        	notifyFailure("Checksum failed for downloading the file: " + name);
        }
	}
	
    /**
     * Get the file name relative to the base directory
     * @param fileName
     *            name of the file relative to the base directory
     * @return the name of the file 
     */
	private String getBaseName(String filename) {
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
		
		String err = response.getErrorMessage();
		if (err != null && err.trim().length() > 0) {
			buffer.append(" - " + err);
		}
		
		buffer.append(")");
		
		return buffer.toString();
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
		
		private boolean lastChunk;
		
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
		
		// String representation
		public String toString() {
			return name+"(Offset: "+offset+", Chunk Size: "+length+", File Size: "+totalLength+")";
		}

		public void setLastChunk(boolean lastChunk) {
			this.lastChunk = lastChunk;
		}

		public boolean isLastChunk() {
			return lastChunk;
		}
	}
	
	/**
	 * Class to represent the file transfer progress
	 * 
	 */
	private class FileItem {
		// the number of bytes to be tranfered
		private long bytes = 0;
		
		// the file length
		private long length;
		
		// the file name
		private String name;
		
		FileItem(String name, long len) {
			length = len;
			this.name = name;
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
		
		// files checksum map
		private HashMap<String, String> checksum = new HashMap<String, String>();
		
		// files dowunload directory map
		private HashMap<String, String> downloadDir = new HashMap<String, String>();
		
		
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
				if (verifyTransfer) {
					checkEOF();
				}
			}
			if (verifyTransfer) {
				checkEOF();
				workerWrapper.deregisterThread();
			}
		}
		
	    /**
	     * Add the properties of a new downloaded file
	     * @param name
	     *            the file name
	     * @param outputDir
	     *            the output directory
	     * @param checkSum
	     *            the checksum of the file
	     * @param handle
	     *            the handle of the file
	     */
		private void addFile(String name, RandomAccessFile handle, String checkSum, String outputDir) {
			filesHandle.put(name, handle);
			if (checkSum != null) {
				checksum.put(name, checkSum);
			}
			downloadDir.put(name, outputDir);
			workerWrapper.addActiveFile(name);
		}
		
	    /**
	     * Mark the termination of the download of file
	     * @param name
	     *            the file name
	     */
		private void setEOF(String name) {
			String dir = null;
			String cksum = null;
			try {
				filesHandle.get(name).close();
				filesHandle.remove(name);
				cksum = checksum.remove(name);
				dir = downloadDir.remove(name);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			boolean isEOF = workerWrapper.setEOF(name);
			if (isEOF && cksum != null) {
				// checksum is needed
				verifyCheckSum(name, dir, cksum);
			}
		}
		
	    /**
	     * Check the termination of the download process
	     */
		private void checkEOF() {
			if (filesHandle.size() == 0) {
				return;
			}
			synchronized (workerWrapper) {
				Set<String> files = workerWrapper.getEOF();
				Set<String> openFiles = filesHandle.keySet();
				files.retainAll(openFiles);
				for (String file : files) {
					setEOF(file);
				}
			}
		}
		
	    /**
	     * Check the termination of downloading a file
	     * @param file
	     *            the file name
	     */
		private void checkEOF(String file) {
			synchronized (workerWrapper) {
				setEOF(file);
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
		
		// map for the active files
		private HashMap<String, Integer> activeFiles = new HashMap<String, Integer>();
		
	    /**
	     * Mark a new thread that handles the download of a file
	     * @param name
	     *            the file name
	     */
		synchronized private void addActiveFile(String name) {
			Integer value = activeFiles.get(name);
			if (value != null) {
				if (value != -1) {
					activeFiles.put(name, value+1);
				} else {
					activeFiles.remove(name);
				}
			} else {
				activeFiles.put(name, 1);
			}
		}
		
	    /**
	     * Mark the termination of downloading a file
	     * @param name
	     *            the file name
	     */
		private boolean setEOF(String name) {
			Integer value = activeFiles.get(name);
			if (value > 1) {
				activeFiles.put(name, -value+1);
				return false;
			} else if (value == 1 || value == -1) {
				activeFiles.remove(name);
				return true;
			} else {
				activeFiles.put(name, value+1);
				return false;
			}
		}
		
	    /**
	     * Get the files that terminate the download process
	     * @param name
	     *            the file name
	     */
		private Set<String> getEOF() {
			HashSet<String> ret = new HashSet<String>();
			for (String name : activeFiles.keySet()) {
				if (activeFiles.get(name) < 0) {
					ret.add(name);
				}
			}
			
			return ret;
		}
		
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
