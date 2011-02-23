package edu.isi.misd.tagfiler.upload;

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
 * Interface for a file upload process to the tag server.
 * 
 * @author David Smith
 * 
 */
public interface FileUpload extends FileTransfer {

    /**
     * Sets the base directory for the file upload.
     * 
     * @param baseDir
     *            base directory
     */
    public void setBaseDirectory(String baseDir);

    /**
     * Posts a list of files to the tagfiler server with a random dataset name
     * and using the best available MessageDigest available.
     * 
     * @param files
     *            list of file names to upload
     * @return true if all files were uploaded successfully
     */
    public boolean postFileData(List<String> files);

    /**
     * Sets the files to be uploaded on the Web Page.
     * 
     * @param filesList
     *            the list of files
     */
    public void addFilesToList(List<String> filesList);

    /**
     * Sets the dataset name.
     * 
     * @param name
     *            the dataset name
     */
    public void setDatasetName(String name);

}
