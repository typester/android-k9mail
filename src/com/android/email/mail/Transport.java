
package com.android.email.mail;

import com.android.email.mail.transport.SmtpTransport;
import com.android.email.mail.transport.WebDavTransport;

public abstract class Transport {
    protected static final int SOCKET_CONNECT_TIMEOUT = 10000;

    // RFC 1047
    protected static final int SOCKET_READ_TIMEOUT = 300000;

    public synchronized static Transport getInstance(String uri) throws MessagingException {
        if (uri.startsWith("smtp")) {
            return new SmtpTransport(uri);
        } else if (uri.startsWith("webdav")) {
                return new WebDavTransport(uri);
        } else {
            throw new MessagingException("Unable to locate an applicable Transport for " + uri);
        }
    }

    public abstract void open() throws MessagingException;

    public abstract void sendMessage(Message message) throws MessagingException;

    public abstract void close() throws MessagingException;
}
