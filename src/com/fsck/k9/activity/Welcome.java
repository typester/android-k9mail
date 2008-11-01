
package com.fsck.k9.activity;

import com.fsck.k9.Account;
import com.fsck.k9.k9;
import com.fsck.k9.Preferences;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * The Welcome activity initializes the application and decides what Activity
 * the user should start with.
 * If no accounts are configured the user is taken to the Accounts Activity where they
 * can configure an account.
 * If a single account is configured the user is taken directly to the FolderMessageList for
 * the INBOX of that account.
 * If more than one account is configuref the user is takaen to the Accounts Activity so they
 * can select an account.
 */
public class Welcome extends Activity {
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Account[] accounts = Preferences.getPreferences(this).getAccounts();
        if (accounts.length == 1) {
            FolderMessageList.actionHandleAccount(this, accounts[0], k9.INBOX);
        } else {
            startActivity(new Intent(this, Accounts.class));
        }
        
        finish();
    }
}
