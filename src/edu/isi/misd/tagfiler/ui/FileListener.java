package edu.isi.misd.tagfiler.ui;

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

/**
 * Listener interface for a class that wants to be notified of events from the
 * {@link edu.isi.misd.tagfiler.upload.FileUpload} class.
 * 
 * @author David Smith
 * 
 */
public interface FileListener {

    /**
     * Called when a dataset is transferred successfully.
     * 
     * @param datasetName
     *            name of the dataset
     * @param version
     *            the dataset version
     */
    public void notifySuccess(String datasetName, int version);

    /**
     * Called when a dataset fails to transfer completely.
     * 
     * @param datasetName
     *            name of the dataset
     * @param message
     *            the failure message
     */
    public void notifyFailure(String datasetName, int code, String error);

    /**
     * Called when the FileUpload logs a message that could be displayed in a
     * log or UI
     * 
     * @param message
     *            the message to display.
     *            the response status.
     */
    public void notifyLogMessage(String message);

    /**
     * Called before a file transfer operation begins.
     * 
     * @param filename
     *            name of the file that will be transferred.
     */
    public void notifyFileTransferStart(String filename);

    /**
     * Called after a file transfer operation completes successfully.
     * 
     * @param filename
     *            name of the file that was transferred
     * @param totalBytes
     *            size of the file that was transferred.
     */
    public void notifyFileTransferComplete(String filename, long totalBytes);

    /**
     * Called when a chunk file transfer completes
     * @param file
     *            if true, a file transfer completed
     * @param size
     *            size of the chunk that was transferred.
     */
    public void notifyChunkTransfered(boolean file, long size);
    
    /**
     * Called before a dataset transfer starts.
     * 
     * @param datasetName
     *            name of the dataset being transferred.
     * @param totalBytes
     *            total amount of bytes to transfer for the dataset.
     */
    public void notifyStart(String datasetName, long totalBytes);

    /**
     * Called when a file in the dataset is skipped, in case the listener is
     * counting files.
     * 
     * @param filename
     *            name of the file that was skipped.
     */
    public void notifyFileTransferSkip(String filename);

    /**
     * Called when a fatal error occurs that should be reported to the UI
     */
    public void notifyError(Throwable t);
    
    /**
     * Called when a fatal error occurred
     */
    public void notifyFatal(Throwable e);
    
    
    /**
     * Get the number of uploaded/downloaded files
     */
    public int getFilesCompleted();
}
