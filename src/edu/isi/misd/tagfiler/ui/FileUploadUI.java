package edu.isi.misd.tagfiler.ui;


/**
 * Interface for the file upload user interface that is needed to communicate
 * between the file upload components and the UI.
 * 
 * @author David Smith
 * 
 */
public interface FileUploadUI extends FileUI {

    /**
     * Allow the user to click the "Upload" button
     * 
     */
    public void enableUpload();

    /**
     * Allow the user to click the "Add" button.
     */
    public void enableAdd();

}
