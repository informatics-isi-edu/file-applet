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

import edu.isi.misd.tagfiler.ui.FileListener;

/**
 * Listener interface for a class that wants to be notified of events from the
 * {@link edu.isi.misd.tagfiler.upload.FileUpload} class.
 * 
 * @author David Smith
 * 
 */
public interface FileUploadListener extends FileListener {

    /**
     * Called when a dataset fails to transfer completely.
     * 
     * @param datasetName
     *            name of the dataset
     */
    public void notifyFailure(String datasetName);

    /**
     * Called when a dataset fails to transfer completely.
     * 
     * @param datasetName
     *            name of the dataset
     * @param err
     *            the error message
     */
    public void notifyFailure(String datasetName, String err);

}
