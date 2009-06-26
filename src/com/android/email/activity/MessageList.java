package com.android.email.activity;
//import android.os.Debug;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.util.Config;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.AdapterView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

import com.android.email.K9ListActivity;
import com.android.email.Account;
import com.android.email.Email;
import com.android.email.MessagingController;
import com.android.email.MessagingListener;
import com.android.email.R;
import com.android.email.Utility;
import com.android.email.MessagingController.SORT_TYPE;
import com.android.email.activity.MessageList.MessageInfoHolder;
import com.android.email.activity.setup.FolderSettings;
import com.android.email.mail.Address;
import com.android.email.mail.Flag;
import com.android.email.mail.Folder;
import com.android.email.mail.Message;
import com.android.email.mail.MessagingException;
import com.android.email.mail.Store;
import com.android.email.mail.Message.RecipientType;
import com.android.email.mail.store.LocalStore;
import com.android.email.mail.store.LocalStore.LocalFolder;
import com.android.email.mail.store.LocalStore.LocalMessage;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;



/**
 * MessageList is the primary user interface for the program. This
 * Activity shows a two level list of the Account's folders and each folder's
 * messages. From this Activity the user can perform all standard message
 * operations.
 *
 *
 * TODO Break out seperate functions for: refresh local folders refresh remote
 * folders refresh open folder local messages refresh open folder remote
 * messages
 *
 * And don't refresh remote folders ever unless the user runs a refresh. Maybe
 * not even then.
 */

public class MessageList extends K9ListActivity {

    private static final String INTENT_DATA_PATH_SUFFIX = "/accounts";

    private static final int DIALOG_MARK_ALL_AS_READ = 1;

    private static final int ACTIVITY_CHOOSE_FOLDER_MOVE = 1;

    private static final int ACTIVITY_CHOOSE_FOLDER_COPY = 2;


    private static final boolean FORCE_REMOTE_SYNC = true;

    private static final String EXTRA_ACCOUNT = "account";
    private static final String EXTRA_STARTUP = "startup";

    private static final String EXTRA_CLEAR_NOTIFICATION = "clearNotification";

    private static final String EXTRA_FOLDER = "folder";
    private static final String STATE_KEY_LIST = "com.android.email.activity.messagelist_state";

    private static final String STATE_CURRENT_FOLDER = "com.android.email.activity.messagelist_folder";
    private static final String STATE_KEY_SELECTION = "com.android.email.activity.messagelist_selection";

    private static final int[] colorChipResIds = new int[] {
                R.drawable.appointment_indicator_leftside_1,
                R.drawable.appointment_indicator_leftside_2,
                R.drawable.appointment_indicator_leftside_3,
                R.drawable.appointment_indicator_leftside_4,
                R.drawable.appointment_indicator_leftside_5,
                R.drawable.appointment_indicator_leftside_6,
                R.drawable.appointment_indicator_leftside_7,
                R.drawable.appointment_indicator_leftside_8,
                R.drawable.appointment_indicator_leftside_9,
                R.drawable.appointment_indicator_leftside_10,
                R.drawable.appointment_indicator_leftside_11,
                R.drawable.appointment_indicator_leftside_12,
                R.drawable.appointment_indicator_leftside_13,
                R.drawable.appointment_indicator_leftside_14,
                R.drawable.appointment_indicator_leftside_15,
                R.drawable.appointment_indicator_leftside_16,
                R.drawable.appointment_indicator_leftside_17,
                R.drawable.appointment_indicator_leftside_18,
                R.drawable.appointment_indicator_leftside_19,
                R.drawable.appointment_indicator_leftside_20,
                R.drawable.appointment_indicator_leftside_21,
            };

    private ListView mListView;

    private int colorChipResId;

    private MessageListAdapter mAdapter;

    private FolderInfoHolder mCurrentFolder;

    private LayoutInflater mInflater;

    private Account mAccount;


    /**
    * Stores the name of the folder that we want to open as soon as possible
    * after load. It is set to null once the folder has been opened once.
     */
    private String mFolderName;


    private boolean mRestoringState;

    private boolean mRefreshRemote;

    private MessageListHandler mHandler = new MessageListHandler();

    private DateFormat dateFormat = null;

    private DateFormat timeFormat = null;

    private SORT_TYPE sortType = SORT_TYPE.SORT_DATE;

    private boolean sortAscending = true;

    private boolean sortDateAscending = false;

    private boolean mStartup = false;

    private static final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(1, 1, 120000L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue());

    private DateFormat getDateFormat() {
        if (dateFormat == null) {
            String dateFormatS = android.provider.Settings.System.getString(getContentResolver(),
                                 android.provider.Settings.System.DATE_FORMAT);

            if (dateFormatS != null) {
                dateFormat = new java.text.SimpleDateFormat(dateFormatS);
            } else {
                dateFormat = new java.text.SimpleDateFormat(Email.BACKUP_DATE_FORMAT);
            }
        }

        return dateFormat;
    }

    private DateFormat getTimeFormat() {

        if (timeFormat == null) {
            String timeFormatS = android.provider.Settings.System.getString(getContentResolver(),
                                 android.provider.Settings.System.TIME_12_24);
            boolean b24 =  !(timeFormatS == null || timeFormatS.equals("12"));
            timeFormat = new java.text.SimpleDateFormat(b24 ? Email.TIME_FORMAT_24 : Email.TIME_FORMAT_12);
        }

        return timeFormat;
    }

    private void clearFormats() {
        dateFormat = null;
        timeFormat = null;
    }



    class MessageListHandler extends Handler {

        private static final int MSG_PROGRESS = 2;

        private static final int MSG_DATA_CHANGED = 3;

        private static final int MSG_FOLDER_LOADING = 7;

        private static final int MSG_REMOVE_MESSAGE = 11;

        private static final int MSG_SYNC_MESSAGES = 13;

        private static final int MSG_FOLDER_SYNCING = 18;

        private static final int MSG_SENDING_OUTBOX = 19;

        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
            case MSG_PROGRESS:
                showProgressIndicator(msg.arg1 != 0 );
                break;

            case MSG_DATA_CHANGED:
                mAdapter.notifyDataSetChanged();

