package edu.isi.misd.tagfiler.exception;

/**
 * Exception that should halt the operation of the file transfer process.
 * 
 * @author David Smith
 * 
 */
public class FatalException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a fatal exception around another throwable
     * 
     * @param t
     */
    public FatalException(Throwable t) {
        super(t);
    }

    /**
     * Constructs a fatal exception around another exception
     * 
     * @param t
     */
    public FatalException(Exception e) {
        super(e);
    }

    /**
     * Constructs a fatal exception with the given message
     * 
     * @param s
     *            message of the exception
     */
    public FatalException(String s) {
        super(s);
    }

}
