package com.android.email.mail;

import com.android.email.Preferences;


public abstract class Folder {
		private String status = null;
		private long lastChecked = 0;
    public enum OpenMode {
        READ_WRITE, READ_ONLY,
    }
    
    public enum FolderClass {
    	NONE, FIRST_CLASS, SECOND_CLASS;
    }
    
    public enum FolderType {
        HOLDS_FOLDERS, HOLDS_MESSAGES,
    }

    /**
     * Forces an open of the MailProvider. If the provider is already open this
     * function returns without doing anything.
     *
     * @param mode READ_ONLY or READ_WRITE
     */
    public abstract void open(OpenMode mode) throws MessagingException;

    /**
     * Forces a close of the MailProvider. Any further access will attempt to
     * reopen the MailProvider.
     *
     * @param expunge If true all deleted messages will be expunged.
     */
    public abstract void close(boolean expunge) throws MessagingException;

    /**
     * @return True if further commands are not expected to have to open the
     *         connection.
     */
    public abstract boolean isOpen();

    /**
     * Get the mode the folder was opened with. This may be different than the mode the open
     * was requested with.
     * @return
     */
    public abstract OpenMode getMode() throws MessagingException;

    public abstract boolean create(FolderType type) throws MessagingException;

    /**
     * Create a new folder with a specified display limit.  Not abstract to allow 
     * remote folders to not override or worry about this call if they don't care to.
     */
    public boolean create(FolderType type, int displayLimit) throws MessagingException {
        return create(type);
    }

    public abstract boolean exists() throws MessagingException;

    /**
     * @return A count of the messages in the selected folder.
     */
    public abstract int getMessageCount() throws MessagingException;

    public abstract int getUnreadMessageCount() throws MessagingException;

    public abstract Message getMessage(String uid) throws MessagingException;

    public abstract Message[] getMessages(int start, int end, MessageRetrievalListener listener)
            throws MessagingException;

    /**
     * Fetches the given list of messages. The specified listener is notified as
     * each fetch completes. Messages are downloaded as (as) lightweight (as
     * possible) objects to be filled in with later requests. In most cases this
     * means that only the UID is downloaded.
     *
     * @param uids
     * @param listener
     */
    public abstract Message[] getMessages(MessageRetrievalListener listener)
            throws MessagingException;

    public abstract Message[] getMessages(String[] uids, MessageRetrievalListener listener)
            throws MessagingException;

    public abstract void appendMessages(Message[] messages) throws MessagingException;

    public abstract void copyMessages(Message[] msgs, Folder folder) throws MessagingException;

    public abstract void setFlags(Message[] messages, Flag[] flags, boolean value)
            throws MessagingException;
    
		public abstract void setFlags(Flag[] flags, boolean value) throws MessagingException;

    public abstract String getUidFromMessageId(Message message) throws MessagingException;

    public abstract Message[] expunge() throws MessagingException;

    public abstract void fetch(Message[] messages, FetchProfile fp,
            MessageRetrievalListener listener) throws MessagingException;

    public abstract void delete(boolean recurse) throws MessagingException;

    public abstract String getName();

    public abstract Flag[] getPermanentFlags() throws MessagingException;

    public boolean supportsFetchingFlags() {
        return true;
    }//isFlagSupported

    @Override
    public String toString() {
        return getName();
    }

		public long getLastChecked()
		{
			return lastChecked;
		}

		public void setLastChecked(long lastChecked) throws MessagingException
		{
			this.lastChecked = lastChecked;
		}
		
   	public FolderClass getDisplayClass()
		{
			return FolderClass.NONE;
		}
		
		public FolderClass getSyncClass()
		{
			return getDisplayClass();
		}
		
    public void refresh(Preferences preferences) throws MessagingException
    {

    }

		public String getStatus()
		{
			return status;
		}

		public void setStatus(String status) throws MessagingException
		{
			this.status = status;
		}



}
