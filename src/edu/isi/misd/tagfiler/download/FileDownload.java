package edu.isi.misd.tagfiler.download;

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

import java.util.List;

import edu.isi.misd.tagfiler.FileTransfer;

/**
 * Interface for a file download process to the tag server.
 * 
 * @author Serban Voinea
 * 
 */
public interface FileDownload extends FileTransfer {

    /**
     * Returns the total number of bytes to be downloaded.
     */
    public long getSize();

    /**
     * Returns the list of the file names to be downloaded.
     * 
     * @param dataset
     *            the dataset name of the files to retrieve
     * @param version
     *            the dataset version
     */
    public List<String> getFiles(String dataset, int version);

    /**
     * Performs the dataset download.
     * 
     * @param destinationDir
     *            directory to save the files
     * @param target
     *            resume or download all
     * @return true if all files were downloaded successfully
     */
    public boolean downloadFiles(String destinationDir, String target);

    /**
     * Checks with the tagfiler server to verify that a dataset by the control
     * number already exists
     * 
     * @param controlNumber
     *            the Dataset Name to check
     * @param version
     *            the Dataset version
     * @param status
     *            the status returned by the HTTP response 
     * @param errorMessage
     *            the error message to be displayed
     * @return true if a dataset with the particular Dataset Name exists,
     *         false otherwise
     */
    public boolean verifyValidControlNumber(String controlNumber, int version, StringBuffer status, StringBuffer errorMessage);

}
