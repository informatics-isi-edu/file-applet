package edu.isi.misd.tagfiler;

import java.io.UnsupportedEncodingException;

import edu.isi.misd.tagfiler.util.DatasetUtils;
import edu.isi.misd.tagfiler.util.TagFilerProperties;

/**
     * Abstract class that listens for progress from the file upload/download process and
     * takes action on the applet UI based on this progress.
     * 
     * @author Sertban Voinea
     * 
     */
    public abstract class TagFilerAppletListener {

        protected int filesCompleted = 0;

		protected int totalFiles = 0;

        protected long totalBytes = 0;

        protected long bytesTransferred = 0;
        
        protected long lastPercent;

        /**
         * Called when a failure occurred.
         */
        public void notifyFailure(AbstractTagFilerApplet applet, String datasetFailure, String property, String datasetName, int code, String errorMessage) {
            if (datasetName == null || datasetName.length() == 0) throw new IllegalArgumentException(datasetName);
            String message = TagFilerProperties
                    .getProperty(datasetFailure);
            if (code != -1) {
                message += " (Status Code: " + code;
                if (errorMessage != null && errorMessage.trim().length() > 0) {
                    message += " - " + errorMessage;
                }
                message +=  ")";
            }
            try {
                message = DatasetUtils.urlEncode(message);
            } catch (UnsupportedEncodingException e) {
                // just pass the unencoded message
            }
            final StringBuffer buff = new StringBuffer(applet.getTagFilerServerURL())
                    .append(TagFilerProperties.getProperty(
                    		property, new String[] {
                                    datasetName, message }));
            applet.redirect(buff.toString());

        }

        /**
         * Called to log a message.
         */
        public void notifyLogMessage(String message) {
            if (message == null) throw new IllegalArgumentException(message);
            System.out.println(message);
        }

        /**
         * Called when a file transfer starts
         */
        public void notifyFileTransferStart(AbstractTagFilerApplet applet, String property, String filename) {
            applet.updateStatus(TagFilerProperties.getProperty(
            		property,
                    new String[] { Integer.toString(filesCompleted + 1),
                            Integer.toString(totalFiles) }));
            System.out.println("Transferring " + filename + "...");
        }

        /**
         * Called when a file transfer completes
         */
        public void notifyFileTransferComplete(AbstractTagFilerApplet applet, String property, String filename, long size) {
            filesCompleted++;
            
            bytesTransferred += size + 1;
            long percent = bytesTransferred * 100 / totalBytes;
            applet.drawProgressBar(percent);

            if (filesCompleted < totalFiles) {
                applet.updateStatus(TagFilerProperties.getProperty(
                		property,
                        new String[] { Integer.toString(filesCompleted + 1),
                                Integer.toString(totalFiles) }));
            }
        }

        /**
         * Called when a chunk file transfer completes
         */
        public void notifyChunkTransfered(AbstractTagFilerApplet applet, String property, boolean file, long size) {
            bytesTransferred += size;
            if (file) {
                filesCompleted++;
                 bytesTransferred++;
            }
            long percent = bytesTransferred * 100 / totalBytes;
            if (percent > lastPercent || file) {
            	lastPercent = percent;
                applet.drawProgressBar(percent);

                if (filesCompleted < totalFiles) {
                    applet.updateStatus(TagFilerProperties.getProperty(
                    		property,
                            new String[] { Integer.toString(filesCompleted + 1),
                                    Integer.toString(totalFiles) }));
                }
            }
        }

        /**
         * Called if a file is skipped and not transferred
         */
        public void notifyFileTransferSkip(String filename) {
            filesCompleted++;
        }

        /**
         * Called when a fatal error occurred
         */
        public void notifyFatal(AbstractTagFilerApplet applet, String property, Throwable e) {
            String message = TagFilerProperties.getProperty(
            		property, new String[] { e
                            .getClass().getCanonicalName() + (e.getMessage() != null ? ". " + e.getMessage() : "") });
            try {
                message = DatasetUtils.urlEncode(message);
            } catch (UnsupportedEncodingException f) {
                // just use the unencoded message
            }

            StringBuffer buff = new StringBuffer(applet.getTagFilerServerURL())
                    .append(TagFilerProperties.getProperty(
                            "tagfiler.url.GenericFailure",
                            new String[] { message }));

            System.out.println("Fatal error: \"" + e.getMessage() + "\". Redirecting...");
            applet.redirect(buff.toString());

        }
        
        /**
         * Called to update a status message
         */
        public void notifyStatus(AbstractTagFilerApplet applet, String message) {
        	applet.updateStatus(message);
        }

        /**
         * Getter method
         */
        public int getFilesCompleted() {
			return filesCompleted;
		}

}
