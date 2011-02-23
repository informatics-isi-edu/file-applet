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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.xml.crypto.dsig.DigestMethod;

import org.apache.commons.codec.digest.DigestUtils;

import edu.isi.misd.tagfiler.exception.FatalException;

/**
 * Utility class responsible for computing the checksum on a file.
 * 
 * @author David Smith
 * 
 */
public final class LocalFileChecksum {

    private static final String digestType = TagFilerProperties
            .getProperty("tagfiler.checksum.type");

    /**
     * Computes a checksum on a file, given the proper message digest
     * implementation
     * 
     * @param file
     *            file to read
     * @param messageDigest
     *            MessageDigest to use
     * @return the checksum bytes of the file
     * @thows FatalException if the checksum cannot be constructed.
     */
    public static String computeFileChecksum(File file) throws FatalException {
        if (file == null) throw new IllegalArgumentException("file");

        String checksum = null;

        FileInputStream stream = null;
        try {
            stream = new FileInputStream(file);
            if (DigestMethod.SHA512.equals(digestType)) {
                checksum = DigestUtils.sha512Hex(stream);
            } else if (DigestMethod.SHA256.equals(digestType)) {
                checksum = DigestUtils.sha256Hex(stream);
            } else if (DigestMethod.SHA1.equals(digestType)) {
                checksum = DigestUtils.shaHex(stream);
            } else {
                checksum = DigestUtils.md5Hex(stream);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new FatalException(e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                }
            }
        }
        return checksum;
    }
}
