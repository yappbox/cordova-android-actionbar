// Copyright (C) 2013 Polychrom Pty Ltd
//
// This program is licensed under the 3-clause "Modified" BSD license,
// see LICENSE file for full definition.

package com.polychrom.cordova;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.Window;

import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**! A naive ActionBar/Menu plugin for Cordova/Android.
 * 
 * @author Mitchell Wheeler
 *
 *	Wraps the bare essentials of ActionBar and the options menu to appropriately populate the ActionBar in it's various forms.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class ActionBarPlugin extends CordovaPlugin
{
	JSONArray menu_definition = null;
	Menu menu = null;
	HashMap<MenuItem, String> menu_callbacks = new HashMap<MenuItem, String>();

	HashMap<Integer, ActionBar.Tab> tabs = new HashMap<Integer, ActionBar.Tab>();
	HashMap<MenuItem, String> tab_callbacks = new HashMap<MenuItem, String>();
	
	// A set of base paths to check for relative paths from
	String bases[];
	
	@Override
	public Object onMessage(String id, Object data)
	{
		if("onCreateOptionsMenu".equals(id) || "onPrepareOptionsMenu".equals(id))
		{
			menu = (Menu)data;

			if(menu_definition != null && menu.size() != menu_definition.length())
			{
				menu.clear();
				menu_callbacks.clear();
				buildMenu(menu, menu_definition);
			}
		}
		else if("onOptionsItemSelected".equals(id))
		{
			MenuItem item = (MenuItem)data;
			if(item.getItemId() == android.R.id.home)
			{
				webView.sendJavascript("if(window.plugins.actionbar.home_callback) window.plugins.actionbar.home_callback();");
			}
			else if(menu_callbacks.containsKey(item))
			{
				final String callback = menu_callbacks.get(item);
				webView.sendJavascript(callback);
			}
		}
		
		return null;
	}
	
	private String removeFilename(String path)
	{
		if(!path.endsWith("/"))
		{
			path = path.substring(0, path.lastIndexOf('/')+1);
		}
		
		return path;
	}

	private Drawable getDrawableForURI(String uri_string)
	{
		Uri uri = Uri.parse(uri_string);
		Context ctx = cordova.getActivity();

		// Special case - TrueType fonts
		if(uri_string.endsWith(".ttf"))
		{
			/*for(String base: bases)
			{
				String path = base + uri;
				
				// TODO: Font load / glyph rendering ("/blah/fontawesome.ttf:\f1234")
			}*/
		}
		// General bitmap
		else
		{
			if(uri.isAbsolute())
			{
				if(uri.getScheme().startsWith("http"))
				{
					try
					{
						URL url = new URL(uri_string);
						InputStream stream = url.openConnection().getInputStream();
						return new BitmapDrawable(ctx.getResources(), stream);
					}
					catch (MalformedURLException e)
					{
						return null;
					}
					catch (IOException e)
					{
						return null;
					}
					catch (Exception e)
					{
						return null;
					}
				}
				else
				{
					try
					{
						InputStream stream = ctx.getContentResolver().openInputStream(uri);
						return new BitmapDrawable(ctx.getResources(), stream);
					}
					catch(FileNotFoundException e)
					{
						return null;
					}
				}
			}
			else
			{
				for(String base: bases)
				{
					String path = base + uri;
					
					// Asset
					if(base.startsWith("file:///android_asset/"))
					{
						path = path.substring(22);
						
						try
						{
							InputStream stream = ctx.getAssets().open(path);
							return new BitmapDrawable(ctx.getResources(), stream);
						}
						catch (IOException e)
						{
							continue;
						}
					}
					// General URI
					else
					{
						try
						{
							InputStream stream = ctx.getContentResolver().openInputStream(Uri.parse(path));
							return new BitmapDrawable(ctx.getResources(), stream);
						}
						catch(FileNotFoundException e)
						{
							continue;
						}
					}
				}
			}
		}

		return null;
	}
	
	/**! Build a menu from a JSON definition.
	 *
	 * Example definition:
	 * [{
	 * 	 icon: 'icons/new.png',
	 *	 text: 'New',
	 *	 click: function() { alert('Create something new!'); }
	 * },
	 * {
	 * 	 icon: 'icons/save.png',
	 *	 text: 'Save As',
	 *	 header: { icon: 'icons/save.png', title: 'Save file as...' },
	 *	 items: [
	 *	 		 {
	 *	 			icon: 'icons/png.png',
	 *	 			text: 'PNG',
	 *	 			click: function() { alert('save as png'); }
	 *	 		 },
	 *	 		 {
	 *	 			icon: 'icons/jpeg.png',
	 *	 			text: 'JPEG',
	 *	 			click: function() { alert('save as jpeg'); }
	 *	 		 }
	 *	 ]
	 * },
	 * {
	 * 	 icon: 'fonts/fontawesome.ttf?U+0040',
	 *	 text: 'Contact',
	 *	 show: SHOW_AS_ACTION_NEVER
	 * }]
	 * 
	 * Note: By default all menu items have the show flag SHOW_AS_ACTION_IF_ROOM
	 * 
	 * @param menu The menu to build the definition into
	 * @param definition The menu definition (see example above)
	 * @return true if the definition was valid, false otherwise.
	 */
	private boolean buildMenu(Menu menu, JSONArray definition)
	{
		return buildMenu(menu, definition, "window.plugins.actionbar.menu");
	}

	private boolean buildMenu(Menu menu, JSONArray definition, String menu_var)
	{
		// Sadly MenuItem.setIcon and SubMenu.setIcon have conficting return types (for chaining), thus this can't be done w/ generics :(
		class GetMenuItemIconTask extends AsyncTask<String, Void, Drawable>
		{
			public final MenuItem item;
			public Exception exception = null;
			
			GetMenuItemIconTask(MenuItem item)
			{
				this.item = item;
			}

			@Override
			protected Drawable doInBackground(String... uris)
			{
				return getDrawableForURI(uris[0]);
			}
			
			@Override
			protected void onPostExecute(Drawable icon)
			{
				if(icon != null)
				{
					item.setIcon(icon);
				}
			}
		};
		
		class GetSubMenuIconTask extends AsyncTask<String, Void, Drawable>
		{
			public final SubMenu item;
			public Exception exception = null;
			
			GetSubMenuIconTask(SubMenu item)
			{
				this.item = item;
			}

			@Override
			protected Drawable doInBackground(String... uris)
			{
				return getDrawableForURI(uris[0]);
			}
			
			@Override
			protected void onPostExecute(Drawable icon)
			{
				if(icon != null)
				{
					item.setIcon(icon);
				}
			}
		};
		
		try
		{
			for(int i = 0; i < definition.length(); ++i)
			{
				final JSONObject item_def = definition.getJSONObject(i);
				final String text = item_def.isNull("text")? "" : item_def.getString("text");

				if(!item_def.has("items"))
				{
					MenuItem item = menu.add(0, i, i, text);
					if(item_def.isNull("icon") == false)
					{
						GetMenuItemIconTask task = new GetMenuItemIconTask(item);
						
						synchronized(task)
						{
							task.execute(item_def.getString("icon"));
						}
					}

					// Default to MenuItem.SHOW_AS_ACTION_IF_ROOM, otherwise take user defined value.
					item.setShowAsAction(item_def.has("show")? item_def.getInt("show") : MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

					menu_callbacks.put(item, "var item = " + menu_var + "[" + i + "]; if(item.click) item.click();");
				}
				else
				{
					SubMenu submenu = menu.addSubMenu(0, i, i, text);
					if(item_def.isNull("icon") == false)
					{
						GetSubMenuIconTask task = new GetSubMenuIconTask(submenu);
						
						synchronized(task)
						{
							task.execute(item_def.getString("icon"));
						}
					}
					
					// Set submenu header
					if(item_def.has("header"))
					{
						JSONObject header = item_def.getJSONObject("header");

						if(header.has("title"))
						{
							submenu.setHeaderTitle(header.getString("title"));
						}
						
						if(header.has("icon"))
						{
							submenu.setHeaderIcon(getDrawableForURI(header.getString("icon")));
						}
					}
					
					// Build sub-menu
					buildMenu(submenu, item_def.getJSONArray("items"), menu_var + "[" + i + "].items");
				}
			}
		}
		catch (JSONException e)
		{
			return false;
		}

		return true;
	}

	
	/**! Build a tab bar from a JSON definition.
	 * 
	 * Example definition:
	 * [{
	 * 	   icon: 'icons/tab1_icon.png',
	 *	 text: 'Tab #1',
	 *	 select: function() { alert('View Tab #1!'); },
	 *	 reselect: function() { alert('Refresh Tab #1!'); },
	 *	 unselect: function() { alert('Hide Tab #1!'); }
	 * },
	 * {
	 * 	   icon: 'icons/tab2_icon.png',
	 *	 text: 'Tab #2',
	 *	 select: function() { alert('View Tab #2!'); },
	 *	 reselect: function() { alert('Refresh Tab #2!'); },
	 *	 unselect: function() { alert('Hide Tab #2!'); }
	 * }]
	 * 
	 * @param bar The action bar to build the definition into
	 * @param definition The tab bar definition (see example above)
	 * @return true if the definition was valid, false otherwise.
	 */
	private boolean buildTabs(ActionBar bar, JSONArray definition)
	{
		return buildTabs(bar, definition, "window.plugins.actionbar.tabs");
	}
	
	private boolean buildTabs(ActionBar bar, JSONArray definition, String menu_var)
	{
		try
		{
			for(int i = 0; i < definition.length(); ++i)
			{
				final JSONObject item_def = definition.getJSONObject(i);
				final String text = item_def.isNull("text")? "" : item_def.getString("text");
				final Drawable icon = item_def.isNull("icon")? null : getDrawableForURI(item_def.getString("icon"));

				bar.addTab(bar.newTab().setText(text).setIcon(icon).setTabListener(new TabListener(this, menu_var + "[" + i + "]")));
			}
		}
		catch (JSONException e)
		{
			return false;
		}

		return true;
	}

	private final static List<String> plugin_actions = Arrays.asList(new String[] {
		"isAvailable",
		"show", "hide", "isShowing", "getHeight",
		"setMenu", "setTabs",
		"setDisplayOptions", "getDisplayOptions",
		"setHomeButtonEnabled", "setIcon", "setLogo",
		"setDisplayShowHomeEnabled", "setDisplayHomeAsUpEnabled", "setDisplayShowTitleEnabled", "setDisplayUseLogoEnabled",
		"setNavigationMode", "getNavigationMode", "setSelectedNavigationItem", "getSelectedNavigationItem",
		"setTitle", "getTitle", "setSubtitle", "getSubtitle"
		
	});

	@Override
	public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException
	{
		if(!plugin_actions.contains(action))
		{
			return false;
		}

		if("isAvailable".equals(action))
		{
			JSONObject result = new JSONObject();
			result.put("value", cordova.getActivity().getWindow().hasFeature(Window.FEATURE_ACTION_BAR));
			callbackContext.success(result);
			return true;
		}

		final ActionBar bar = cordova.getActivity().getActionBar();
		if(bar == null)
		{
			Window window = cordova.getActivity().getWindow();
			if(!window.hasFeature(Window.FEATURE_ACTION_BAR))
			{
				callbackContext.error("ActionBar feature not available, Window.FEATURE_ACTION_BAR must be enabled!");
			}
			else
			{
				callbackContext.error("Failed to get ActionBar");
			}

			return true;
		}

		if(menu == null)
		{
			callbackContext.error("Options menu not initialised");
			return true;
		}

		// This is a bit of a hack (should be specific to the request, not global)
		bases = new String[]
		{
			removeFilename(webView.getOriginalUrl()),
			removeFilename(webView.getUrl())
		};

		final StringBuffer error = new StringBuffer();
		JSONObject result = new JSONObject();

		if("isShowing".equals(action))
		{
			result.put("value", bar.isShowing());
		}
		else if("getHeight".equals(action))
		{
			result.put("value", bar.getHeight());
		}
		else if("getDisplayOptions".equals(action))
		{
			result.put("value", bar.getDisplayOptions());
		}
		else if("getNavigationMode".equals(action))
		{
			result.put("value", bar.getNavigationMode());
		}
		else if("getSelectedNavigationItem".equals(action))
		{
			result.put("value", bar.getSelectedNavigationIndex());
		}
		else if("getSubtitle".equals(action))
		{
			result.put("value", bar.getSubtitle());
		}
		else if("getTitle".equals(action))
		{
			result.put("value", bar.getTitle());
		}
		else
		{
			try
			{
				JSONException exception = new Runnable()
				{
					public JSONException exception = null;
					
					public void run()
					{
						try
						{
							if("show".equals(action))
							{
								bar.show();
							}
							else if("hide".equals(action))
							{
								bar.hide();
							}
							else if("setMenu".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("menu can not be null");
									return;
								}

								menu_definition = args.getJSONArray(0);
								
								if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
								{
									cordova.getActivity().invalidateOptionsMenu();
								}
							}
							else if("setTabs".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("menu can not be null");
									return;
								}
					
								bar.removeAllTabs();
								tab_callbacks.clear();
					
								if(!buildTabs(bar, args.getJSONArray(0)))
								{
									error.append("Invalid tab bar definition");
								}
							}
							else if("setDisplayHomeAsUpEnabled".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("showHomeAsUp can not be null");
									return;
								}
					
								bar.setDisplayHomeAsUpEnabled(args.getBoolean(0));
							}
							else if("setDisplayOptions".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("options can not be null");
									return;
								}

								final int options = args.getInt(0);
								bar.setDisplayOptions(options);
							}
							else if("setDisplayShowHomeEnabled".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("showHome can not be null");
									return;
								}
					
								bar.setDisplayShowHomeEnabled(args.getBoolean(0));
							}
							else if("setDisplayShowTitleEnabled".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("showTitle can not be null");
									return;
								}
					
								bar.setDisplayShowTitleEnabled(args.getBoolean(0));
							}
							else if("setDisplayUseLogoEnabled".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("useLogo can not be null");
									return;
								}
					
								bar.setDisplayUseLogoEnabled(args.getBoolean(0));
							}
							else if("setHomeButtonEnabled".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("enabled can not be null");
									return;
								}
					
								bar.setHomeButtonEnabled(args.getBoolean(0));
							}
							else if("setIcon".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("icon can not be null");
									return;
								}
								
								Drawable drawable = getDrawableForURI(args.getString(0));
								bar.setIcon(drawable);
							}
							else if("setLogo".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("logo can not be null");
									return;
								}
								
								Drawable drawable = getDrawableForURI(args.getString(0));
								bar.setLogo(drawable);
							}
							else if("setNavigationMode".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("mode can not be null");
									return;
								}

								final int mode = args.getInt(0);
								bar.setNavigationMode(mode);
							}
							else if("setSelectedNavigationItem".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("position can not be null");
									return;
								}
					
								bar.setSelectedNavigationItem(args.getInt(0));
							}
							else if("setSubtitle".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("subtitle can not be null");
									return;
								}
					
								bar.setSubtitle(args.getString(0));
							}
							else if("setTitle".equals(action))
							{
								if(args.isNull(0))
								{
									error.append("title can not be null");
									return;
								}
					
								bar.setTitle(args.getString(0));
							}
						}
						catch (JSONException e)
						{
							exception = e;
						}
						finally
						{
							synchronized(this)
							{
								this.notify();
							}
						}
					}
					
					// Run task synchronously
					{
						synchronized(this)
						{
							cordova.getActivity().runOnUiThread(this);
							this.wait();
						}
					}
				}.exception;
				
				if(exception != null)
				{
					throw exception;
				}
			}
			catch (InterruptedException e)
			{
				error.append("Function interrupted on UI thread");
			}
		}
		
		if(error.length() == 0)
		{
			if(result.length() > 0)
			{
				callbackContext.success(result);
			}
			else
			{
				callbackContext.success();
			}
		}
		else
		{
			callbackContext.error(error.toString());
		}

		return true;
	}

	public static class TabListener implements ActionBar.TabListener
	{
		private ActionBarPlugin plugin;
		private String js_item;

		public TabListener(ActionBarPlugin plugin, String js_item)
		{
			this.plugin = plugin;
			this.js_item = js_item;
		}

		public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft)
		{
			String callback = "var item = " + js_item + "; if(item.select) item.select(item);";
			
			plugin.webView.sendJavascript(callback);
		}

		public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft)
		{
			String callback = "var item = " + js_item + "; if(item.unselect) item.unselect(item);";
			
			plugin.webView.sendJavascript(callback);
		}

		public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft)
		{
			String callback = "var item = " + js_item + "; if(item.reselect) item.reselect(item);";
			
			plugin.webView.sendJavascript(callback);
		}
	}
}
