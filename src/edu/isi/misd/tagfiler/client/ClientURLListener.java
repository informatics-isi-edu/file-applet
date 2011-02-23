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

/**
 * @author Serban Voinea
 *
 */
public interface ClientURLListener {
	
    /**
     * Callback to notify success for the entire upload/download process
     * 
     */
	public void notifySuccess();
	
    /**
     * Callback to notify a failure during the upload/download process
     * 
     * @param err
     *            the error message
     */
	public void notifyFailure(String err);
	
    /**
     * Callback to notify a chunk block transfer completion during the upload/download process
     * 
     * @param size
     *            the chunk size
     */
	public void notifyChunkTransfered(long size);
	
    /**
     * Callback to notify a file transfer completion during the upload/download process
     * 
     * @param size
     *            the chunk size
     */
	public void notifyFileTransfered(long size);
	
    /**
     * Callback to notify an error during the upload/download process
     * 
     * @param err
     *            the error message
     * @param e
     *            the exception
     */
	public void notifyError(String err, Exception e);
	
    /**
     * Callback to get the cookie.
     * 
     * @return the cookie or null if cookies are not used
     */
	public String getCookie();
	
    /**
     * Callback to update the session cookie. Should have an empty body if cookies are not used.
    */
	public void updateSessionCookie();
	
    /**
     * Get the dataset name
     * @return the dataset name
     */
	public String getDataset();

    /**
     * @return true if the checksum is enabled
     */
    public boolean isEnableChecksum();

    /**
     * @return the dataset id
     */
    public String getDatasetId();

}
