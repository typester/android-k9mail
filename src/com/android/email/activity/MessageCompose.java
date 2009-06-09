
package com.android.email.activity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

import com.android.email.K9Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.OpenableColumns;
import android.text.TextWatcher;
import android.text.util.Rfc822Tokenizer;
import android.util.Config;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AutoCompleteTextView.Validator;

import com.android.email.Account;
import com.android.email.Email;
import com.android.email.EmailAddressAdapter;
import com.android.email.EmailAddressValidator;
import com.android.email.MessagingController;
import com.android.email.MessagingListener;
import com.android.email.Preferences;
import com.android.email.R;
import com.android.email.Utility;
import com.android.email.mail.Address;
import com.android.email.mail.Body;
import com.android.email.mail.Flag;
import com.android.email.mail.Message;
import com.android.email.mail.MessagingException;
import com.android.email.mail.Multipart;
import com.android.email.mail.Part;
import com.android.email.mail.Message.RecipientType;
import com.android.email.mail.internet.MimeBodyPart;
import com.android.email.mail.internet.MimeHeader;
import com.android.email.mail.internet.MimeMessage;
import com.android.email.mail.internet.MimeMultipart;
import com.android.email.mail.internet.MimeUtility;
import com.android.email.mail.internet.TextBody;
import com.android.email.mail.store.LocalStore;
import com.android.email.mail.store.LocalStore.LocalAttachmentBody;

public class MessageCompose extends K9Activity implements OnClickListener, OnFocusChangeListener {
    private static final String ACTION_REPLY = "com.android.email.intent.action.REPLY";
    private static final String ACTION_REPLY_ALL = "com.android.email.intent.action.REPLY_ALL";
    private static final String ACTION_FORWARD = "com.android.email.intent.action.FORWARD";
    private static final String ACTION_EDIT_DRAFT = "com.android.email.intent.action.EDIT_DRAFT";


    private static final String EXTRA_ACCOUNT = "account";
    private static final String EXTRA_FOLDER = "folder";
    private static final String EXTRA_MESSAGE = "message";

    private static final String STATE_KEY_ATTACHMENTS =
        "com.android.email.activity.MessageCompose.attachments";
    private static final String STATE_KEY_CC_SHOWN =
        "com.android.email.activity.MessageCompose.ccShown";
    private static final String STATE_KEY_BCC_SHOWN =
        "com.android.email.activity.MessageCompose.bccShown";
    private static final String STATE_KEY_QUOTED_TEXT_SHOWN =
        "com.android.email.activity.MessageCompose.quotedTextShown";
    private static final String STATE_KEY_SOURCE_MESSAGE_PROCED =
        "com.android.email.activity.MessageCompose.stateKeySourceMessageProced";
    private static final String STATE_KEY_DRAFT_UID =
        "com.android.email.activity.MessageCompose.draftUid";
    private static final String STATE_IDENTITY_CHANGED =
      "com.android.email.activity.MessageCompose.identityChanged";
    private static final String STATE_IDENTITY =
      "com.android.email.activity.MessageCompose.identity";

    private static final int MSG_PROGRESS_ON = 1;
    private static final int MSG_PROGRESS_OFF = 2;
    private static final int MSG_UPDATE_TITLE = 3;
    private static final int MSG_SKIPPED_ATTACHMENTS = 4;
    private static final int MSG_SAVED_DRAFT = 5;
    private static final int MSG_DISCARDED_DRAFT = 6;

    private static final int ACTIVITY_REQUEST_PICK_ATTACHMENT = 1;
    private static final int ACTIVITY_CHOOSE_IDENTITY = 2;

    private Account mAccount;
    private Account.Identity mIdentity;
    private boolean mIdentityChanged = false;
    private boolean mSignatureChanged = false;
    private String mFolder;
    private String mSourceMessageUid;
    private Message mSourceMessage;
    /**
     * Indicates that the source message has been processed at least once and should not
     * be processed on any subsequent loads. This protects us from adding attachments that
     * have already been added from the restore of the view state.
     */
    private boolean mSourceMessageProcessed = false;


    private TextView mFromView;
    private MultiAutoCompleteTextView mToView;
    private MultiAutoCompleteTextView mCcView;
    private MultiAutoCompleteTextView mBccView;
    private EditText mSubjectView;
    private EditText mSignatureView;
    private EditText mMessageContentView;
    private LinearLayout mAttachments;
    private View mQuotedTextBar;
    private ImageButton mQuotedTextDelete;
    private EditText mQuotedText;

    private boolean mDraftNeedsSaving = false;

    /**
     * The draft uid of this message. This is used when saving drafts so that the same draft is
     * overwritten instead of being created anew. This property is null until the first save.
     */
    private String mDraftUid;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case MSG_PROGRESS_ON:
                    setProgressBarIndeterminateVisibility(true);
                    break;
                case MSG_PROGRESS_OFF:
                    setProgressBarIndeterminateVisibility(false);
                    break;
                case MSG_UPDATE_TITLE:
                    updateTitle();
                    break;
                case MSG_SKIPPED_ATTACHMENTS:
                    Toast.makeText(
                            MessageCompose.this,
                            getString(R.string.message_compose_attachments_skipped_toast),
                            Toast.LENGTH_LONG).show();
                    break;
                case MSG_SAVED_DRAFT:
                    Toast.makeText(
                            MessageCompose.this,
                            getString(R.string.message_saved_toast),
                            Toast.LENGTH_LONG).show();
                    break;
                case MSG_DISCARDED_DRAFT:
                    Toast.makeText(
                            MessageCompose.this,
                            getString(R.string.message_discarded_toast),
                            Toast.LENGTH_LONG).show();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    private Listener mListener = new Listener();
    private EmailAddressAdapter mAddressAdapter;
    private Validator mAddressValidator;