                break;

            case MSG_REMOVE_MESSAGE: {
                FolderInfoHolder folder = (FolderInfoHolder)((Object[]) msg.obj)[0];
                MessageInfoHolder message = (MessageInfoHolder)((Object[]) msg.obj)[1];
                folder.messages.remove(message);
                mAdapter.notifyDataSetChanged();
                break;
            }

            case MSG_SYNC_MESSAGES: {
                FolderInfoHolder folder = (FolderInfoHolder)((Object[]) msg.obj)[0];
                Message[] messages = (Message[])((Object[]) msg.obj)[1];

                for(MessageInfoHolder message : mAdapter.messages) {
                    message.dirty = true;
                }

                for (Message message : messages) {
                    mAdapter.addOrUpdateMessage(folder, message, true, false);
                    mAdapter.notifyDataSetChanged();
                }
                mAdapter.removeDirtyMessages();                
                break;
            }

            case MSG_FOLDER_SYNCING: {
                String folderName = (String)((Object[]) msg.obj)[0];
                String dispString;
                dispString = mAccount.getDescription();

                if (folderName != null) {
                    dispString += " (" + getString(R.string.status_loading)
                                  + folderName + ")";
                }

                setTitle(dispString);

                break;
            }

            case MSG_SENDING_OUTBOX: {
                boolean sending = (msg.arg1 != 0);
                String dispString;
                dispString = mAccount.getDescription();

                if (sending) {
                    dispString += " (" + getString(R.string.status_sending) + ")";
                }

                setTitle(dispString);

                break;
            }

