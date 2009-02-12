package com.android.email.activity;

import android.content.Context;

import com.android.email.R;

public class SizeFormatter
{
  public static String formatSize(Context context, long size)
  {
    if (size > 1024000000)
    {
      return ((float)(size / 102400000) / 10) + context.getString(R.string.abbrev_gigabytes);
    }
    if (size > 1024000)
    {
      return ((float)(size / 102400) / 10) + context.getString(R.string.abbrev_megabytes);
    }
    if (size > 1024)
    {
      return ((float)(size / 102) / 10) + context.getString(R.string.abbrev_kilobytes);
    }
    return size + context.getString(R.string.abbrev_bytes);
  }
  
}
