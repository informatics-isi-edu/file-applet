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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import edu.isi.misd.tagfiler.exception.FatalException;
import edu.isi.misd.tagfiler.util.DatasetUtils;
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
	
    // flag to mark a download process
	private boolean isDownload;
	
    // flag to mark a checksum computation
	private boolean enableChecksum;
	
    // wrapper for WorkerQueue
	private QueueWrapper workerWrapper = new QueueWrapper();
	
    // map containing the checksums of all files to be transferred.
	private Map<String, String> checksumMap;

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
	public void upload(List<String> files) {
		enableChecksum = listener.isEnableChecksum();
		totalFiles = files.size();
		for (String file : files) {
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
     */
	public void upload(List<String> files, String baseDirectory, Map<String, String> checksumMap) {
		// TODO Auto-generated method stub
		this.baseDirectory = baseDirectory;
		this.checksumMap = checksumMap;
		upload(files);
	}
	
    /**
     * Upload a file
     * 
     * @param file
     *            the file to be uploaded
     */
	public void upload(String filename) {
		enableChecksum = listener.isEnableChecksum();
		totalFiles = 1;
		uploadFile(filename);
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
        if (file == null || outputDir == null) throw new IllegalArgumentException(file+", "+outputDir);
		enableChecksum = listener.isEnableChecksum();
		totalFiles = 1;
		isDownload = true;
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
        if (files == null || outputDir == null) throw new IllegalArgumentException(""+files+", "+outputDir);
		enableChecksum = listener.isEnableChecksum();
		totalFiles = files.size();
		isDownload = true;
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
		FileChunk fc = null;
		
		if (!allowChunks || length <= chunkSize) {
			// a single chunk - put it directly into the Completion Queue
			fc = new FileChunk(filename, 0, length, length);
			fc.setLastChunk(true);
		} else {
			// the files will be sent in chunks
			// put the first chunk into Worker Queue
			fc = new FileChunk(filename, 0, chunkSize, length);
		}
		if (enableChecksum) {
			fc.setFileChecksum(new FileChecksum(filename, length, connections-1));
		}
		workerWrapper.put(fc);
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
        if (file == null || outputDir == null) throw new IllegalArgumentException(file+", "+outputDir);
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
		FileChecksum fileChecksum = null;
		if (checksumMap != null && checksumMap.get(file) != null && enableChecksum) {
			fileChecksum = new FileChecksum(file, totalLength, connections-1);
		}
		while (position < totalLength) {
			long size = allowChunks ? chunkSize : totalLength;
			if (position+size > totalLength) {
				size = totalLength - position;
			}
			FileChunk fc = new FileChunk(file, position, size, totalLength, outputDir);
			if (checksumMap != null && checksumMap.get(file) != null && enableChecksum) {
				fc.setFileChecksum(fileChecksum);
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
        if (dir == null) throw new IllegalArgumentException(""+dir);
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
			} catch (UnsupportedEncodingException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			
			FileItem fi = filesCompletion.get(file.getName());

			// if this is the last chunk, Dataset Name and Checksum parameters will be added
			String params = null;
			String cksum = null;
			byte ret[] = null;
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
			if (file.getLength() == file.getTotalLength() || file.isLastChunk()) {
				try {
					if (enableChecksum) {
						checksumMap.put(DatasetUtils.getBaseName(file.getName(), baseDirectory), cksum);
					}
					params = DatasetUtils.getUploadQuerySuffix(listener.getDataset(), cksum);
				} catch (FatalException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
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
				notifyFailure("Error: NULL response in uploading file " + file);
				return;
			}
			
			// Check result
			status = response.getStatus();
			updateSessionCookie();
			FileChunk fc = null;
			if (201 == status || 204 == status) {
				long size = fi.update(file.getLength());
				if (size > 0) {
					long position = file.getTotalLength() - size;
					if (size <= chunkSize) {
						// put the last chunk into the Transmission queue
						fc = new FileChunk(file.getName(), position, size, file.getTotalLength());
						if (enableChecksum) {
							fc.setFileChecksum(file.getFileChecksum());
						}
						fc.setLastChunk(true);
						synchronized (this) {
							try {
								TransmissionQueue.put(fc);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					} else if (file.getOffset() == 0) {
						// first chunk was completed
						// put the rest of chunks but the last into the Transmission Queue
						position = chunkSize;
						long filesize = file.getTotalLength();
						synchronized (this) {
							while (position + chunkSize < filesize) {
								try {
									fc = new FileChunk(file.getName(), position, chunkSize, filesize);
									if (enableChecksum) {
										fc.setFileChecksum(file.getFileChecksum());
									}
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
    			thread.addFile(file.getName(), raf, file.getChecksum(), file.getDownloadDir(), file.getFileChecksum());
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
               }
                
                // release the open resources
                bis.close();
				file.getResponse().release();
				FileItem fi = filesCompletion.get(file.getName());
				
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
		
		String err = response.getErrorMessage();
		if (err != null && err.trim().length() > 0) {
			buffer.append(" - " + err);
		}
		
		buffer.append(")");
		
		return buffer.toString();
	}
	
	public void setChecksumMap(Map<String, String> checksumMap) {
		this.checksumMap = checksumMap;
	}

	public Map<String, String> getChecksumMap() {
		return checksumMap;
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
		
		// the chunk expected to be processed
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
		
		// files handles map
		private HashMap<String, FileChecksum> filesChecksum = new HashMap<String, FileChecksum>();
		
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
			}
			if (isDownload) {
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
		private void addFile(String name, RandomAccessFile handle, String checkSum, String outputDir, FileChecksum fileChecksum) {
			filesHandle.put(name, handle);
			if (checkSum != null) {
				checksum.put(name, checkSum);
				filesChecksum.put(name, fileChecksum);
			}
			downloadDir.put(name, outputDir);
		}
		
	    /**
	     * Mark the termination of the download of file
	     * @param name
	     *            the file name
	     */
		private void setEOF(String name) {
			//String dir = null;
			String cksum = null;
			try {
				filesHandle.get(name).close();
				filesHandle.remove(name);
				cksum = checksum.remove(name);
				downloadDir.remove(name);
				//dir = downloadDir.remove(name);
				if (cksum != null && enableChecksum) {
					FileChecksum fileChecksum = filesChecksum.remove(name);
					String fileCksum = fileChecksum.getDigest();
			        if (fileCksum == null || !fileCksum.equals(cksum)) {
			        	notifyFailure("Checksum failed for downloading the file: " + name);
			        }
					//verifyCheckSum(name, dir, cksum);
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