    class Attachment implements Serializable {
        public String name;
        public String contentType;
        public long size;
        public Uri uri;
    }

    /**
     * Compose a new message using the given account. If account is null the default account
     * will be used.
     * @param context
     * @param account
     */
    public static void actionCompose(Context context, Account account) {
       Intent i = new Intent(context, MessageCompose.class);
       i.putExtra(EXTRA_ACCOUNT, account);
       context.startActivity(i);
    }

    /**
     * Compose a new message as a reply to the given message. If replyAll is true the function
     * is reply all instead of simply reply.
     * @param context
     * @param account
     * @param message
     * @param replyAll
     */
    public static void actionReply(
            Context context,
            Account account,
            Message message,
            boolean replyAll) { 
        Intent i = new Intent(context, MessageCompose.class);
        i.putExtra(EXTRA_ACCOUNT, account);
        i.putExtra(EXTRA_FOLDER, message.getFolder().getName());
        i.putExtra(EXTRA_MESSAGE, message.getUid());
        if (replyAll) {
            i.setAction(ACTION_REPLY_ALL);
        }
        else {
            i.setAction(ACTION_REPLY);
        }
        context.startActivity(i);
    }

    /**
     * Compose a new message as a forward of the given message.
     * @param context
     * @param account
     * @param message
     */
    public static void actionForward(Context context, Account account, Message message) {
        Intent i = new Intent(context, MessageCompose.class);
        i.putExtra(EXTRA_ACCOUNT, account);
        i.putExtra(EXTRA_FOLDER, message.getFolder().getName());
        i.putExtra(EXTRA_MESSAGE, message.getUid());
        i.setAction(ACTION_FORWARD);
        context.startActivity(i);
    }

    /**
     * Continue composition of the given message. This action modifies the way this Activity
     * handles certain actions.
     * Save will attempt to replace the message in the given folder with the updated version.
     * Discard will delete the message from the given folder.
     * @param context
     * @param account
     * @param folder
     * @param message
     */
    public static void actionEditDraft(Context context, Account account, Message message) {
        Intent i = new Intent(context, MessageCompose.class);
        i.putExtra(EXTRA_ACCOUNT, account);
        i.putExtra(EXTRA_FOLDER, message.getFolder().getName());
        i.putExtra(EXTRA_MESSAGE, message.getUid());
        i.setAction(ACTION_EDIT_DRAFT);
        context.startActivity(i);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.message_compose);

        mAddressAdapter = new EmailAddressAdapter(this);
        mAddressValidator = new EmailAddressValidator();


        mFromView = (TextView)findViewById(R.id.from);
        mToView = (MultiAutoCompleteTextView)findViewById(R.id.to);
        mCcView = (MultiAutoCompleteTextView)findViewById(R.id.cc);
        mBccView = (MultiAutoCompleteTextView)findViewById(R.id.bcc);
        mSubjectView = (EditText)findViewById(R.id.subject);
        
