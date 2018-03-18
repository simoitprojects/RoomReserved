package com.simosnipe.roomreserved;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

public class MainActivity extends Activity {

	private SharedPreferences preferences;
	
	public List<String[]> event_list; // event_list[0] = DTSTART, event_list[1] = DTEND, event_list[2] = TITLE, event_list[3] = ORGANIZER, event_list[4] = TRUE ( if current)
	public List<String> calendar_list;
	public String room_status = "FREE"; 
	public long next24h_miliseconds = 1000*60*60*24;
	
	private static final String TAG = "RoomReserved";
	
	//boolean has_menu_key = false;
	
	public class AppSettings {
		public long warning_time_miliseconds = 1000*60*15;
		public long refresh_time = 5000;
		public boolean fullscreen;
		public boolean keep_screen_on;
		public int base_font_size;
		public String room_name;
		public boolean event_element_title;
		public boolean event_element_organizer;
		public boolean event_element_time;
//		public Set<String> event_elements = new HashSet<String>();
	}
	
	private class RefreshTimerTask extends TimerTask {
		  @Override
		  public void run() {
			  runOnUiThread(new Runnable() {
				    public void run() {
				    	refresh_events();
				    }
				});
		  }
		}
	
    public Timer timer = new Timer();
    public RefreshTimerTask refresh_task = new RefreshTimerTask();
	
    public void set_refresh_timer(){
        refresh_task.cancel();
        timer.cancel();
        timer.purge();
        timer = new Timer();
        refresh_task = new RefreshTimerTask();
        timer.scheduleAtFixedRate(refresh_task, 5, app_settings.refresh_time);    	
    }
    
	public AppSettings app_settings = new AppSettings();
	
