package com.android.email.activity;

import com.android.email.Account;
import com.android.email.Email;
import com.android.email.Preferences;

import com.android.email.K9Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * The Welcome activity initializes the application and decides what Activity
 * the user should start with.
 * If no accounts are configured the user is taken to the Accounts Activity where they
 * can configure an account.
 * If a single account is configured the user is taken directly to the MessageList for
 * the INBOX of that account.
 * If more than one account is configuref the user is takaen to the Accounts Activity so they
 * can select an account.
 */
public class Welcome extends K9Activity {
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Accounts.actionLaunch(this);
        finish();
    }
}