        EditText upperSignature = (EditText)findViewById(R.id.upper_signature);
        EditText lowerSignature = (EditText)findViewById(R.id.lower_signature);
        
        
        mMessageContentView = (EditText)findViewById(R.id.message_content);
        mAttachments = (LinearLayout)findViewById(R.id.attachments);
        mQuotedTextBar = findViewById(R.id.quoted_text_bar);
        mQuotedTextDelete = (ImageButton)findViewById(R.id.quoted_text_delete);
        mQuotedText = (EditText)findViewById(R.id.quoted_text);

        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start,
                                          int before, int after) { }

            public void onTextChanged(CharSequence s, int start,
                                          int before, int count) {
                mDraftNeedsSaving = true;
            }

            public void afterTextChanged(android.text.Editable s) { }
        };

        TextWatcher sigwatcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start,
                                          int before, int after) { }

            public void onTextChanged(CharSequence s, int start,
                                          int before, int count) {
                mDraftNeedsSaving = true;
                mSignatureChanged = true;
            }

            public void afterTextChanged(android.text.Editable s) { }
        };

        mToView.addTextChangedListener(watcher);
        mCcView.addTextChangedListener(watcher);
        mBccView.addTextChangedListener(watcher);
        mSubjectView.addTextChangedListener(watcher);
        
        mMessageContentView.addTextChangedListener(watcher);

        /*
         * We set this to invisible by default. Other methods will turn it back on if it's
         * needed.
         */
        mQuotedTextBar.setVisibility(View.GONE);
        mQuotedText.setVisibility(View.GONE);

        mQuotedTextDelete.setOnClickListener(this);

        mFromView.setVisibility(View.GONE);
               
        mToView.setAdapter(mAddressAdapter);
        mToView.setTokenizer(new Rfc822Tokenizer());
        mToView.setValidator(mAddressValidator);

        mCcView.setAdapter(mAddressAdapter);
        mCcView.setTokenizer(new Rfc822Tokenizer());
        mCcView.setValidator(mAddressValidator);

        mBccView.setAdapter(mAddressAdapter);
        mBccView.setTokenizer(new Rfc822Tokenizer());
        mBccView.setValidator(mAddressValidator);


        mSubjectView.setOnFocusChangeListener(this);

        if (savedInstanceState != null) {
            /*
             * This data gets used in onCreate, so grab it here instead of onRestoreIntstanceState
             */
            mSourceMessageProcessed = savedInstanceState.getBoolean(STATE_KEY_SOURCE_MESSAGE_PROCED, false);
        }

        Intent intent = getIntent();

        String action = intent.getAction();

        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SENDTO.equals(action)) {
            /*
             * Someone has clicked a mailto: link. The address is in the URI.
             */
            mAccount = Preferences.getPreferences(this).getDefaultAccount();
            if (mAccount == null) {
                /*
                 * There are no accounts set up. This should not have happened. Prompt the
                 * user to set up an account as an acceptable bailout.
                 */
                startActivity(new Intent(this, Accounts.class));
                mDraftNeedsSaving = false;
                finish();
                return;
            }
            if (intent.getData() != null) {
                Uri uri = intent.getData();
                try {
                    if (uri.getScheme().equalsIgnoreCase("mailto")) {
                        Address[] addresses = Address.parse(uri.getSchemeSpecificPart());
                        addAddresses(mToView, addresses);
                    }
                }
                catch (Exception e) {
                    /*
                     * If we can't extract any information from the URI it's okay. They can
                     * still compose a message.
                     */
                }
            }
        }
        else if (Intent.ACTION_SEND.equals(action)) {
            /*
             * Someone is trying to compose an email with an attachment, probably Pictures.
             * The Intent should contain an EXTRA_STREAM with the data to attach.
             */

            mAccount = Preferences.getPreferences(this).getDefaultAccount();
            if (mAccount == null) {
                /*
                 * There are no accounts set up. This should not have happened. Prompt the
                 * user to set up an account as an acceptable bailout.
                 */
                startActivity(new Intent(this, Accounts.class));
                mDraftNeedsSaving = false;
                finish();
                return;
            }

            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
            	mMessageContentView.setText(text);
            }
            String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            if (subject != null) {
            	mSubjectView.setText(subject);
            }
            
            String type = intent.getType();
            Uri stream = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream != null && type != null) {
                if (MimeUtility.mimeTypeMatches(type, Email.ACCEPTABLE_ATTACHMENT_SEND_TYPES)) {
                    addAttachment(stream);
                }
            }

            /*
             * There might be an EXTRA_SUBJECT, EXTRA_TEXT, EXTRA_EMAIL, EXTRA_BCC or EXTRA_CC
             */
            
            String extraSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            String[] extraEmail = intent.getStringArrayExtra(Intent.EXTRA_EMAIL);
            String[] extraBcc = intent.getStringArrayExtra(Intent.EXTRA_BCC);
            String[] extraCc = intent.getStringArrayExtra(Intent.EXTRA_CC);

            String addressList = new String();
            // Cache array size, as per Google's recommendations.
            int arraySize;
            int i;

            mSubjectView.setText(extraSubject);
            mMessageContentView.setText(extraText);

            if (extraEmail != null) {
                arraySize = extraEmail.length;
                if (arraySize > 1){
              	    for (i=0; i < (arraySize-1); i++) {
                        addressList += extraEmail[i]+", ";
                    }
                addressList += extraEmail[arraySize-1];
                }
            }
            mToView.setText(addressList);
            addressList = "";

            if (extraBcc != null) {
                arraySize = extraBcc.length;
                if (arraySize > 1) {
              	    for (i=0; i < (arraySize-1); i++) {
                        addressList += extraBcc[i]+", ";
                    }
                addressList += extraBcc[arraySize-1];
                }
            }
            mBccView.setText(addressList);
            addressList = "";

            if (extraCc != null){
                arraySize = extraCc.length;
                if (arraySize > 1){
              	    for (i=0; i < (arraySize-1); i++) {
                        addressList += extraCc[i]+", ";
                    }
                addressList += extraCc[arraySize-1];
                }
            }
            mCcView.setText(addressList);
            addressList = "";

        } else {
            mAccount = (Account) intent.getSerializableExtra(EXTRA_ACCOUNT);
            mFolder = (String) intent.getStringExtra(EXTRA_FOLDER);
            mSourceMessageUid = (String) intent.getStringExtra(EXTRA_MESSAGE);
        }
        
        if (mIdentity == null)
        {
          mIdentity = mAccount.getIdentity(0);
        }
        
        if (mAccount.isSignatureBeforeQuotedText())
        {
            mSignatureView = upperSignature;
            lowerSignature.setVisibility(View.GONE);
        }
        else
        {
            mSignatureView = lowerSignature;
            upperSignature.setVisibility(View.GONE);
        }
        mSignatureView.addTextChangedListener(sigwatcher);
        
        updateFrom();
        updateSignature();
               
        Log.d(Email.LOG_TAG, "action = " + action + ", mAccount = " + mAccount + ", mFolder = " + mFolder + ", mSourceMessageUid = " + mSourceMessageUid);
        if ((ACTION_REPLY.equals(action) || ACTION_REPLY_ALL.equals(action)) && mAccount != null && mFolder != null && mSourceMessageUid != null) {
          Log.d(Email.LOG_TAG, "Setting message ANSWERED flag to true");
          // TODO: Really, we should wait until we send the message, but that would require saving the original
          // message info along with a Draft copy, in case it is left in Drafts for a while before being sent
            MessagingController.getInstance(getApplication()).setMessageFlag(mAccount, mFolder, mSourceMessageUid, Flag.ANSWERED, true);
        }

        if (ACTION_REPLY.equals(action) || ACTION_REPLY_ALL.equals(action) || ACTION_FORWARD.equals(action) || ACTION_EDIT_DRAFT.equals(action)) {
            /*
             * If we need to load the message we add ourself as a message listener here
             * so we can kick it off. Normally we add in onResume but we don't
             * want to reload the message every time the activity is resumed.
             * There is no harm in adding twice.
             */
            MessagingController.getInstance(getApplication()).addListener(mListener);
            MessagingController.getInstance(getApplication()).loadMessageForView( mAccount, mFolder, mSourceMessageUid, null);
        }
        if (ACTION_REPLY.equals(action) || ACTION_REPLY_ALL.equals(action) || ACTION_EDIT_DRAFT.equals(action)) {
            //change focus to message body.
            mMessageContentView.requestFocus();
        }

        if (!ACTION_EDIT_DRAFT.equals(action)) {
            addAddress(mBccView, new Address(mAccount.getAlwaysBcc(), ""));
        }

        updateTitle();
    }

    public void onResume() {
        super.onResume();
        MessagingController.getInstance(getApplication()).addListener(mListener);
    }

    public void onPause() {
        super.onPause();
        saveIfNeeded();
        MessagingController.getInstance(getApplication()).removeListener(mListener);
    }

    /**
     * The framework handles most of the fields, but we need to handle stuff that we
     * dynamically show and hide:
     * Attachment list,
     * Cc field,
     * Bcc field,
     * Quoted text,
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveIfNeeded();
        ArrayList<Uri> attachments = new ArrayList<Uri>();
        for (int i = 0, count = mAttachments.getChildCount(); i < count; i++) {
            View view = mAttachments.getChildAt(i);
            Attachment attachment = (Attachment) view.getTag();
            attachments.add(attachment.uri);
        }
        outState.putParcelableArrayList(STATE_KEY_ATTACHMENTS, attachments);
        outState.putBoolean(STATE_KEY_CC_SHOWN, mCcView.getVisibility() == View.VISIBLE);
        outState.putBoolean(STATE_KEY_BCC_SHOWN, mBccView.getVisibility() == View.VISIBLE);
        outState.putBoolean(STATE_KEY_QUOTED_TEXT_SHOWN, mQuotedTextBar.getVisibility() == View.VISIBLE);
        outState.putBoolean(STATE_KEY_SOURCE_MESSAGE_PROCED, mSourceMessageProcessed);
        outState.putString(STATE_KEY_DRAFT_UID, mDraftUid);
        outState.putSerializable(STATE_IDENTITY, mIdentity);
        outState.putBoolean(STATE_IDENTITY_CHANGED, mIdentityChanged);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        ArrayList<Parcelable> attachments = (ArrayList<Parcelable>) savedInstanceState.getParcelableArrayList(STATE_KEY_ATTACHMENTS);
        mAttachments.removeAllViews();
        for (Parcelable p : attachments) {
            Uri uri = (Uri) p;
            addAttachment(uri);
        }

        mCcView.setVisibility(savedInstanceState.getBoolean(STATE_KEY_CC_SHOWN) ?  View.VISIBLE : View.GONE);
        mBccView.setVisibility(savedInstanceState.getBoolean(STATE_KEY_BCC_SHOWN) ?  View.VISIBLE : View.GONE);
        mQuotedTextBar.setVisibility(savedInstanceState.getBoolean(STATE_KEY_QUOTED_TEXT_SHOWN) ?  View.VISIBLE : View.GONE);
        mQuotedText.setVisibility(savedInstanceState.getBoolean(STATE_KEY_QUOTED_TEXT_SHOWN) ?  View.VISIBLE : View.GONE);
        mDraftUid = savedInstanceState.getString(STATE_KEY_DRAFT_UID);
        mIdentity = (Account.Identity)savedInstanceState.getSerializable(STATE_IDENTITY);
        mIdentityChanged = savedInstanceState.getBoolean(STATE_IDENTITY_CHANGED);
        updateFrom();
        updateSignature();
          
        mDraftNeedsSaving = false;
    }

    private void updateTitle() {
        if (mSubjectView.getText().length() == 0) {
            setTitle(R.string.compose_title);
        } else {
            setTitle(mSubjectView.getText().toString());
        }
    }

    public void onFocusChange(View view, boolean focused) {
        if (!focused) {
            updateTitle();
        }
    }

    private void addAddresses(MultiAutoCompleteTextView view, Address[] addresses) {
        if (addresses == null) {
            return;
        }
        for (Address address : addresses) {
            addAddress(view, address);
        }
    }

    private void addAddress(MultiAutoCompleteTextView view, Address address) {
        view.append(address + ", ");
    }

    private Address[] getAddresses(MultiAutoCompleteTextView view) {
        Address[] addresses = Address.parseUnencoded(view.getText().toString().trim());
        return addresses;
    }

    private MimeMessage createMessage(boolean appendSig) throws MessagingException {
        MimeMessage message = new MimeMessage();
        message.setSentDate(new Date());
        Address from = new Address(mIdentity.getEmail(), mIdentity.getName());
        message.setFrom(from);
        message.setRecipients(RecipientType.TO, getAddresses(mToView));
        message.setRecipients(RecipientType.CC, getAddresses(mCcView));
        message.setRecipients(RecipientType.BCC, getAddresses(mBccView));
        message.setSubject(mSubjectView.getText().toString());
        // XXX TODO - not sure why this won't add header
        // message.setHeader("X-User-Agent", getString(R.string.message_header_mua));

        /*
         * Build the Body that will contain the text of the message. We'll decide where to
         * include it later.
         */

        String text = mMessageContentView.getText().toString();
        if (appendSig && mAccount.isSignatureBeforeQuotedText()) {
            text = appendSignature(text);
        }

        if (mQuotedTextBar.getVisibility() == View.VISIBLE) {
            text += "\n" + mQuotedText.getText().toString();
        }
            
        
        if (appendSig && mAccount.isSignatureBeforeQuotedText() == false) {
            text = appendSignature(text);
        }

        TextBody body = new TextBody(text);

        if (mAttachments.getChildCount() > 0) {
            /*
             * The message has attachments that need to be included. First we add the part
             * containing the text that will be sent and then we include each attachment.
             */

            MimeMultipart mp;

            mp = new MimeMultipart();
            mp.addBodyPart(new MimeBodyPart(body, "text/plain"));

            for (int i = 0, count = mAttachments.getChildCount(); i < count; i++) {
                Attachment attachment = (Attachment) mAttachments.getChildAt(i).getTag();
                MimeBodyPart bp = new MimeBodyPart( new LocalStore.LocalAttachmentBody(attachment.uri, getApplication()));
                bp.setHeader(MimeHeader.HEADER_CONTENT_TYPE, String.format("%s;\n name=\"%s\"", attachment.contentType, attachment.name));
                bp.setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, "base64");
                bp.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, String.format("attachment;\n filename=\"%s\"", attachment.name));
                mp.addBodyPart(bp);
            }

            message.setBody(mp);
        }
        else {
            /*
             * No attachments to include, just stick the text body in the message and call
             * it good.
             */
            message.setBody(body);
        }

        return message;
    }

    private String appendSignature (String text) {
        String signature= mSignatureView.getText().toString();
        
       if (signature != null && ! signature.contentEquals("")){
         text += "\n" + signature;
        }

        return text;
    }

    private void sendOrSaveMessage(boolean save) {
        /*
         * Create the message from all the data the user has entered.
         */
        MimeMessage message;
        try {
            message = createMessage(!save);  // Only append sig on save
        }
        catch (MessagingException me) {
            Log.e(Email.LOG_TAG, "Failed to create new message for send or save.", me);
            throw new RuntimeException("Failed to create a new message for send or save.", me);
        }

        if (save) {
            /*
             * Save a draft
             */
            if (mDraftUid != null) {
                message.setUid(mDraftUid);
            }
            else if (ACTION_EDIT_DRAFT.equals(getIntent().getAction())) {
                /*
                 * We're saving a previously saved draft, so update the new message's uid
                 * to the old message's uid.
                 */
                message.setUid(mSourceMessageUid);
            }
            
            String k9identity = Utility.base64Encode("" + mMessageContentView.getText().toString().length());
            
            if (mIdentityChanged || mSignatureChanged)
            {
              String signature  = mSignatureView.getText().toString();
              k9identity += ":" + Utility.base64Encode(signature) ;
              if (mIdentityChanged)
              {
              
                  String name = mIdentity.getName();
                  String email = mIdentity.getEmail();
                  
                  k9identity +=  ":" + Utility.base64Encode(name) + ":" + Utility.base64Encode(email);
              }
            }
            
            Log.d(Email.LOG_TAG, "Saving identity: " + k9identity);
            message.setHeader(Email.K9MAIL_IDENTITY, k9identity);
            
            MessagingController.getInstance(getApplication()).saveDraft(mAccount, message);
            mDraftUid = message.getUid();

            // Don't display the toast if the user is just changing the orientation
            if ((getChangingConfigurations() & ActivityInfo.CONFIG_ORIENTATION) == 0) {
                mHandler.sendEmptyMessage(MSG_SAVED_DRAFT);
            }
        }
        else {
            /*
             * Send the message
             * TODO Is it possible for us to be editing a draft with a null source message? Don't
             * think so. Could probably remove below check.
             */
            if (ACTION_EDIT_DRAFT.equals(getIntent().getAction()) && mSourceMessageUid != null) {
                /*
                 * We're sending a previously saved draft, so delete the old draft first.
                 */
                MessagingController.getInstance(getApplication()).deleteMessage( mAccount, mFolder, mSourceMessage, null);
            }
            MessagingController.getInstance(getApplication()).sendMessage(mAccount, message, null);
        }
    }

    private void saveIfNeeded() {
        if (!mDraftNeedsSaving) {
            return;
        }
        mDraftNeedsSaving = false;
        sendOrSaveMessage(true);
    }

    private void onSend() {
        if (getAddresses(mToView).length == 0 && getAddresses(mCcView).length == 0 && getAddresses(mBccView).length == 0) {
            mToView.setError(getString(R.string.message_compose_error_no_recipients));
            Toast.makeText(this, getString(R.string.message_compose_error_no_recipients), Toast.LENGTH_LONG).show();
            return;
        }
        sendOrSaveMessage(false);
        mDraftNeedsSaving = false;
        finish();
    }

    private void onDiscard() {
        if (mSourceMessageUid != null) {
            if (ACTION_EDIT_DRAFT.equals(getIntent().getAction()) && mSourceMessageUid != null) {
                MessagingController.getInstance(getApplication()).deleteMessage( mAccount, mFolder, mSourceMessage, null);
            }
        }
        mHandler.sendEmptyMessage(MSG_DISCARDED_DRAFT);
        mDraftNeedsSaving = false;
        finish();
    }

    private void onSave() {
        saveIfNeeded();
        finish();
    }

    private void onAddCcBcc() {
        mCcView.setVisibility(View.VISIBLE);
        mBccView.setVisibility(View.VISIBLE);
    }

    /**
     * Kick off a picker for whatever kind of MIME types we'll accept and let Android take over.
     */
    private void onAddAttachment() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(Email.ACCEPTABLE_ATTACHMENT_SEND_TYPES[0]);
        startActivityForResult(Intent.createChooser(i, null), ACTIVITY_REQUEST_PICK_ATTACHMENT);
    }

    private void addAttachment(Uri uri) {
        addAttachment(uri, -1, null);
    }

    private void addAttachment(Uri uri, int size, String name) {
        ContentResolver contentResolver = getContentResolver();

        String contentType = contentResolver.getType(uri);

        if (contentType == null) {
            contentType = "";
        }

        Attachment attachment = new Attachment();
        attachment.name = name;
        attachment.contentType = contentType;
        attachment.size = size;
        attachment.uri = uri;

        if (attachment.size == -1 || attachment.name == null) {
            Cursor metadataCursor = contentResolver.query( uri, new String[]{ OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE }, null, null, null);
            if (metadataCursor != null) {
                try {
                    if (metadataCursor.moveToFirst()) {
                        if (attachment.name == null) {
                            attachment.name = metadataCursor.getString(0);
                        }
                        if (attachment.size == -1) {
                            attachment.size = metadataCursor.getInt(1);
                        }
                    }
                } finally {
                    metadataCursor.close();
                }
            }
        }

        if (attachment.name == null) {
            attachment.name = uri.getLastPathSegment();
        }

        View view = getLayoutInflater().inflate( R.layout.message_compose_attachment, mAttachments, false);
        TextView nameView = (TextView)view.findViewById(R.id.attachment_name);
        ImageButton delete = (ImageButton)view.findViewById(R.id.attachment_delete);
        nameView.setText(attachment.name);
        delete.setOnClickListener(this);
        delete.setTag(view);
        view.setTag(attachment);
        mAttachments.addView(view);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
      if(resultCode != RESULT_OK)
        return;
        if (data == null) {
            return;
        }
      switch(requestCode) {
        case ACTIVITY_REQUEST_PICK_ATTACHMENT:
          
        addAttachment(data.getData());
        mDraftNeedsSaving = true;
          break;
        case ACTIVITY_CHOOSE_IDENTITY:
          onIdentityChosen(data);
          break;
    }
    }

    private void onIdentityChosen(Intent intent)
    {
      Bundle bundle = intent.getExtras();;
      mIdentity = (Account.Identity)bundle.getSerializable(ChooseIdentity.EXTRA_IDENTITY);
//      if (mIdentityChanged == false)
//      {
//          Toast.makeText(this, getString(R.string.identity_will_not_be_saved),
//                  Toast.LENGTH_LONG).show();
//      }
      mIdentityChanged = true;
      mDraftNeedsSaving = true;
      updateFrom();
      updateSignature();
    }
    
    private void updateFrom()
    {
      if (mIdentityChanged)
      {
        mFromView.setVisibility(View.VISIBLE);
      }
      mFromView.setText(getString(R.string.message_view_from_format, mIdentity.getName(), mIdentity.getEmail()));
    }
    
    private void updateSignature()
    {
      mSignatureView.setText(mIdentity.getSignature());
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.attachment_delete:
                /*
                 * The view is the delete button, and we have previously set the tag of
                 * the delete button to the view that owns it. We don't use parent because the
                 * view is very complex and could change in the future.
                 */
                mAttachments.removeView((View) view.getTag());
                mDraftNeedsSaving = true;
                break;
            case R.id.quoted_text_delete:
                mQuotedTextBar.setVisibility(View.GONE);
                mQuotedText.setVisibility(View.GONE);
                mDraftNeedsSaving = true;
                break;
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.send:
                onSend();
                break;
            case R.id.save:
                onSave();
                break;
            case R.id.discard:
                onDiscard();
                break;
            case R.id.add_cc_bcc:
                onAddCcBcc();
                break;
            case R.id.add_attachment:
                onAddAttachment();
                break;
            case R.id.choose_identity:
              onChooseIdentity();
              break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void onChooseIdentity()
    {
        if (mAccount.getIdentities().size() > 1)
        {
          Intent intent = new Intent(this, ChooseIdentity.class);
          intent.putExtra(ChooseIdentity.EXTRA_ACCOUNT, mAccount);
          startActivityForResult(intent, ACTIVITY_CHOOSE_IDENTITY);
        }
        else
        {
            Toast.makeText(this, getString(R.string.no_identities),
                    Toast.LENGTH_LONG).show();
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.message_compose_option, menu);
     
        return true;
    }

    /**
     * Returns true if all attachments were able to be attached, otherwise returns false.
     */
    private boolean loadAttachments(Part part, int depth) throws MessagingException {
        if (part.getBody() instanceof Multipart) {
            Multipart mp = (Multipart) part.getBody();
            boolean ret = true;
            for (int i = 0, count = mp.getCount(); i < count; i++) {
                if (!loadAttachments(mp.getBodyPart(i), depth + 1)) {
                    ret = false;
                }
            }
            return ret;
        } else {
            String contentType = MimeUtility.unfoldAndDecode(part.getContentType());
            String name = MimeUtility.getHeaderParameter(contentType, "name");
            if (name != null) {
                Body body = part.getBody();
                if (body != null && body instanceof LocalAttachmentBody) {
                    final Uri uri = ((LocalAttachmentBody) body).getContentUri();
                    mHandler.post(new Runnable() {
                        public void run() {
                            addAttachment(uri);
                        }
                    });
                }
                else {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Pull out the parts of the now loaded source message and apply them to the new message
     * depending on the type of message being composed.
     * @param message
     */
    private void processSourceMessage(Message message) {
        String action = getIntent().getAction();
        if (ACTION_REPLY.equals(action) || ACTION_REPLY_ALL.equals(action)) {
            try {
                if (message.getSubject() != null && !message.getSubject().toLowerCase().startsWith("re:")) {
                    mSubjectView.setText("Re: " + message.getSubject());
                }
                else {
                    mSubjectView.setText(message.getSubject());
                }
                /*
                 * If a reply-to was included with the message use that, otherwise use the from
                 * or sender address.
                 */
                Address[] replyToAddresses;
                if (message.getReplyTo().length > 0) {
                    addAddresses(mToView, replyToAddresses = message.getReplyTo());
                }
                else {
                    addAddresses(mToView, replyToAddresses = message.getFrom());
                }
                
                Part part = MimeUtility.findFirstPartByMimeType(mSourceMessage,
                        "text/plain");
                if (part != null) {
                    String quotedText = String.format(
                            getString(R.string.message_compose_reply_header_fmt),
                            Address.toString(mSourceMessage.getFrom()));
                    
                    quotedText += MimeUtility.getTextFromPart(part).replaceAll("(?m)^", ">");
                    mQuotedText.setText(quotedText);

                    mQuotedTextBar.setVisibility(View.VISIBLE);
                    mQuotedText.setVisibility(View.VISIBLE);
                }
                
                if (ACTION_REPLY_ALL.equals(action)) {
                    for (Address address : message.getRecipients(RecipientType.TO)) {
                        if (!mAccount.isAnIdentity(address)) {
                            addAddress(mToView, address);
                        }
                    }
                    if (message.getRecipients(RecipientType.CC).length > 0) {
                        for (Address address : message.getRecipients(RecipientType.CC)) {
                            if (!Utility.arrayContains(replyToAddresses, address)) {
                                addAddress(mCcView, address);
                            }
                        }
                        mCcView.setVisibility(View.VISIBLE);
                    }
                }
             }
            catch (MessagingException me) {
                /*
                 * This really should not happen at this point but if it does it's okay.
                 * The user can continue composing their message.
                 */
            }
        }
        else if (ACTION_FORWARD.equals(action)) {
            try {
                if (message.getSubject() != null && !message.getSubject().toLowerCase().startsWith("fwd:")) {
                    mSubjectView.setText("Fwd: " + message.getSubject());
                }
                else {
                    mSubjectView.setText(message.getSubject());
                }
                Part part = MimeUtility.findFirstPartByMimeType(message, "text/plain");
                if (part == null) {
                    part = MimeUtility.findFirstPartByMimeType(message, "text/html");
                }
                if (part != null) {
                    String quotedText = MimeUtility.getTextFromPart(part);
                    if (quotedText != null) {
                        String text = String.format(
                                getString(R.string.message_compose_fwd_header_fmt),
                                mSourceMessage.getSubject(),
                                Address.toString(mSourceMessage.getFrom()),
                                Address.toString(
                                        mSourceMessage.getRecipients(RecipientType.TO)),
                                Address.toString(
                                        mSourceMessage.getRecipients(RecipientType.CC)));
                    
                        text += quotedText;
                        mQuotedText.setText(text);
                        mQuotedTextBar.setVisibility(View.VISIBLE);
                        mQuotedText.setVisibility(View.VISIBLE);
                    }
                }
                if (!mSourceMessageProcessed) {
                    if (!loadAttachments(message, 0)) {
                        mHandler.sendEmptyMessage(MSG_SKIPPED_ATTACHMENTS);
                    }
                }
            }
            catch (MessagingException me) {
                /*
                 * This really should not happen at this point but if it does it's okay.
                 * The user can continue composing their message.
                 */
            }
        }
        else if (ACTION_EDIT_DRAFT.equals(action)) {
            try {
                mSubjectView.setText(message.getSubject());
                addAddresses(mToView, message.getRecipients(RecipientType.TO));
                if (message.getRecipients(RecipientType.CC).length > 0) {
                    addAddresses(mCcView, message.getRecipients(RecipientType.CC));
                    mCcView.setVisibility(View.VISIBLE);
                }
                if (message.getRecipients(RecipientType.BCC).length > 0) {
                    addAddresses(mBccView, message.getRecipients(RecipientType.BCC));
                    mBccView.setVisibility(View.VISIBLE);
                }
                
                if (!mSourceMessageProcessed) {
                    loadAttachments(message, 0);
                }
                Integer bodyLength = null;
                String[] k9identities = message.getHeader(Email.K9MAIL_IDENTITY);
                if (k9identities != null && k9identities.length > 0)
                {
                  String k9identity = k9identities[0];
                
                  if (k9identity != null)
                  {
                    Log.d(Email.LOG_TAG, "Got a saved identity: " + k9identity);
                    StringTokenizer tokens = new StringTokenizer(k9identity, ":", false);
                    
                    String bodyLengthS = null;
                    String name = null;
                    String email = null;
                    String signature = null;
                    if (tokens.hasMoreTokens())
                    {
                      bodyLengthS = Utility.base64Decode(tokens.nextToken());
                      try
                      {
                          bodyLength = Integer.parseInt(bodyLengthS);
                      }
                      catch (Exception e)
                      {
                          Log.e(Email.LOG_TAG, "Unable to parse bodyLength '" + bodyLengthS + "'");
                      }
                    }
                    if (tokens.hasMoreTokens())
                    {
                      signature = Utility.base64Decode(tokens.nextToken());
                    }
                    if (tokens.hasMoreTokens())
                    {
                      name = Utility.base64Decode(tokens.nextToken());
                    }
                    if (tokens.hasMoreTokens())
                    {
                      email = Utility.base64Decode(tokens.nextToken());
                    }
                    
                    Account.Identity newIdentity= mAccount.new Identity();
                    if (signature != null)
                    {
                        newIdentity.setSignature(signature); 
                        mSignatureChanged = true;
                    }
                    else
                    {
                        newIdentity.setSignature(mIdentity.getSignature());
                    }
                    
                    if (name != null)
                    {
                        newIdentity.setName(name);
                        mIdentityChanged = true;
                    }
                    else
                    {
                        newIdentity.setName(mIdentity.getName());
                    }
                    
                    if (email != null)
                    {
                        newIdentity.setEmail(email);
                        mIdentityChanged = true;
                    }
                    else
                    {
                        newIdentity.setEmail(mIdentity.getEmail());
                    }
                    
                    mIdentity = newIdentity;
                    
                    updateSignature();
                    updateFrom();
                    
                  }
                }
                Part part = MimeUtility.findFirstPartByMimeType(message, "text/plain");
                if (part != null) {
                    String text = MimeUtility.getTextFromPart(part);
                    if (bodyLength != null && bodyLength + 1 < text.length())   // + 1 to get rid of the newline we added when saving the draft
                    {
                        String bodyText = text.substring(0, bodyLength);
                        String quotedText = text.substring(bodyLength + 1, text.length());

                        mMessageContentView.setText(bodyText);
                        mQuotedText.setText(quotedText);

                        mQuotedTextBar.setVisibility(View.VISIBLE);
                        mQuotedText.setVisibility(View.VISIBLE);
                    }
                    else
                    {
                    mMessageContentView.setText(text);
                }
                }
            }
            catch (MessagingException me) {
                // TODO
            }
        }
        mSourceMessageProcessed = true;
        mDraftNeedsSaving = false;
    }

    class Listener extends MessagingListener {
        @Override
        public void loadMessageForViewStarted(Account account, String folder, String uid) {
            mHandler.sendEmptyMessage(MSG_PROGRESS_ON);
        }

        @Override
        public void loadMessageForViewFinished(Account account, String folder, String uid, Message message) {
            mHandler.sendEmptyMessage(MSG_PROGRESS_OFF);
        }

        @Override
        public void loadMessageForViewBodyAvailable(Account account, String folder, String uid, final Message message) {
            mSourceMessage = message;
            runOnUiThread(new Runnable() {
                public void run() {
                    processSourceMessage(message);
                }
            });
        }

        @Override
        public void loadMessageForViewFailed(Account account, String folder, String uid, final String message) {
            mHandler.sendEmptyMessage(MSG_PROGRESS_OFF);
            // TODO show network error
        }

        @Override
        public void messageUidChanged( Account account, String folder, String oldUid, String newUid) {
            if (account.equals(mAccount) && (folder.equals(mFolder) || (mFolder == null && folder.equals(mAccount.getDraftsFolderName())))) {
                if (oldUid.equals(mDraftUid)) {
                    mDraftUid = newUid;
                }
                if (oldUid.equals(mSourceMessageUid)) {
                    mSourceMessageUid = newUid;
                }
                if (mSourceMessage != null && (oldUid.equals(mSourceMessage.getUid()))) {
                    mSourceMessage.setUid(newUid);
                }
            }
        }
    }
}
