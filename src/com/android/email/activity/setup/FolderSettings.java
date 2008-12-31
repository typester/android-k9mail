
package com.android.email.activity.setup;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.preference.PreferenceActivity;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.RingtonePreference;

import com.android.email.Account;
import com.android.email.Email;
import com.android.email.Preferences;
import com.android.email.R;
import com.android.email.mail.Folder;
import com.android.email.mail.MessagingException;
import com.android.email.mail.Store;
import com.android.email.mail.Folder.FolderClass;
import com.android.email.mail.store.LocalStore.LocalFolder;

public class FolderSettings extends PreferenceActivity {
	
    private static final String EXTRA_FOLDER_NAME = "com.android.email.folderName";
    private static final String EXTRA_ACCOUNT = "com.android.email.account";
    
    private static final String PREFERENCE_TOP_CATERGORY = "folder_settings";
    private static final String PREFERENCE_DISPLAY_CLASS = "folder_settings_folder_display_mode";
    private static final String PREFERENCE_SYNC_CLASS = "folder_settings_folder_sync_mode";

    private LocalFolder mFolder;
    
    private ListPreference mDisplayClass;
    private ListPreference mSyncClass;

    public static void actionSettings(Context context, Account account, String folderName) {
        Intent i = new Intent(context, FolderSettings.class);
        i.putExtra(EXTRA_FOLDER_NAME, folderName);
        i.putExtra(EXTRA_ACCOUNT, account);
        context.startActivity(i);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String folderName = (String)getIntent().getSerializableExtra(EXTRA_FOLDER_NAME);
        Account mAccount = (Account)getIntent().getSerializableExtra(EXTRA_ACCOUNT);
        
				try
				{
					Store localStore = Store.getInstance(mAccount.getLocalStoreUri(),
							getApplication());
					mFolder = (LocalFolder) localStore.getFolder(folderName);
					mFolder.refresh(Preferences.getPreferences(this));
				}
				catch (MessagingException me)
				{
					Log.e(Email.LOG_TAG, "Unable to edit folder " + folderName + " preferences", me);
					return;
				}

        addPreferencesFromResource(R.xml.folder_settings_preferences);

        Preference category = findPreference(PREFERENCE_TOP_CATERGORY);
        category.setTitle(getString(R.string.folder_settings_title));
       
        mDisplayClass = (ListPreference) findPreference(PREFERENCE_DISPLAY_CLASS);
        mDisplayClass.setValue(mFolder.getDisplayClass().name());
        mDisplayClass.setSummary(mDisplayClass.getEntry());
        mDisplayClass.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String summary = newValue.toString();
                int index = mDisplayClass.findIndexOfValue(summary);
                mDisplayClass.setSummary(mDisplayClass.getEntries()[index]);
                mDisplayClass.setValue(summary);
                return false;
            }
        });

        mSyncClass = (ListPreference) findPreference(PREFERENCE_SYNC_CLASS);
        mSyncClass.setValue(mFolder.getRawSyncClass().name());
        mSyncClass.setSummary(mSyncClass.getEntry());
        mSyncClass.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final String summary = newValue.toString();
                int index = mSyncClass.findIndexOfValue(summary);
                mSyncClass.setSummary(mSyncClass.getEntries()[index]);
                mSyncClass.setValue(summary);
                return false;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        try
        {
        	mFolder.refresh(Preferences.getPreferences(this));
        }
        catch (MessagingException me)
        {
        	Log.e(Email.LOG_TAG, "Could not refresh folder preferences for folder " + mFolder.getName(), me);
        }
    }

    private void saveSettings() {
    	mFolder.setDisplayClass(FolderClass.valueOf(mDisplayClass.getValue()));
     	mFolder.setSyncClass(FolderClass.valueOf(mSyncClass.getValue()));
        	
     	try
     	{
     		mFolder.save(Preferences.getPreferences(this));
     	}
      catch (MessagingException me)
      {
      	Log.e(Email.LOG_TAG, "Could not refresh folder preferences for folder " + mFolder.getName(), me);
      }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            saveSettings();
        }
        return super.onKeyDown(keyCode, event);
    }

 
}
