package edu.isi.misd.tagfiler.util;

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
 * Utility class for keeping a file transfer status 
 * @author serban
 *
 */
public class FileWrapper {
	
	// the file name
	private String name;
	
	// the file check point offset
	private long offset;

	// the file length
	private long fileLength;

	// the file check point offset
	private int version;

	public FileWrapper(String name, long offset, int version, long fileLength) {
		this.name = name;
		this.offset = offset;
		this.fileLength = fileLength;
		this.version = version;
	}
	
	public String getName() {
		return name;
	}
	
	public long getOffset() {
		return offset;
	}
	
	public long getFileLength() {
		return fileLength;
	}

	public int getVersion() {
		return version;
	}

	public String toString() {
		return name + " (Offset: "+offset+", fileLength: "+fileLength+", version="+version+")";
	}
}
