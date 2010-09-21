package edu.isi.misd.tagfiler.util;

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
        assert (file != null);

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