            default:
                super.handleMessage(msg);
            }
        }

        public void synchronizeMessages(FolderInfoHolder folder, Message[] messages) {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_SYNC_MESSAGES;
            msg.obj = new Object[] { folder, messages };
            sendMessage(msg);
        }


        public void removeMessage(MessageInfoHolder message) {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_REMOVE_MESSAGE;
            msg.obj = new Object[] { message.folder, message };
            sendMessage(msg);
        }

        public void folderLoading(String folder, boolean loading) {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_FOLDER_LOADING;
            msg.arg1 = loading ? 1 : 0;
            msg.obj = folder;
            sendMessage(msg);
        }

        public void progress(boolean progress) {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_PROGRESS;
            msg.arg1 = progress ? 1 : 0;
            sendMessage(msg);
        }

        public void dataChanged() {
            sendEmptyMessage(MSG_DATA_CHANGED);
        }

        public void folderSyncing(String folder) {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_FOLDER_SYNCING;
            msg.obj = new String[]
                      { folder };
            sendMessage(msg);
        }

        public void sendingOutbox(boolean sending) {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_SENDING_OUTBOX;
            msg.arg1 = sending ? 1 : 0;
            sendMessage(msg);
        }
    }

    /**
    * This class is responsible for reloading the list of local messages for a
    * given folder, notifying the adapter that the message have been loaded and
    * queueing up a remote update of the folder.
     */

    public static void actionHandleFolder(Context context, Account account, String folder, boolean startup) {
        Intent intent = new Intent(context, MessageList.class);
        intent.putExtra(EXTRA_ACCOUNT, account);
        intent.putExtra(EXTRA_STARTUP, startup);

        if (folder != null) {
            intent.putExtra(EXTRA_FOLDER, folder);
        }

        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        //Debug.startMethodTracing("k9");
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        mListView = getListView();
        mListView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);
        mListView.setLongClickable(true);
        //mListView.setFastScrollEnabled(true); // XXX TODO - reenable when we switch to 1.5
        mListView.setScrollingCacheEnabled(true);

        registerForContextMenu(mListView);

        /*
        * We manually save and restore the list's state because our adapter is
        * slow.
         */
        mListView.setSaveEnabled(false);

        mInflater = getLayoutInflater();

        Intent intent = getIntent();
        mAccount = (Account)intent.getSerializableExtra(EXTRA_ACCOUNT);
        mStartup = (boolean)intent.getBooleanExtra(EXTRA_STARTUP, false);

        // Take the initial folder into account only if we are *not* restoring the
        // activity already

        if (savedInstanceState == null) {
            mFolderName = intent.getStringExtra(EXTRA_FOLDER);

            if (mFolderName == null) {
                mFolderName = mAccount.getAutoExpandFolderName();
            }
        } else {
            mFolderName = savedInstanceState.getString(STATE_CURRENT_FOLDER);
        }

        mListView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView parent, View v, int itemPosition, long id){
                    if ((itemPosition+1) == (mAdapter.getCount() )) {
                        onRefresh(FORCE_REMOTE_SYNC);
                        return;
                    } else { 
                        MessageInfoHolder message = (MessageInfoHolder) mAdapter.getItem( itemPosition);
                        onOpenMessage( message);
                    }
            }


        });

        /*
        * Since the color chip is always the same color for a given account we just
        * cache the id of the chip right here.
         */
        colorChipResId = colorChipResIds[mAccount.getAccountNumber() % colorChipResIds.length];

        mAdapter = new MessageListAdapter();

        mCurrentFolder = mAdapter.getFolder(mFolderName);

        setListAdapter(mAdapter);

        if (savedInstanceState != null) {
            mRestoringState = true;
            onRestoreListState(savedInstanceState);
            mRestoringState = false;
        }

        setTitle(
                    mAccount.getDescription()
                    + " - " +
                    mCurrentFolder.displayName
                    
                    );
    }

    private void onRestoreListState(Bundle savedInstanceState) {
            String currentFolder = savedInstanceState.getString(STATE_CURRENT_FOLDER);
            int selectedChild = savedInstanceState.getInt( STATE_KEY_SELECTION, -1);

            if (selectedChild != 0 ){
                mListView.setSelection(selectedChild);
            }
            if (currentFolder != null ) { 
                mCurrentFolder = mAdapter.getFolder(currentFolder);
            }


        mListView.onRestoreInstanceState(savedInstanceState.getParcelable(STATE_KEY_LIST));
    }

    @Override
    public void onPause() {
        super.onPause();
        //Debug.stopMethodTracing();
        MessagingController.getInstance(getApplication()).removeListener( mAdapter.mListener);
    }

    /**
    * On resume we refresh 
    * messages for any folder that is currently open. This guarantees that things
    * like unread message count and read status are updated.
     */
    @Override
    public void onResume() {
        super.onResume();
        clearFormats();
        sortType = MessagingController.getInstance(getApplication()).getSortType();
        sortAscending = MessagingController.getInstance(getApplication()).isSortAscending(sortType);
        sortDateAscending = MessagingController.getInstance(getApplication()).isSortAscending(SORT_TYPE.SORT_DATE);

        MessagingController.getInstance(getApplication()).addListener( mAdapter.mListener);

        onRefresh(!FORCE_REMOTE_SYNC);

        NotificationManager notifMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notifMgr.cancel(mAccount.getAccountNumber());
        notifMgr.cancel(-1000 - mAccount.getAccountNumber());

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_KEY_LIST, mListView.onSaveInstanceState());
        outState.putInt(STATE_KEY_SELECTION, mListView .getSelectedItemPosition());
        outState.putString(STATE_CURRENT_FOLDER, mCurrentFolder.name);
    }





    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //Shortcuts that work no matter what is selected

        switch (keyCode) {
        case KeyEvent.KEYCODE_C: { onCompose(); return true;}

        case KeyEvent.KEYCODE_Q: { onShowFolderList(); return true; }

        case KeyEvent.KEYCODE_O: { onCycleSort(); return true; }

        case KeyEvent.KEYCODE_I: { onToggleSortAscending(); return true; }

        case KeyEvent.KEYCODE_H: {
            Toast toast = Toast.makeText(this, R.string.message_list_help_key, Toast.LENGTH_LONG);
            toast.show();
            return true;
        }
        }//switch

        int position = mListView.getSelectedItemPosition();
        try {
               if (position >= 0 ) {
                MessageInfoHolder message = (MessageInfoHolder) mAdapter.getItem(position);

                if (message != null) {
                    switch (keyCode) {
                    case KeyEvent.KEYCODE_DEL: { onDelete(message, position); return true;}

                    case KeyEvent.KEYCODE_D: { onDelete(message, position); return true;}

                    case KeyEvent.KEYCODE_F: { onForward(message); return true;}

                    case KeyEvent.KEYCODE_A: { onReplyAll(message); return true; }

                    case KeyEvent.KEYCODE_R: { onReply(message); return true; }

                    case KeyEvent.KEYCODE_G: { onToggleFlag(message); return true; }

                    case KeyEvent.KEYCODE_M: { onMove(message); return true; }

                    case KeyEvent.KEYCODE_Y: { onCopy(message); return true; }

                    case KeyEvent.KEYCODE_Z: { onToggleRead(message); return true; }
                    }
                }
            }
        } finally {
            return super.onKeyDown(keyCode, event);
        }
    }//onKeyDown




    private void onRefresh(final boolean forceRemote) {
        if (forceRemote) {
            mRefreshRemote = true;
        }

        new Thread() {

            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                if (forceRemote) {
                    MessagingController.getInstance(getApplication()).synchronizeMailbox( mAccount, mFolderName, mAdapter.mListener);
                    MessagingController.getInstance(getApplication()).sendPendingMessages(mAccount, null);
                }
                MessagingController.getInstance(getApplication()).listLocalMessages(mAccount, mFolderName,  mAdapter.mListener);

            }
        }

        .start();
    }

    private void onOpenMessage( MessageInfoHolder message) {
        /*
        * We set read=true here for UI performance reasons. The actual value will
        * get picked up on the refresh when the Activity is resumed but that may
        * take a second or so and we don't want this to show and then go away. I've
        * gone back and forth on this, and this gives a better UI experience, so I
        * am putting it back in.
         */

        if (!message.read) {
            message.read = true;
            mHandler.dataChanged();
        }

        if (message.folder.name.equals(mAccount.getDraftsFolderName())) {
            MessageCompose.actionEditDraft(this, mAccount, message.message);
        } else {
            ArrayList<String> folderUids = new ArrayList<String>();

            for (MessageInfoHolder holder : mAdapter.messages) {
                folderUids.add(holder.uid);
            }

            MessageView.actionView(this, mAccount, message.folder.name, message.uid, folderUids);
        }
    }

    private void onShowFolderList() {
        // If we're a child activity (say because Welcome dropped us straight to the message list
        // we won't have a parent activity and we'll need to get back to it
        if (mStartup
            || isTaskRoot()) {
            FolderList.actionHandleAccount(this, mAccount, false);
        }
        finish();
    }

    private void onCompose() {
        MessageCompose.actionCompose(this, mAccount);
    }

    private void changeSort(SORT_TYPE newSortType) {
        sortType = newSortType;
        MessagingController.getInstance(getApplication()).setSortType(sortType);
        sortAscending = MessagingController.getInstance(getApplication()).isSortAscending(sortType);
        sortDateAscending = MessagingController.getInstance(getApplication()).isSortAscending(SORT_TYPE.SORT_DATE);
        reSort();
    }

    private void reSort() {
        int toastString = sortType.getToast(sortAscending);

        Toast toast = Toast.makeText(this, toastString, Toast.LENGTH_SHORT);
        toast.show();

        Collections.sort(mAdapter.messages);

        mAdapter.notifyDataSetChanged();

    }


    private void onCycleSort() {
        SORT_TYPE[] sorts = SORT_TYPE.values();
        int curIndex = 0;

        for (int i = 0; i < sorts.length; i++) {
            if (sorts[i] == sortType) {
                curIndex = i;
                break;
            }
        }

        curIndex++;

        if (curIndex == sorts.length) {
            curIndex = 0;
        }

        changeSort(sorts[curIndex]);
    }

    private void onToggleSortAscending() {
        MessagingController.getInstance(getApplication()).setSortAscending(sortType, !sortAscending);

        sortAscending = MessagingController.getInstance(getApplication()).isSortAscending(sortType);
        sortDateAscending = MessagingController.getInstance(getApplication()).isSortAscending(SORT_TYPE.SORT_DATE);

        reSort();
    }

    private void onDelete(MessageInfoHolder holder, int position) {
        if (holder.read == false && holder.folder.unreadMessageCount > 0) {
            holder.folder.unreadMessageCount--;
        }

        FolderInfoHolder trashHolder = mAdapter.getFolder(mAccount.getTrashFolderName());

        if (trashHolder != null) {
            trashHolder.needsRefresh = true;
        }

        mAdapter.removeMessage(holder);
        mListView.setSelection(position);
        MessagingController.getInstance(getApplication()).deleteMessage(mAccount, holder.message.getFolder().getName(), holder.message, null);

    }


    private void onMove(MessageInfoHolder holder) {
        if (MessagingController.getInstance(getApplication()).isMoveCapable(mAccount) == false) {
            return;
        }

        if (MessagingController.getInstance(getApplication()).isMoveCapable(holder.message) == false) {
            Toast toast = Toast.makeText(this, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG);
            toast.show();
            return;
        }

        Intent intent = new Intent(this, ChooseFolder.class);

        intent.putExtra(ChooseFolder.EXTRA_ACCOUNT, mAccount);
        intent.putExtra(ChooseFolder.EXTRA_CUR_FOLDER, holder.folder.name);
        intent.putExtra(ChooseFolder.EXTRA_MESSAGE_UID, holder.message.getUid());
        startActivityForResult(intent, ACTIVITY_CHOOSE_FOLDER_MOVE);
    }

    private void onCopy(MessageInfoHolder holder) {
        if (MessagingController.getInstance(getApplication()).isCopyCapable(mAccount) == false) {
            return;
        }

        if (MessagingController.getInstance(getApplication()).isCopyCapable(holder.message) == false) {
            Toast toast = Toast.makeText(this, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG);
            toast.show();
            return;
        }

        Intent intent = new Intent(this, ChooseFolder.class);

        intent.putExtra(ChooseFolder.EXTRA_ACCOUNT, mAccount);
        intent.putExtra(ChooseFolder.EXTRA_CUR_FOLDER, holder.folder.name);
        intent.putExtra(ChooseFolder.EXTRA_MESSAGE_UID, holder.message.getUid());
        startActivityForResult(intent, ACTIVITY_CHOOSE_FOLDER_COPY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK)
            return;

        switch (requestCode) {
        case ACTIVITY_CHOOSE_FOLDER_MOVE:
        case ACTIVITY_CHOOSE_FOLDER_COPY:
            if (data == null)
                return;

            String destFolderName = data.getStringExtra(ChooseFolder.EXTRA_NEW_FOLDER);

            String srcFolderName = data.getStringExtra(ChooseFolder.EXTRA_CUR_FOLDER);

            String uid = data.getStringExtra(ChooseFolder.EXTRA_MESSAGE_UID);

            FolderInfoHolder srcHolder = mAdapter.getFolder(srcFolderName);

            FolderInfoHolder destHolder = mAdapter.getFolder(destFolderName);

            if (srcHolder != null && destHolder != null) {
                MessageInfoHolder m = mAdapter.getMessage( uid);

                if (m != null) {
                    switch (requestCode) {
                    case ACTIVITY_CHOOSE_FOLDER_MOVE:
                        onMoveChosen(m, destHolder);

                        break;

                    case ACTIVITY_CHOOSE_FOLDER_COPY:
                        onCopyChosen(m, destHolder);

                        break;
                    }
                }
            }
        }
    }


    private void onMoveChosen(MessageInfoHolder holder, FolderInfoHolder folder) {
        if (MessagingController.getInstance(getApplication()).isMoveCapable(mAccount) == false) {
            return;
        }

//    String destFolderName = folder.name;
//    FolderInfoHolder destHolder = mAdapter.getFolder(destFolderName);
//
        if (folder == null) {
            return;
        }

        if (holder.read == false) {
            if (holder.folder.unreadMessageCount > 0) {
                holder.folder.unreadMessageCount--;
            }

            folder.unreadMessageCount++;
        }

        folder.needsRefresh = true;

        mAdapter.removeMessage(holder);
        MessagingController.getInstance(getApplication()).moveMessage(mAccount, holder.message.getFolder().getName(), holder.message, folder.name, null);

    }


    private void onCopyChosen(MessageInfoHolder holder, FolderInfoHolder folder) {
        if (MessagingController.getInstance(getApplication()).isCopyCapable(mAccount) == false) {
            return;
        }

        if (folder == null) {
            return;
        }

        if (holder.read == false) {
            folder.unreadMessageCount++;
        }

        folder.needsRefresh = true;

        MessagingController.getInstance(getApplication()).copyMessage(mAccount,
                holder.message.getFolder().getName(), holder.message, folder.name, null);
    }


    private void onReply(MessageInfoHolder holder) {
        MessageCompose.actionReply(this, mAccount, holder.message, false);
    }

    private void onReplyAll(MessageInfoHolder holder) {
        MessageCompose.actionReply(this, mAccount, holder.message, true);
    }

    private void onForward(MessageInfoHolder holder) {
        MessageCompose.actionForward(this, mAccount, holder.message);
    }


    private Account mSelectedContextAccount = null;
    private FolderInfoHolder mSelectedContextFolder = null;
    private void onMarkAllAsRead(final Account account, final FolderInfoHolder folder) {
        mSelectedContextAccount = account;
        mSelectedContextFolder = folder;
        showDialog(DIALOG_MARK_ALL_AS_READ);
    }

    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_MARK_ALL_AS_READ:
            return createMarkAllAsReadDialog();
        }

        return super.onCreateDialog(id);
    }

    public void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
        case DIALOG_MARK_ALL_AS_READ:
            ((AlertDialog)dialog).setMessage(getString(R.string.mark_all_as_read_dlg_instructions_fmt,
                                             mSelectedContextFolder.displayName));

            break;

        default:
            super.onPrepareDialog(id, dialog);
        }
    }

    private Dialog createMarkAllAsReadDialog() {
        return new AlertDialog.Builder(this)
               .setTitle(R.string.mark_all_as_read_dlg_title)
               .setMessage(getString(R.string.mark_all_as_read_dlg_instructions_fmt,
                                     mSelectedContextFolder.displayName))
               .setPositiveButton(R.string.okay_action, new DialogInterface.OnClickListener() {
                                      public void onClick(DialogInterface dialog, int whichButton) {
                                          dismissDialog(DIALOG_MARK_ALL_AS_READ);

                                          try {

                                              MessagingController.getInstance(getApplication()).markAllMessagesRead(mSelectedContextAccount, mSelectedContextFolder.name);

                                              for (MessageInfoHolder holder : mSelectedContextFolder.messages) {
                                                  holder.read = true;
                                              }

                                              mSelectedContextFolder.unreadMessageCount = 0;

                                              mHandler.dataChanged();


                                          } catch (Exception e) {
                                              // Ignore
                                          }
                                      }
                                  })

               .setNegativeButton(R.string.cancel_action, new DialogInterface.OnClickListener() {
                                      public void onClick(DialogInterface dialog, int whichButton) {
                                          dismissDialog(DIALOG_MARK_ALL_AS_READ);
                                      }
                                  })

               .create();
    }

    private void onToggleRead(MessageInfoHolder holder) {

        holder.folder.unreadMessageCount += (holder.read ? 1 : -1);

        if (holder.folder.unreadMessageCount < 0) {
            holder.folder.unreadMessageCount = 0;
        }

        MessagingController.getInstance(getApplication()).markMessageRead(mAccount, holder.message.getFolder().getName(), holder.uid, !holder.read);
        holder.read = !holder.read;
        mHandler.dataChanged();
    }

    private void onToggleFlag(MessageInfoHolder holder) {

        MessagingController.getInstance(getApplication()).setMessageFlag(mAccount, holder.message.getFolder().getName(), holder.uid, Flag.FLAGGED, !holder.flagged);
        holder.flagged = !holder.flagged;
        mHandler.dataChanged();
    }

