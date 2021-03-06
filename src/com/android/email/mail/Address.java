
package com.android.email.mail;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.james.mime4j.field.address.AddressList;
import org.apache.james.mime4j.field.address.Mailbox;
import org.apache.james.mime4j.field.address.MailboxList;
import org.apache.james.mime4j.field.address.NamedMailbox;
import org.apache.james.mime4j.field.address.parser.ParseException;
import org.apache.james.mime4j.codec.EncoderUtil;

import android.util.Config;
import android.util.Log;

import com.android.email.Email;
import com.android.email.Utility;
import com.android.email.mail.internet.MimeUtility;

public class Address {
    String mAddress;

    String mPersonal;

    public Address(String address, String personal) {
        this.mAddress = address;
        if ("".equals(personal)) {
            personal = null;
        }
        if (personal!=null) {
            personal = personal.trim();
        }
        this.mPersonal = personal;
    }

    public Address(String address) {
        this.mAddress = address;
    }

    public String getAddress() {
        return mAddress;
    }

    public void setAddress(String address) {
        this.mAddress = address;
    }

    public String getPersonal() {
        return mPersonal;
    }

    public void setPersonal(String personal) {
        if ("".equals(personal)) {
            personal = null;
        }
        if (personal!=null) {
            personal = personal.trim();
        }
        this.mPersonal = personal;
    }

    /**
     * Parse a comma separated list of email addresses in human readable format and return an
     * array of Address objects, RFC-822 encoded.
     * 
     * @param addressList
     * @return An array of 0 or more Addresses.
     */
    public static Address[] parseUnencoded(String addressList) {
        if (addressList != null) {
            String[] addresses = addressList.split(",");
            StringBuilder newAddressList = new StringBuilder();
            String tmp = null;
            String before = null;
            String after = null;
            int pos = -1;
            for( int i = 0, count = addresses.length; i <  count; i++ ) {
                tmp = addresses[i];
                pos = tmp.indexOf("<");
		if( pos > 1 ) {
                    before = tmp.substring( 0, pos );
                    after = tmp.substring( pos );
                    tmp = EncoderUtil.encodeAddressDisplayName( before ) + after;
                }
                if( i > 0 ) {
                    newAddressList.append( "," );
                }
                newAddressList.append( tmp );
            }
            addressList = newAddressList.toString();
        }
	return Address.parse( addressList );
    }
    /**
     * Parse a comma separated list of addresses in RFC-822 format and return an
     * array of Address objects.
     * 
     * @param addressList
     * @return An array of 0 or more Addresses.
     */
    public static Address[] parse(String addressList) {
        ArrayList<Address> addresses = new ArrayList<Address>();
        if (addressList == null) {
            return new Address[] {};
        }
        try {
            MailboxList parsedList = AddressList.parse(addressList).flatten();
            for (int i = 0, count = parsedList.size(); i < count; i++) {
                org.apache.james.mime4j.field.address.Address address = parsedList.get(i);
                if (address instanceof NamedMailbox) {
                    NamedMailbox namedMailbox = (NamedMailbox)address;
                    addresses.add(new Address(namedMailbox.getLocalPart() + "@"
                            + namedMailbox.getDomain(), namedMailbox.getName()));
                } else if (address instanceof Mailbox) {
                    Mailbox mailbox = (Mailbox)address;
                    addresses.add(new Address(mailbox.getLocalPart() + "@" + mailbox.getDomain()));
                } else {
                    Log.e(Email.LOG_TAG, "Unknown address type from Mime4J: "
                            + address.getClass().toString());
                }

            }
        } catch (ParseException pe) {
        }
        return addresses.toArray(new Address[] {});
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Address) {
            return getAddress().equals(((Address) o).getAddress());
        }
        return super.equals(o);
    }

    public String toString() {
        if (mPersonal != null) {
            // if (mPersonal.matches(".*[\\(\\)<>@,;:\\\\\".\\[\\]].*")) {
                return Utility.quoteString(mPersonal) + " <" + mAddress + ">";
            // } else {
            //     return mPersonal + " <" + mAddress + ">";
            // }
        } else {
            return mAddress;
        }
    }

    public static String toString(Address[] addresses) {
        if (addresses == null) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < addresses.length; i++) {
            sb.append(addresses[i].toString());
            if (i < addresses.length - 1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }
    
    /**
     * Returns either the personal portion of the Address or the address portion if the personal
     * is not available.
     * @return
     */
    public String toFriendly() {
        if (mPersonal != null && mPersonal.length() > 0) {
            return  mPersonal;
        }
        else {
            return mAddress;
        }
    }
    
    public static String toFriendly(Address[] addresses) {
        if (addresses == null) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < addresses.length; i++) {
            sb.append(addresses[i].toFriendly());
            if (i < addresses.length - 1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }
    
    /**
     * Unpacks an address list previously packed with packAddressList()
     * @param list
     * @return
     */
    public static Address[] unpack(String addressList) {
        if (addressList == null) {
            return new Address[] { };
        }
        ArrayList<Address> addresses = new ArrayList<Address>();
        int length = addressList.length();
        int pairStartIndex = 0;
        int pairEndIndex = 0;
        int addressEndIndex = 0;
        while (pairStartIndex < length) {
            pairEndIndex = addressList.indexOf(",\u0000", pairStartIndex);
            if (pairEndIndex == -1) {
                pairEndIndex = length;
            }
            addressEndIndex = addressList.indexOf(";\u0000", pairStartIndex);
            String address = null;
            String personal = null;
            if (addressEndIndex == -1 || addressEndIndex > pairEndIndex) {
                address = addressList.substring(pairStartIndex, pairEndIndex);
            }
            else {
                address = addressList.substring(pairStartIndex, addressEndIndex);
                personal =addressList.substring(addressEndIndex + 2, pairEndIndex);
            }
            addresses.add(new Address(address, personal));
            pairStartIndex = pairEndIndex + 2;
        }
        return addresses.toArray(new Address[] { });
    }
    
    /**
     * Packs an address list into a String that is very quick to read
     * and parse. Packed lists can be unpacked with unpackAddressList()
     * The packed list is a "\u0000," seperated list of:
     * address\u0000;personal
     * @param list
     * @return
     */
    public static String pack(Address[] addresses) {
        if (addresses == null) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0, count = addresses.length; i < count; i++) {
            Address address = addresses[i];
                sb.append(address.getAddress());
                String personal = address.getPersonal();
                if (personal != null) {
                    sb.append(";\u0000");
                    // Escape quotes in the address part on the way in
                    personal.replaceAll("\"","\\\"");
                    sb.append(personal);
                }
                if (i < count - 1) {
                    sb.append(",\u0000");
                }
        }
        return sb.toString();
    }
}
