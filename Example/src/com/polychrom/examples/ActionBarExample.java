// Copyright (C) 2013 Polychrom Pty Ltd
//
// This program is licensed under the 3-clause "Modified" BSD license,
// see LICENSE file for full definition.

package com.polychrom.examples;

import java.util.concurrent.ExecutorService;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

import org.apache.cordova.*;
import org.apache.cordova.api.CordovaInterface;
import org.apache.cordova.api.CordovaPlugin;

// NOTE: cordova-android's DroidGap class currently prevents custom window features, thus we implement our own Activity (with full control) and embed Cordova instead.
public class ActionBarExample extends Activity implements CordovaInterface
{
    private CordovaPlugin activityResultCallback;
    private CordovaWebView cdv;

	@Override
    public void onCreate(Bundle savedInstanceState)
    {
		// Request action bar feature BEFORE onCreate (window features must be requested before the window is created)
		this.requestWindowFeature(Window.FEATURE_ACTION_BAR);
		//this.getWindow().setUiOptions(ActivityInfo.UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        cdv = (CordovaWebView)findViewById(R.id.cordova);
        cdv.loadUrl("file:///android_asset/www/index.html");
        
        // Artificially create our action bar (action bar won't be created until the first call to getActionBar)
        getActionBar();
    }

	@Override
	public boolean onCreateOptionsMenu (Menu menu)
	{
		cdv.postMessage("onCreateOptionsMenu", menu);
		super.onCreateOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu (Menu menu)
	{
		cdv.postMessage("onPrepareOptionsMenu", menu);
		super.onPrepareOptionsMenu(menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		cdv.postMessage("onOptionsItemSelected", item);
		super.onOptionsItemSelected(item);
		return true;
	}

    @Override
    public void setActivityResultCallback(CordovaPlugin plugin)
    {
        this.activityResultCallback = plugin;        
    }

    @Override
    public void startActivityForResult(CordovaPlugin command, Intent intent, int requestCode)
    {
        this.activityResultCallback = command;

        // Start activity
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        super.onActivityResult(requestCode, resultCode, intent);
        CordovaPlugin callback = this.activityResultCallback;
        if(callback != null)
        {
            callback.onActivityResult(requestCode, resultCode, intent);
        }
    }
    
    @Override
    public ExecutorService getThreadPool()
    {
        return getThreadPool();
    }

	@Override
	public Activity getActivity() { return this; }

	@Override
	public Object onMessage(String id, Object data) { return null; }

	@Override
	public Context getContext() { return this; }

	@Override
	public void cancelLoadUrl() {}
}