	public void update_preferences(SharedPreferences preferences){
		app_settings.keep_screen_on = preferences.getBoolean("keep_screen_on", false);
		app_settings.fullscreen = preferences.getBoolean("fullscreen", false);
        app_settings.refresh_time = Long.valueOf(preferences.getString("refresh_time", "5000")).longValue();
        app_settings.base_font_size = Integer.parseInt(preferences.getString("text_size", "10"));
        app_settings.warning_time_miliseconds = Long.valueOf(preferences.getString("warning_time", "900000")).longValue();
        app_settings.room_name = preferences.getString("room_name", "");
		app_settings.event_element_title = preferences.getBoolean("event_element_title", true);
		app_settings.event_element_organizer = preferences.getBoolean("event_element_organizer", true);
		app_settings.event_element_time = preferences.getBoolean("event_element_time", true);

		Log.i(TAG, "app_settings.event_elements" + app_settings.base_font_size);	
        check_keep_screen_on();
        check_fullscreen();
        set_room_name();
        refresh_events();
        set_refresh_timer();
	}
	
//CONSTANTS
	private static int green_color = 0xFF52FF52;
	private static int red_collor = 0xFFFF5252;
	private static int orange_collor = 0xFFFFCD00;
	
    
	public SharedPreferences.OnSharedPreferenceChangeListener settings_change_listener = new SharedPreferences.OnSharedPreferenceChangeListener() 
    {
		public void onSharedPreferenceChanged(SharedPreferences preferences, String key) 
  	  	{
			update_preferences(preferences);
			
			//Toast.makeText(MainActivity.this, "Settings changed.", Toast.LENGTH_LONG).show();
			Log.i(TAG, "Settings changed. base_font_size = " + app_settings.base_font_size + " , room_name = " + app_settings.room_name);
  	  	}
  	};
  	 
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2){
//        	requestWindowFeature(Window.FEATURE_NO_TITLE);
//        }	
        setContentView(R.layout.activity_main);
        //has_menu_key = ViewConfiguration.get(getBaseContext()).hasPermanentMenuKey();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        update_preferences(preferences);
        preferences.registerOnSharedPreferenceChangeListener(settings_change_listener);    
        
        set_refresh_timer();
        
        //doTheAutoRefresh();
    }
    
    @Override
    public void onResume() {
    	Log.i(TAG, "RUN onResume()");
        super.onResume();  // Always call the superclass method first
        check_fullscreen();
    }
      
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_refresh:
            	refresh_events();
                return true;
            case R.id.menu_settings:
            	Intent settings_activity = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(settings_activity);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private void check_fullscreen() {
	   if(app_settings.fullscreen) {
		   Log.i(TAG, "Set fullscreen parameters");
	       getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	       getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	       getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
	       getActionBar().show();
	       getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
	    	   public void onSystemUiVisibilityChange(int visibility) {
	    		   Timer timer = new Timer();
	    		   timer.schedule(new TimerTask() {
	    			   @Override
	    			   public void run() {
	    				   runOnUiThread(new Runnable() {
	    					   public void run() {
	    			    		   Log.i(TAG, "Set SYSTEM_UI_FLAG_HIDE_NAVIGATION");
	    						   getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
	    					   }
	    				   });
	    			   }
	    		   }, 2000);
	    	   }
	       });
	   } else {
		   Log.i(TAG, "UnSet fullscreen parameters");
		   getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		   getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		   getActionBar().show();
		   getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
			   public void onSystemUiVisibilityChange(int visibility) {
				   Log.i(TAG, "Set SYSTEM_UI_FLAG_VISIBLE");
				   getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
	           }
	       });
	   }
    }
    
    
    private void check_keep_screen_on() {
    	if (app_settings.keep_screen_on)
    	    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    	else
    	    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
     }
    
    private void set_room_name() {
    	TextView room_name_tv = (TextView) findViewById(R.id.text_room_name);
    	room_name_tv.setTextSize(TypedValue.COMPLEX_UNIT_PT, app_settings.base_font_size + 5);
    	room_name_tv.setText(app_settings.room_name);    	    	
    }
    
    public void refresh_events()
    {
    	LinearLayout ll = (LinearLayout) findViewById(R.id.linearLayout1);
    	ll.removeAllViews();
    	event_list = new ArrayList<String[]>();
    	get_event_list(next24h_miliseconds);
       	Log.i(TAG, "Events refreshed room_status = " + room_status);
    	sort_event_list();
        show_events();
    }
    
    public void get_calendars()
    {
    	
    	
    }
    
    public void get_event_list(long future_limit)  //future_limit time in epoch miliseconds 
    {	
    	room_status = "FREE";
    	long now = System.currentTimeMillis();
    	long future_scope = now + future_limit;
    	Uri.Builder eventsUriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon();
    	ContentUris.appendId(eventsUriBuilder, now);
    	ContentUris.appendId(eventsUriBuilder, future_scope);
    	Uri eventsUri = eventsUriBuilder.build();
    	Cursor cursor = getContentResolver().query(eventsUri, null, null, null, Instances.DTSTART + " ASC");
    	//Log.i(TAG, "Cursor cursor = getContentResolver().query(eventsUri, null, null, null, Instances.DTSTART + ' ASC');");
    	if (cursor != null)
    	{
    		while (cursor.moveToNext())
	    	{
	    		String event_id = cursor.getString(cursor.getColumnIndex(Instances.EVENT_ID));
	    		String event_start_string = cursor.getString(cursor.getColumnIndex(Instances.BEGIN));
	    		String event_end_string = cursor.getString(cursor.getColumnIndex(Instances.END));
	    		String event_title = get_event_title(event_id);
	    		String event_organizer = get_event_organizer(event_id);
	       		if (event_start_string != null && !event_start_string.isEmpty() && event_end_string != null && !event_end_string.isEmpty())
	    		{
		    		long event_start_long = Long.valueOf(event_start_string).longValue();
		    		long event_end_long = Long.valueOf(event_end_string).longValue();
		    		if (event_end_long > now && event_start_long < future_scope) 
		    		{
		    			String status = "FREE";
		        		if (event_start_long <= now && event_end_long > now)
		        		{
		        			status = "NOW";
		        		}
		        		else if (event_start_long > now && event_start_long < (now + app_settings.warning_time_miliseconds))
		        		{
		        			status = "NEAR";
		        		}
		        		else
		        		{
		        			status = "FUTURE";
		        		}
		        		event_list.add(new String[] { event_start_string, event_end_string, event_title, event_organizer, status });
		        		
		        		if (status == "NOW")
		        		{
		        			room_status = "NOW";
		        		}
		        		else if (status == "NEAR")
		        		{
			        		if (room_status != "NOW")
			        		{
			        			room_status = "NEAR";
			        		}
		        		}
		    		}
	    		}
	    	}
    	}
    	else
    	{
    		Log.e(TAG,"Events Cursor was null");
    	}
    	//Log.i(TAG, "cursor.close();");
    	cursor.close();
    }

	
    
    public String get_event_title(String event_id)
    {
    	long event_id_long = Long.valueOf(event_id).longValue();
    	Uri event_Uri = ContentUris.withAppendedId(Events.CONTENT_URI, event_id_long);
    	Cursor cursor = getContentResolver().query(event_Uri, null, null, null, null);
    	String event_title = "";
    	if (cursor==null) {
    		Log.i(TAG, "Cursor cursor = getContentResolver().query(event_Uri, null, null, null, null) returned null object for event_id_long=" +  String.valueOf(event_id_long));
    	} else {
        	while (cursor.moveToNext())
        	{
        		event_title = cursor.getString(cursor.getColumnIndex(Events.TITLE));
        		Log.i(TAG, "Get event_title = " + event_title + " for event_id=" + String.valueOf(event_id_long));
        	}
    	}
    	cursor.close();
    	return event_title;
    }
    
    public String get_event_organizer(String event_id)
    {
    	long event_id_long = Long.valueOf(event_id).longValue();
    	Uri event_Uri = ContentUris.withAppendedId(Events.CONTENT_URI, event_id_long);
    	Cursor cursor = getContentResolver().query(event_Uri, null, null, null, null);
    	String event_organizer = "";
    	if (cursor==null) {
    		Log.i(TAG, "Cursor cursor = getContentResolver().query(event_Uri, null, null, null, null) returned null object for event_id_long=" + String.valueOf(event_id_long));
    	} else {
        	while (cursor.moveToNext())
        	{
        		event_organizer = cursor.getString(cursor.getColumnIndex(Events.ORGANIZER));
        		Log.i(TAG, "Get event_organizer = " + event_organizer + " for event_id=" + String.valueOf(event_id_long));
        	}
    	}
    	cursor.close();
    	return event_organizer;
    }
    
    public void sort_event_list() 
    {
    	Collections.sort(event_list ,new Comparator<String[]>() 
    			{
            		public int compare(String[] strings, String[] otherStrings) {
            		return strings[0].compareTo(otherStrings[0]);
            	}
        });
    	Log.i(TAG, "Events sorted");
    }
    	
    public void show_events() 
    {
    	LinearLayout ll = (LinearLayout) findViewById(R.id.linearLayout1);
//    	LinearLayout ll_top = (LinearLayout) findViewById(R.id.linearLayoutTop);
    	LinearLayout ll_root = (LinearLayout) findViewById(R.id.linearLayoutRoot);
    	if (room_status == "NOW")
    	{
    		ll_root.setBackgroundColor(red_collor);
    	}
    	else if (room_status == "NEAR")
    	{
    		ll_root.setBackgroundColor(orange_collor);
    	}
    	else 
    	{
    		ll_root.setBackgroundColor(green_color);
    	}    		
    	String event_title ="";
    	String event_organizer = "";
    	String event_start_hour;
    	String event_end_hour;
    	SimpleDateFormat hours_format = new SimpleDateFormat("HH:mm");
    	if (event_list.size() > 0)
    	{
	    	for(String[] event_tab: event_list)
	    	{
	    		event_title = event_tab[2];
	    		if (event_title.length() > 40)
	    			event_title = event_title.substring(0, 40) + "...";
	    		event_organizer = event_tab[3];
	    		event_start_hour = hours_format.format(new Date(Long.parseLong(event_tab[0])));
	    		event_end_hour = hours_format.format(new Date(Long.parseLong(event_tab[1])));
	    		String event_text = "";
	    		if (app_settings.event_element_time)
	        	{
	    			event_text = event_start_hour + " - " + event_end_hour + "\n";
	        	}
	    		if (app_settings.event_element_title)
	        	{
	    			event_text = event_text + event_title + "\n";
	        	}
	    		if (app_settings.event_element_organizer)
	        	{
	    			event_text = event_text + event_organizer;
	        	}
	        	View line = new View(this);
	        	line.setLayoutParams(new LayoutParams(-1 , 2)); //-1 = match_parrent
	        	line.setBackgroundColor(0xFF000000);
	        	TextView tv = new TextView(this);
	    	    if (event_tab[4] == "NOW")
	    	    {
	    	    	tv.setTypeface(null, Typeface.BOLD);
	    	    }
	    	    tv.setTextSize(TypedValue.COMPLEX_UNIT_PT, app_settings.base_font_size);
	    	    tv.setGravity(Gravity.CENTER_HORIZONTAL);
	        	tv.setText(event_text);
	        	ll.addView(line);
	        	ll.addView(tv);
	    	}
	    	View line = new View(this);
	    	line.setLayoutParams(new LayoutParams(-1 , 2));
	    	line.setBackgroundColor(0xFF000000);
	    	ll.addView(line);
    	}
    	else
    	{
    		TextView tv = new TextView(this);
    		tv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    	    tv.setGravity(Gravity.CENTER);
    	    tv.setTextSize(TypedValue.COMPLEX_UNIT_PT, app_settings.base_font_size + 10);
        	tv.setText("FREE");
        	ll.addView(tv);
    	}
    }
}