//    private void checkMail(final Account account) {
//        MessagingController.getInstance(getApplication()).checkMail(this, account, true, true, mAdapter.mListener);
//    }

    private void checkMail(Account account, String folderName) {
        MessagingController.getInstance(getApplication()).synchronizeMailbox( account, folderName, mAdapter.mListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.check_mail:
            checkMail(mAccount, mFolderName);
            return true;

        case R.id.compose:
            onCompose();

            return true;

        case R.id.set_sort_date:
            changeSort(SORT_TYPE.SORT_DATE);

            return true;

        case R.id.set_sort_subject:
            changeSort(SORT_TYPE.SORT_SUBJECT);

            return true;

        case R.id.set_sort_sender:
            changeSort(SORT_TYPE.SORT_SENDER);

            return true;

        case R.id.set_sort_flag:
            changeSort(SORT_TYPE.SORT_FLAGGED);

            return true;

        case R.id.set_sort_unread:
            changeSort(SORT_TYPE.SORT_UNREAD);

            return true;

        case R.id.set_sort_attach:
            changeSort(SORT_TYPE.SORT_ATTACHMENT);

            return true;

        case R.id.reverse_sort:
            onToggleSortAscending();

            return true;

        case R.id.list_folders:
            onShowFolderList();
            
            return true;

        case R.id.mark_all_as_read:
            MessagingController.getInstance(getApplication()).markAllMessagesRead(mAccount, mFolderName);

            return true;

        case R.id.folder_settings:
            FolderSettings.actionSettings(this, mAccount, mFolderName);

            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.message_list_option, menu);
        return true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item .getMenuInfo();
        MessageInfoHolder holder = (MessageInfoHolder) mAdapter.getItem(info.position);

            switch (item.getItemId()) {
            case R.id.open:
                onOpenMessage(holder);

                break;

            case R.id.delete:
                onDelete(holder, info.position);

                break;

            case R.id.reply:
                onReply(holder);

                break;

            case R.id.reply_all:
                onReplyAll(holder);

                break;

            case R.id.forward:
                onForward(holder);

                break;

            case R.id.mark_as_read:
                onToggleRead(holder);

                break;

            case R.id.flag:
                onToggleFlag(holder);

                break;

            case R.id.move:
                onMove(holder);

                break;

            case R.id.copy:
                onCopy(holder);

                break;

            case R.id.send_alternate:
                onSendAlternate(mAccount, holder);

                break;

            }

        return super.onContextItemSelected(item);
    }

    public void onSendAlternate(Account account, MessageInfoHolder holder) {
        MessagingController.getInstance(getApplication()).sendAlternate(this, account, holder.message);
    }

    public void showProgressIndicator(boolean status) {
        setProgressBarIndeterminateVisibility(status);
        ProgressBar bar = (ProgressBar)mListView.findViewById( R.id.message_list_progress);
        if (bar == null) {
            return;
        }   

        bar.setIndeterminate(true);
        if (status) {
            bar.setVisibility(bar.VISIBLE);
        } else {
            bar.setVisibility(bar.INVISIBLE);

        }
    }

    @Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
    
    
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        MessageInfoHolder message = (MessageInfoHolder) mAdapter.getItem(info.position );
    
        if (message == null) {
            return;
        }

        getMenuInflater().inflate(R.menu.message_list_context, menu);
   
        menu.setHeaderTitle((CharSequence) message.subject);

        if (message.read) {
            menu.findItem(R.id.mark_as_read).setTitle( R.string.mark_as_unread_action);
        }
    
        if (message.flagged) {
            menu.findItem(R.id.flag).setTitle( R.string.unflag_action);
        }
    
        if (MessagingController.getInstance(getApplication()).isCopyCapable(mAccount) == false) {
            menu.findItem(R.id.copy).setVisible(false);
        }
    
        if (MessagingController.getInstance(getApplication()).isMoveCapable(mAccount) == false) {
            menu.findItem(R.id.move).setVisible(false);
        }
    }
    
    private String truncateStatus(String mess) {
        if (mess != null && mess.length() > 27) {
            mess = mess.substring(0, 27);
        }

        return mess;
    }

    class MessageListAdapter extends BaseAdapter {
        private ArrayList<MessageInfoHolder> messages = new ArrayList<MessageInfoHolder>();

        private MessagingListener mListener = new MessagingListener() {

            @Override
			public void synchronizeMailboxStarted(Account account, String folder) {
				if (!account.equals(mAccount) || !folder.equals(mFolderName)) {
                    return;
                }

                mHandler.progress(true);
            }

            @Override
			public void synchronizeMailboxNewMessage(Account account, String folder, Message message) {
				if (!account.equals(mAccount) || !folder.equals(mFolderName)) {
                    return;
                }

                addOrUpdateMessage(folder, message, true, true);
            }
            
            @Override
            public void synchronizeMailboxRemovedMessage(Account account, String folder,Message message) {
                removeMessage(getMessage( message.getUid()));
            }

            @Override
            public void synchronizeMailboxFinished(Account account, String folder,
                int totalMessagesInMailbox, int numNewMessages)  {
				if (!account.equals(mAccount) || !folder.equals(mFolderName)) {
                    return;
                }

                mHandler.progress(false);
            }

            @Override
            public void synchronizeMailboxFailed(Account account, String folder, String message) {
				if (!account.equals(mAccount) || !folder.equals(mFolderName)) {
                    return;
                }

                Toast.makeText(MessageList.this, message, Toast.LENGTH_LONG).show();
            }

            @Override
			public void listLocalMessagesStarted(Account account, String folder) {
				if (!account.equals(mAccount)) {
                    return;
                }
                mHandler.progress(true);
                mHandler.folderLoading(folder, true);
            }

            @Override
			public void listLocalMessagesFailed(Account account, String folder, String message) {
				if (!account.equals(mAccount)) {
                    return;
                }
              
                Collections.sort(mAdapter.messages);
                mHandler.dataChanged();
                mHandler.progress(false);
                mHandler.folderLoading(folder, false);
            }

            @Override
			public void listLocalMessagesFinished(Account account, String folder) {
				if (!account.equals(mAccount)) {
                    return;
                }

                Collections.sort(mAdapter.messages);
                mHandler.dataChanged();

                mHandler.progress(false);
                mHandler.folderLoading(folder, false);
            }

            @Override
			public void listLocalMessages(Account account, String folder, Message[] messages) {
				if (!account.equals(mAccount)) {
                    return;
                }
        
                if (folder != mFolderName) {
                    return;
                }
                
                //synchronizeMessages(folder, messages);
            }
            @Override
            public void listLocalMessagesRemoveMessage(Account account, String folder,Message message) {
				if (!account.equals(mAccount) || !folder.equals(mFolderName)) {
                    return;
                }
                MessageInfoHolder holder = getMessage(message.getUid());
                if (holder != null ) {
                    removeMessage(getMessage( message.getUid()));
                }
            } 


            @Override
			public void listLocalMessagesAddMessage(Account account, String folder, Message message) {
				if (!account.equals(mAccount) || !folder.equals(mFolderName)) {
                    return;
                }

                addOrUpdateMessage(folder, message, false, false);//true, true);
                if (mAdapter.messages.size() % 10 == 0 ) { 
                    Collections.sort(mAdapter.messages);
                    mHandler.dataChanged();
                }

            }
            
            @Override
			public void listLocalMessagesUpdateMessage(Account account, String folder, Message message) {
				if (!account.equals(mAccount) || !folder.equals(mFolderName)) {
                    return;
                }

                addOrUpdateMessage(folder, message, false, true);
            }

        };
        
        private Drawable mAttachmentIcon;
        private Drawable mAnsweredIcon;
        private View footerView = null;

        MessageListAdapter() {
            mAttachmentIcon = getResources().getDrawable( R.drawable.ic_mms_attachment_small);
            mAnsweredIcon = getResources().getDrawable( R.drawable.ic_mms_answered_small);
        }

        public void removeDirtyMessages() {
            Iterator<MessageInfoHolder> iter = messages.iterator();
                while(iter.hasNext()) {
                    MessageInfoHolder message = iter.next();
                    if (message.dirty) {
                        iter.remove();
                }
            }
            notifyDataSetChanged();
         }

        public void removeMessage(MessageInfoHolder holder) {
            if (holder.folder == null) {
                return;
            }


            if (holder == null) {
                return;
            }

            mAdapter.messages.remove(holder);
            mHandler.removeMessage(holder);
            Collections.sort(mAdapter.messages);
            mHandler.dataChanged();

        }

        public void synchronizeMessages(String folder, Message[] messages) {
            FolderInfoHolder f = getFolder(folder);

            if (f == null) {
                return;
            }

            mHandler.synchronizeMessages(f, messages);
        }

        public void addOrUpdateMessage(String folder, Message message) {
            addOrUpdateMessage(folder, message, true, true);
        }

        private void addOrUpdateMessage(FolderInfoHolder folder, Message message, boolean sort, boolean notify) {

            MessageInfoHolder m = getMessage( message.getUid());

            if (m == null) {
                m = new MessageInfoHolder(message, folder);
                mAdapter.messages.add(m);
            } else {
                m.populate(message, folder);
            }

            if (sort) {
                Collections.sort(mAdapter.messages);
            }

            if (notify) {
                mHandler.dataChanged();
            }
        }

        private void addOrUpdateMessage(String folder, Message message, boolean sort, boolean notify) {
            FolderInfoHolder f = getFolder(folder);

            if (f == null) {
                return;
            }

            addOrUpdateMessage(f, message, sort, notify);
        }

        // XXX TODO - make this not use a for loop
        public MessageInfoHolder getMessage( String messageUid) {
            MessageInfoHolder searchHolder = new MessageInfoHolder();
            searchHolder.uid = messageUid;
            int index = mAdapter.messages.indexOf((Object) searchHolder);
            if (index >= 0) {
                return (MessageInfoHolder)mAdapter.messages.get( index );
            } 
            return null;
        }

        public FolderInfoHolder getFolder(String folder) {
           try {
             LocalStore localStore = (LocalStore)Store.getInstance( mAccount.getLocalStoreUri(), getApplication());
            LocalFolder local_folder = localStore.getFolder(folder);
            FolderInfoHolder holder =   new FolderInfoHolder ((Folder)local_folder);
                     return holder;
            } catch (Exception e) {
                Log.e(Email.LOG_TAG, "getFolder(" + folder + ") goes boom: ",e);
                return null;
        }
        }

        private static final int NON_MESSAGE_ITEMS = 1;
        public int getCount() {
            if (mAdapter.messages == null || mAdapter.messages.size() == 0) {
                return NON_MESSAGE_ITEMS ;
            }

            return mAdapter.messages.size() +NON_MESSAGE_ITEMS  ;
        }

        public long getItemId(int position) {
                try {
                    MessageInfoHolder messageHolder =(MessageInfoHolder) getItem(position);
                    if (messageHolder != null) {
                        return ((LocalStore.LocalMessage)  messageHolder.message).getId();
                     }
                } catch ( Exception e) {
                    Log.i(Email.LOG_TAG,"getItemId("+position+") ",e);
                }
            return -1;
        }

        public Object getItem(long position) {
            return getItem((int)position);
        }

        public Object getItem(int position) {
            try {
                if (position < mAdapter.messages.size())  {
                    return mAdapter.messages.get(position);
                }
            } catch (Exception e) {
                Log.e(Email.LOG_TAG, "getItem(" + position + "), but folder.messages.size() = " + mAdapter.messages.size(), e);
            }
                return null;
        }

         public View getView(int position, View convertView, ViewGroup parent) {
            
            if (position == mAdapter.messages.size()  ) {
                return getFooterView(position, convertView, parent);
            } else { 
               return  getItemView(position, convertView, parent);
            }
        }
       

       public View getItemView(int position, View convertView, ViewGroup parent) {
            MessageInfoHolder message = (MessageInfoHolder) getItem(position);
            View view;
           
            if ((convertView != null) && (convertView.getId() == R.layout.message_list_item)) {
                view = convertView;
            } else {
                view = mInflater.inflate(R.layout.message_list_item, parent, false);
                view.setId(R.layout.message_list_item);
            }
            
            
            MessageViewHolder holder = (MessageViewHolder) view.getTag();

            if (holder == null) {
                holder = new MessageViewHolder();
                holder.subject = (TextView) view.findViewById(R.id.subject);
                holder.from = (TextView) view.findViewById(R.id.from);
                holder.date = (TextView) view.findViewById(R.id.date);
                holder.chip = view.findViewById(R.id.chip);
                holder.chip.setBackgroundResource(colorChipResId);
                view.setTag(holder);
            }

            if (message != null) {
                holder.chip.getBackground().setAlpha(message.read ? 0 : 255);
                holder.subject.setTypeface(null, message.read && !message.flagged ? Typeface.NORMAL  : Typeface.BOLD);

                int subjectColor = holder.from.getCurrentTextColor();  // Get from another field that never changes color 

                if (message.flagged) {
                    subjectColor = Email.FLAGGED_COLOR;
                } 

                if (message.downloaded) {
                    holder.chip.getBackground().setAlpha(message.read ? 0 : 127);
                    view.getBackground().setAlpha(0);
                } else {
                    view.getBackground().setAlpha(127);
                }

                holder.subject.setTextColor( 0xff000000 | subjectColor);
                holder.subject.setText(message.subject);

                holder.from.setText(message.sender);
                holder.from.setTypeface(null, message.read ? Typeface.NORMAL : Typeface.BOLD);
                holder.date.setText(message.date);
                holder.subject.setCompoundDrawablesWithIntrinsicBounds(
                    message.answered ? mAnsweredIcon : null, // left
                    null, // top
                    message.hasAttachments ? mAttachmentIcon : null, // right
                    null); // bottom
            } else {
                holder.chip.getBackground().setAlpha(0);
                holder.subject.setText("No subject");
                holder.subject.setTypeface(null, Typeface.NORMAL);
                holder.from.setText("No sender");
                holder.from.setTypeface(null, Typeface.NORMAL);
                holder.date.setText("No date");
                holder.from.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            }
            return view;
        }

        public View getFooterView(int position, View convertView, ViewGroup parent) {
            if (footerView == null) {
                footerView = mInflater.inflate(R.layout.message_list_item_footer, parent, false);
                footerView.setId(R.layout.message_list_item_footer);
                FooterViewHolder holder = new FooterViewHolder();
                holder.progress = (ProgressBar)footerView.findViewById(R.id.message_list_progress);
                holder.main = (TextView)footerView.findViewById(R.id.main_text);
                footerView.setTag(holder);
            }

            FooterViewHolder holder = (FooterViewHolder)footerView.getTag();
            if (mCurrentFolder.loading) {
                holder.main.setText(getString(R.string.status_loading_more));
                mHandler.progress(true);
            } else {
                if (mCurrentFolder.lastCheckFailed == false) {
                    holder.main.setText(String.format(getString(R.string.load_more_messages_fmt).toString(), mAccount.getDisplayCount()));
                } else {
                    holder.main.setText(getString(R.string.status_loading_more_failed));
                }
            }

            return footerView;
        }

        public boolean hasStableIds() {
            return true;
        }

        public boolean isItemSelectable(int position) {
            if ( position < mAdapter.messages.size() ) {
                return true;
            } else {
                return false;
            }
        }

    }

        public class MessageInfoHolder implements Comparable<MessageInfoHolder> {
            public String subject;

            public String date;

            public Date compareDate;

            public String compareSubject;

            public String sender;

            public String compareCounterparty;

            public String[] recipients;

            public boolean hasAttachments;

            public String uid;

            public boolean read;

            public boolean dirty;

            public boolean answered;

            public boolean flagged;

            public boolean downloaded;

            public boolean partially_downloaded;

            public Message message;

            public FolderInfoHolder folder;


            // Empty constructor for comparison
            public MessageInfoHolder() {}

            public MessageInfoHolder(Message m, FolderInfoHolder folder) {
                populate(m, folder);
            }

            public void populate(Message m, FolderInfoHolder folder) {
                try {
                    LocalMessage message = (LocalMessage) m;
                    Date date = message.getSentDate();
                    this.compareDate = date;
                    this.folder = folder;

                    this.dirty = false;


                    if (Utility.isDateToday(date)) {
                        this.date = getTimeFormat().format(date);
                    } else {
                        this.date = getDateFormat().format(date);
                    }

                    this.hasAttachments = message.getAttachmentCount() > 0;

                    this.read = message.isSet(Flag.SEEN);
                    this.answered = message.isSet(Flag.ANSWERED);
                    this.flagged = message.isSet(Flag.FLAGGED);
                    this.downloaded = message.isSet(Flag.X_DOWNLOADED_FULL);
                    this.partially_downloaded = message.isSet(Flag.X_DOWNLOADED_PARTIAL);

                    Address[] addrs = message.getFrom();

                    if (addrs.length > 0 && mAccount.isAnIdentity(addrs[0])) {
                        this.compareCounterparty = Address.toFriendly(message .getRecipients(RecipientType.TO));
                        this.sender = String.format(getString(R.string.message_list_to_fmt), this.compareCounterparty);
                    } else {
                        this.sender = Address.toFriendly(addrs);
                        this.compareCounterparty = this.sender;
                    }

                    this.subject = message.getSubject();

                    this.uid = message.getUid();
                    this.message = m;
                } catch (MessagingException me) {
                    if (Config.LOGV) {
                        Log.v(Email.LOG_TAG, "Unable to load message info", me);
                    }
                }
            }
            
            public boolean equals(Object o) {
                if (this.uid.equals(((MessageInfoHolder)o).uid)) {
                    return true;
                } else {
                    return false;
                }
            }


            public int compareTo(MessageInfoHolder o) {
                int ascender = (sortAscending ? 1 : -1);
                int comparison = 0;

                if (sortType == SORT_TYPE.SORT_SUBJECT) {
                    if (compareSubject == null) {
                        compareSubject = stripPrefixes(subject).toLowerCase();
                    }

                    if (o.compareSubject == null) {
                        o.compareSubject = stripPrefixes(o.subject).toLowerCase();
                    }

                    comparison = this.compareSubject.compareTo(o.compareSubject);
                } else if (sortType == SORT_TYPE.SORT_SENDER) {
                    comparison = this.compareCounterparty.toLowerCase().compareTo(o.compareCounterparty.toLowerCase());
                } else if (sortType == SORT_TYPE.SORT_FLAGGED) {
                    comparison = (this.flagged ? 0 : 1) - (o.flagged ? 0 : 1);

                } else if (sortType == SORT_TYPE.SORT_UNREAD) {
                    comparison = (this.read ? 1 : 0) - (o.read ? 1 : 0);
                } else if (sortType == SORT_TYPE.SORT_ATTACHMENT) {
                    comparison = (this.hasAttachments ? 0 : 1) - (o.hasAttachments ? 0 : 1);

                }

                if (comparison != 0) {
                    return comparison * ascender;
                }

                int dateAscender = (sortDateAscending ? 1 : -1);


                return this.compareDate.compareTo(o.compareDate) * dateAscender;
            }

            Pattern pattern = null;
            String patternString = "^ *(re|fw|fwd): *";
            private String stripPrefixes(String in) {
                synchronized (patternString) {
                    if (pattern == null) {
                        pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
                    }
                }

                Matcher matcher = pattern.matcher(in);

                int lastPrefix = -1;

                while (matcher.find()) {
                    lastPrefix = matcher.end();
                }

                if (lastPrefix > -1 && lastPrefix < in.length() - 1) {
                    return in.substring(lastPrefix);
                } else {
                    return in;
                }
            }

        }

        class MessageViewHolder {
            public TextView subject;
            public TextView preview;
            public TextView from;
            public TextView date;
            public View chip;
        }

        class FooterViewHolder {
            public ProgressBar progress;
            public TextView main;
        }

      /* THERE IS NO FUCKING REASON THIS IS CLONED HERE */
    public class FolderInfoHolder implements Comparable<FolderInfoHolder> {
            public String name;

            public String displayName;

            public ArrayList<MessageInfoHolder> messages;

            public long lastChecked;

            public int unreadMessageCount;

            public boolean loading;

            public String status;

            public boolean lastCheckFailed;

            public boolean needsRefresh = false;

            /**
             * Outbox is handled differently from any other folder.
             */
            public boolean outbox;

            public int compareTo(FolderInfoHolder o) {
                String s1 = this.name;
                String s2 = o.name;

                if (Email.INBOX.equalsIgnoreCase(s1)) {
                    return -1;
                } else if (Email.INBOX.equalsIgnoreCase(s2)) {
                    return 1;
                } else
                    return s1.compareToIgnoreCase(s2);
            }
           
             public FolderInfoHolder(Folder folder) {
                    populate(folder);
             }
             public void populate (Folder folder) {  
                      int unreadCount = 0;
    
                      try {
                          folder.open(Folder.OpenMode.READ_WRITE);
                          unreadCount = folder.getUnreadMessageCount();
                      } catch (MessagingException me) {
                          Log.e(Email.LOG_TAG, "Folder.getUnreadMessageCount() failed", me);
                      }
    
                      this.name = folder.getName();
    
                      if (this.name.equalsIgnoreCase(Email.INBOX)) {
                          this.displayName = getString(R.string.special_mailbox_name_inbox);
                      } else {
                          this.displayName = folder.getName();
                      }
    
                      if (this.name.equals(mAccount.getOutboxFolderName())) {
                          this.displayName = String.format( getString(R.string.special_mailbox_name_outbox_fmt), this.name);
                          this.outbox = true;
                      }
    
                      if (this.name.equals(mAccount.getDraftsFolderName())) {
                          this.displayName = String.format( getString(R.string.special_mailbox_name_drafts_fmt), this.name);
                      }
    
                      if (this.name.equals(mAccount.getTrashFolderName())) {
                          this.displayName = String.format( getString(R.string.special_mailbox_name_trash_fmt), this.name);
                      }
    
                      if (this.name.equals(mAccount.getSentFolderName())) {
                          this.displayName = String.format( getString(R.string.special_mailbox_name_sent_fmt), this.name);
                      }
    
                      if (this.messages == null) {
                          this.messages = new ArrayList<MessageInfoHolder>();
                      }
    
                      this.lastChecked = folder.getLastChecked();
    
                      String mess = truncateStatus(folder.getStatus());
    
                      this.status = mess;
    
                      this.unreadMessageCount = unreadCount;
    
                      try {
                          folder.close(false);
                      } catch (MessagingException me) {
                          Log.e(Email.LOG_TAG, "Folder.close() failed", me);
                      }
                  }
        }

}
