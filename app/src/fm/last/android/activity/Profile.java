package fm.last.android.activity;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Stack;
import java.util.WeakHashMap;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.os.RemoteException;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ViewFlipper;
import android.widget.ViewSwitcher;
import android.widget.AdapterView.OnItemClickListener;
import fm.last.android.AndroidLastFmServerFactory;
import fm.last.android.LastFMApplication;
import fm.last.android.LastFm;
import fm.last.android.OnListRowSelectedListener;
import fm.last.android.R;
import fm.last.android.adapter.EventListAdapter;
import fm.last.android.adapter.LastFMStreamAdapter;
import fm.last.android.adapter.ListAdapter;
import fm.last.android.adapter.ListEntry;
import fm.last.android.adapter.OnEventRowSelectedListener;
import fm.last.android.adapter.SeparatedListAdapter;
import fm.last.android.player.IRadioPlayer;
import fm.last.android.player.RadioPlayerService;
import fm.last.android.utils.ImageCache;
import fm.last.android.utils.UserTask;
import fm.last.android.widget.ProfileBubble;
import fm.last.android.widget.TabBar;
import fm.last.android.widget.TabBarListener;
import fm.last.api.Album;
import fm.last.api.Artist;
import fm.last.api.Event;
import fm.last.api.LastFmServer;
import fm.last.api.RadioPlayList;
import fm.last.api.Session;
import fm.last.api.Tag;
import fm.last.api.Tasteometer;
import fm.last.api.Track;
import fm.last.api.User;
import fm.last.api.ImageUrl;

public class Profile extends ListActivity implements TabBarListener
{
	private static int TAB_RADIO = 0;
	private static int TAB_PROFILE = 1;
	
	
	//Goddamn Java doesn't let you treat enums as ints easily, so we have to have this mess 
	private static final int PROFILE_TOPARTISTS = 0;
	private static final int PROFILE_TOPALBUMS = 1;
	private static final int PROFILE_TOPTRACKS = 2;
	private static final int PROFILE_RECENTLYPLAYED = 3;
	private static final int PROFILE_EVENTS = 4;
	private static final int PROFILE_FRIENDS = 5;
	private static final int PROFILE_TAGS = 6;
	
	private static final int DIALOG_ALBUM = 0 ;
	private static final int DIALOG_TRACK = 1;

    private SeparatedListAdapter mMainAdapter;
    private ListAdapter mProfileAdapter;
    private LastFMStreamAdapter mMyStationsAdapter;
    private LastFMStreamAdapter mMyRecentAdapter;
    private LastFMStreamAdapter mMyPlaylistsAdapter;
    private User mUser;
    private String mUsername; // store this separate so we have access to it before User obj is retrieved
    private boolean isAuthenticatedUser;
	LastFmServer mServer = AndroidLastFmServerFactory.getServer();

	TabBar mTabBar;
	ViewFlipper mViewFlipper;
	ViewFlipper mNestedViewFlipper;
	ListView mProfileList;
	ProfileBubble mProfileBubble;
    private Stack<Integer> mViewHistory;
	
	View previousSelectedView = null;
	
	ListView mDialogList;
	private ListAdapter mDialogAdapter;
	
	//Animations
	Animation mPushRightIn;
	Animation mPushRightOut;
	Animation mPushLeftIn;
	Animation mPushLeftOut;
	
	//Profile lists
	private ImageCache mImageCache;
	ListView mTopArtistsList;
	private ListAdapter mTopArtistsAdapter;
	ListView mTopAlbumsList;
    private ListAdapter mTopAlbumsAdapter;
    ListView mTopTracksList;
    private ListAdapter mTopTracksAdapter;
    ListView mRecentTracksList;
    private ListAdapter mRecentTracksAdapter;
    ListView mEventsList;
    private EventListAdapter mEventsAdapter;
    ListView mFriendsList;
    private ListAdapter mFriendsAdapter;
    ListView mTagsList;
    private ListAdapter mTagsAdapter;
    
    private Track mTrackInfo; // For the profile actions' dialog
    private Album mAlbumInfo; // Ditto
	
	private IntentFilter mIntentFilter;
    
    @Override
    public void onCreate( Bundle icicle )
    {
        super.onCreate( icicle );
        requestWindowFeature( Window.FEATURE_NO_TITLE );
        setContentView( R.layout.home );
        Session session = ( Session ) LastFMApplication.getInstance().map
                .get( "lastfm_session" );
        if( session == null )
            logout();
        mUsername = getIntent().getStringExtra("lastfm.profile.username");
        if( mUsername == null ) {
            mUsername = session.getName();
            isAuthenticatedUser = true;
        } 
        else 
            isAuthenticatedUser = false;
        
        if( isAuthenticatedUser ) {
            Button b = new Button(this);
            b.setOnClickListener( mNewStationListener );
            b.setOnFocusChangeListener( mNewStationFocusChangeListener );
            
            //Note: The focus and rest drawable resources are also
            // 		hardcoded into the mNewStationFocusChangeListener.
            b.setBackgroundResource( R.drawable.list_station_starter_rest );
            b.setTextColor(0xffffffff);
    		b.setTextSize(TypedValue.COMPLEX_UNIT_PT, 7);
    		b.setFocusable( true );
    		b.setGravity( 3 | 16 ); //sorry not to use constants, I got lame and couldn't figure it out
    		b.setTypeface( Typeface.create( Typeface.DEFAULT, Typeface.BOLD ) );
    		b.setText(R.string.home_newstation);
    		
    		b.setTag("header");

    		getListView().addHeaderView(b, null, true);
        } else {
            mProfileBubble = new ProfileBubble(this);
            mProfileBubble.setTag("header");
            mProfileBubble.setClickable( false );
            getListView().addHeaderView(mProfileBubble, null, false);
        }
        
        
        mViewHistory = new Stack<Integer>();
		mTabBar = (TabBar) findViewById(R.id.TabBar);
		mViewFlipper = (ViewFlipper) findViewById(R.id.ViewFlipper);
		mNestedViewFlipper = (ViewFlipper) findViewById(R.id.NestedViewFlipper);
		mNestedViewFlipper.setAnimateFirstView(false);
		mNestedViewFlipper.setAnimationCacheEnabled(false);
		mTabBar.setViewFlipper(mViewFlipper);
		mTabBar.setListener(this);
		if(isAuthenticatedUser) {
		    mTabBar.addTab("Radio", R.drawable.radio, R.drawable.radio, R.drawable.radio, TAB_RADIO);
		    mTabBar.addTab("Profile", R.drawable.profile, R.drawable.profile, R.drawable.profile, TAB_PROFILE);
		} else {
		    mTabBar.addTab(mUsername + "'s Radio", R.drawable.radio, R.drawable.radio, R.drawable.radio, TAB_RADIO);
            mTabBar.addTab(mUsername + "'s Profile", R.drawable.profile, R.drawable.profile, R.drawable.profile, TAB_PROFILE);
		}
		mTabBar.setActive(TAB_RADIO);
       
        mMyRecentAdapter = new LastFMStreamAdapter( this );

        new LoadUserTask().execute((Void)null);
        SetupMyStations();
        SetupRecentStations();
        RebuildMainMenu();
        
        mProfileList = (ListView)findViewById(R.id.profile_list_view);
    	String[] mStrings = new String[]{"Top Artists", "Top Albums", "Top Tracks", "Recently Played", "Events", "Friends", "Tags"}; // this order must match the ProfileActions enum
        mProfileAdapter = new ListAdapter(Profile.this, mStrings);
        mProfileList.setAdapter(mProfileAdapter); 
        mProfileList.setOnItemClickListener(mProfileClickListener);
        
        {
        	OnListRowSelectedListener listRowListener = new OnListRowSelectedListener(mProfileList);
        	listRowListener.setIconResources( R.drawable.list_item_rest_arrow, R.drawable.list_item_focus_arrow );
        	listRowListener.setResources( R.drawable.list_item_rest_fullwidth, R.drawable.list_item_focus_fullwidth );
        	mProfileList.setOnItemSelectedListener( listRowListener );
        }
               
		getListView().setOnItemSelectedListener(new OnListRowSelectedListener(getListView()));
		((OnListRowSelectedListener)getListView().getOnItemSelectedListener()).setResources(R.drawable.list_item_rest, R.drawable.list_item_focus);
		
		mTopArtistsList = (ListView) findViewById(R.id.topartists_list_view);
		mTopArtistsList.setOnItemSelectedListener(new OnListRowSelectedListener(mTopArtistsList));
		mTopAlbumsList = (ListView) findViewById(R.id.topalbums_list_view);
        mTopAlbumsList.setOnItemSelectedListener(new OnListRowSelectedListener(mTopAlbumsList));
        mTopTracksList = (ListView) findViewById(R.id.toptracks_list_view);
        mTopTracksList.setOnItemSelectedListener(new OnListRowSelectedListener(mTopTracksList));
        mRecentTracksList = (ListView) findViewById(R.id.recenttracks_list_view);
        mRecentTracksList.setOnItemSelectedListener(new OnListRowSelectedListener(mRecentTracksList));
        mEventsList = (ListView) findViewById(R.id.profileevents_list_view);
        mEventsList.setOnItemSelectedListener(new OnEventRowSelectedListener(mEventsList));
        ((OnEventRowSelectedListener)mEventsList.getOnItemSelectedListener()).setResources(R.drawable.list_item_rest_fullwidth, R.drawable.list_item_focus_fullwidth);
        mFriendsList = (ListView) findViewById(R.id.profilefriends_list_view);
        mFriendsList.setOnItemSelectedListener(new OnListRowSelectedListener(mFriendsList));
        mTagsList = (ListView) findViewById(R.id.profiletags_list_view);
        mTagsList.setOnItemSelectedListener(new OnListRowSelectedListener(mTagsList));
        
        // Loading animations
        mPushLeftIn = AnimationUtils.loadAnimation(this, R.anim.push_left_in);
		mPushLeftOut = AnimationUtils.loadAnimation(this, R.anim.push_left_out);
		mPushRightIn = AnimationUtils.loadAnimation(this, R.anim.push_right_in);
		mPushRightOut = AnimationUtils.loadAnimation(this, R.anim.push_right_out);
        
		mIntentFilter = new IntentFilter();
		mIntentFilter.addAction( RadioPlayerService.PLAYBACK_ERROR );
		
		if( getIntent().getBooleanExtra("lastfm.profile.new_user", false ) )
			startActivity( new Intent( Profile.this, NewStation.class ) );
    }
    
    private class LoadUserTask extends UserTask<Void, Void, Boolean> {
        Tasteometer tasteometer;
        @Override
    	public void onPreExecute() {
        	mMyPlaylistsAdapter = null;
        }
    	
        @Override
        public Boolean doInBackground(Void...params) {
        	RadioPlayList[] playlists;
            boolean success = false;
            Session session = ( Session ) LastFMApplication.getInstance().map.get( "lastfm_session" );
    		try {
    		    if( mUsername == null) {
    				mUser = mServer.getUserInfo( session.getKey() );
    				playlists = mServer.getUserPlaylists( session.getName() );
    		    } else {
    		        mUser = mServer.getAnyUserInfo( mUsername );
    		        tasteometer = mServer.tasteometerCompare(mUsername, session.getName(), 8);
    		        playlists = mServer.getUserPlaylists( mUsername );
    		    }
    		    if(playlists.length > 0) {
    		    	mMyPlaylistsAdapter = new LastFMStreamAdapter(Profile.this);
    		    	for(RadioPlayList playlist : playlists) {
    		    		if(playlist.isStreamable())
    		    			mMyPlaylistsAdapter.putStation(playlist.getTitle(), "lastfm://playlist/" + playlist.getId() + "/shuffle");
    		    	}
    		    }
    			success = true;
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
            return success;
        }

        @Override
        public void onPostExecute(Boolean result) {
            if( !isAuthenticatedUser ) {
                mProfileBubble.setUser(Profile.this.mUser);
                SetupCommonArtists(tasteometer);
            }
            RebuildMainMenu();
        }
    }
    
    void SetupCommonArtists(Tasteometer ts)
    {
        mMyRecentAdapter.resetList();

        for (String name : ts.getResults())
        {
            String url = "lastfm://artist/"+Uri.encode(name)+"/similarartists";
            mMyRecentAdapter.putStation( name, url );
        }

        mMyRecentAdapter.updateModel();
    }

    public void tabChanged(int index, int previousIndex) {
		//Log.i("Lukasz", "Changed tab to "+text+", index="+index);
	}
	
    private void RebuildMainMenu() 
    {
        Session session = ( Session ) LastFMApplication.getInstance().map.get( "lastfm_session" );
        mMainAdapter = new SeparatedListAdapter(this);
        if(isAuthenticatedUser) {
            mMainAdapter.addSection( getString(R.string.home_mystations), mMyStationsAdapter );
            if(mMyRecentAdapter.getCount() > 0)
            	mMainAdapter.addSection( getString(R.string.home_recentstations), mMyRecentAdapter );
            if(session.getSubscriber().equals("1")&&mMyPlaylistsAdapter != null && mMyPlaylistsAdapter.getCount() > 0) {
            	mMainAdapter.addSection( "Your Playlists", mMyPlaylistsAdapter);
            }
        } else {
            mMainAdapter.addSection( mUsername + "'s Stations", mMyStationsAdapter );        
            mMainAdapter.addSection( getString(R.string.home_commonartists), mMyRecentAdapter );
            if(session.getSubscriber().equals("1")&&mMyPlaylistsAdapter != null && mMyPlaylistsAdapter.getCount() > 0) {
            	mMainAdapter.addSection( mUsername + "'s Playlists", mMyPlaylistsAdapter);
            }
        }
        setListAdapter( mMainAdapter );
        mMainAdapter.notifyDataSetChanged();
    }
    
    @Override
    public void onResume() {
		registerReceiver( mStatusListener, mIntentFilter );
    	SetupRecentStations();
    	RebuildMainMenu();

    	if(mTopArtistsAdapter != null)
    		mTopArtistsAdapter.disableLoadBar();
    	if(mTopAlbumsAdapter != null)
    		mTopAlbumsAdapter.disableLoadBar();
    	if(mTopTracksAdapter != null)
    		mTopTracksAdapter.disableLoadBar();
    	if(mRecentTracksAdapter != null)
    		mRecentTracksAdapter.disableLoadBar();
    	if(mFriendsAdapter != null)
    		mFriendsAdapter.disableLoadBar();
    	if(mTagsAdapter != null)
    		mTagsAdapter.disableLoadBar();
		super.onResume();
    }
    
	@Override
	protected void onPause() {
		unregisterReceiver( mStatusListener );
		super.onPause();
	}

	private BroadcastReceiver mStatusListener = new BroadcastReceiver()
	{

		@Override
		public void onReceive( Context context, Intent intent )
		{

			String action = intent.getAction();
			if ( action.equals( RadioPlayerService.PLAYBACK_ERROR ) )
			{
				RebuildMainMenu();
		    	if(mTopArtistsAdapter != null)
		    		mTopArtistsAdapter.disableLoadBar();
		    	if(mTagsAdapter != null)
		    		mTagsAdapter.disableLoadBar();
			}
		}
	};
	
	@SuppressWarnings("unchecked")
    private void SetupRecentStations()
    {
        if(!isAuthenticatedUser)
            return;
        mMyRecentAdapter.resetList();
        WeakHashMap<String, String> stations = LastFMApplication.getInstance().getRecentStations();

        Iterator kvp = stations.entrySet().iterator();
        for (int i = 0; i < stations.size(); i++)
        {
            WeakHashMap.Entry<String, String> entry = (WeakHashMap.Entry<String, String>) kvp.next();
            String name = entry.getKey();
            String url = entry.getValue();
            mMyRecentAdapter.putStation( name, url );
        }

        mMyRecentAdapter.updateModel();

    }

    private void SetupMyStations()
    {
        Session session = ( Session ) LastFMApplication.getInstance().map.get( "lastfm_session" );
        mMyStationsAdapter = new LastFMStreamAdapter( this );
        if(isAuthenticatedUser) {
	        mMyStationsAdapter.putStation( getString(R.string.home_mylibrary), 
	        		"lastfm://user/" + Uri.encode( mUsername ) + "/personal" );
	        if(session.getSubscriber().equals("1"))
	        	mMyStationsAdapter.putStation( getString(R.string.home_myloved), 
	        		"lastfm://user/" + Uri.encode( mUsername ) + "/loved" );
	        mMyStationsAdapter.putStation( getString(R.string.home_myrecs), 
	        		"lastfm://user/" + Uri.encode( mUsername ) + "/recommended" );
	        mMyStationsAdapter.putStation( getString(R.string.home_myneighborhood), 
	        		"lastfm://user/" + Uri.encode( mUsername ) + "/neighbours" );
        } else {
	        mMyStationsAdapter.putStation( mUsername + "'s Library", 
	        		"lastfm://user/" + Uri.encode( mUsername ) + "/personal" );
	        if(session.getSubscriber().equals("1"))
	        	mMyStationsAdapter.putStation( mUsername + "'s Loved Tracks", 
	        		"lastfm://user/" + Uri.encode( mUsername ) + "/loved" );
	        mMyStationsAdapter.putStation( getString(R.string.home_myrecs), 
	        		"lastfm://user/" + Uri.encode( mUsername ) + "/recommended" );
	        mMyStationsAdapter.putStation( mUsername + "'s Neighbourhood", 
	        		"lastfm://user/" + Uri.encode( mUsername ) + "/neighbours" );
        }
        
/*
 * we were going to do this (see issue 26) but it didn't look so good
 *     
        // if we're tuned to something, 
        // and it isn't one of the above, 
        // then we want to add it in at the top!
    	String currentStationUrl = null;
    	String currentStationName = null; 
    	try {
	    	currentStationUrl = LastFMApplication.getInstance().player.getStationUrl();
	    	currentStationName = LastFMApplication.getInstance().player.getStationName();
		} catch (RemoteException e) {
		}
        if (currentStationUrl != null) {
    		if (currentStationName == null) {
    			currentStationName = "fixme please";
    		}
            boolean playing = false;
            for(int i = 0; !playing && i < mMyStationsAdapter.getCount(); i++) {
    			if (mMyStationsAdapter.getStation(i).equals(currentStationUrl)) {
    				playing = true;
    			}
    		}
            if (!playing) {
        		mMyStationsAdapter.putStationAtFront(currentStationName, currentStationUrl);
            }
        }
*/        
        mMyStationsAdapter.updateModel();
    }

    public void onListItemClick( ListView l, View v, int position, long id )
    {
    	// mMainAdapter seems to want position-1 :-/

        if( !mMainAdapter.isEnabled( position-1 ))
        	return;
        
        try
        {
	        String adapter_station = mMainAdapter.getStation(position-1);
	        
        	if ( LastFMApplication.getInstance().player.isPlaying() ) {
		        String current_station = LastFMApplication.getInstance().player.getStationUrl();
		
		        if( adapter_station.equals( current_station ) ) {
		            Intent intent = new Intent( Profile.this, Player.class );
		            startActivity( intent );
		            return;
		        }
        	}
	        
	    	l.setEnabled(false);
	    	l.getOnItemSelectedListener().onItemSelected(l, v, position, id);
	    	ViewSwitcher switcher = (ViewSwitcher)v.findViewById(R.id.row_view_switcher);
	    	switcher.showNext();
	    	LastFMApplication.getInstance().playRadioStation(this, adapter_station);
	    }
	    catch (RemoteException e)
	    {
	    	Log.e( "Last.fm", e.getMessage() );
	    }
    }

    private OnClickListener mNewStationListener = new OnClickListener()
    {
        public void onClick( View v )
        {
            Intent intent = new Intent( Profile.this, NewStation.class );
            startActivity( intent );
        }
    };
    
    private OnFocusChangeListener mNewStationFocusChangeListener = new OnFocusChangeListener()
    {
    	public void onFocusChange( View v, boolean hasFocus )
    	{
    		if( hasFocus )
    		{
    			v.setBackgroundResource( R.drawable.list_station_starter_focus );
    		}
    		else
    		{
    			v.setBackgroundResource( R.drawable.list_station_starter_rest );
    		}
    		
    	}
    };
    
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if( keyCode == KeyEvent.KEYCODE_BACK )
        {
            if( mViewFlipper.getDisplayedChild() == TAB_PROFILE && !mViewHistory.isEmpty() )
            {
            	setPreviousAnimation();
                mProfileAdapter.disableLoadBar();
                mNestedViewFlipper.setDisplayedChild(mViewHistory.pop());
                return true;
            }
            if(event.getRepeatCount() == 0) {
                finish();
                return true;
            }
        }
        return false;
    }
    
    private void setNextAnimation(){
    	mNestedViewFlipper.setInAnimation(mPushLeftIn);
		mNestedViewFlipper.setOutAnimation(mPushLeftOut);
    }
    
    private void setPreviousAnimation(){
    	mNestedViewFlipper.setInAnimation(mPushRightIn);
		mNestedViewFlipper.setOutAnimation(mPushRightOut);
    }
    
    private OnItemClickListener mProfileClickListener = new OnItemClickListener()
    {

    	
        public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) 
        {
        	setNextAnimation();
            mProfileAdapter.enableLoadBar(position);
            switch ( position )
            {
            case PROFILE_TOPARTISTS: //"Top Artists"
                new LoadTopArtistsTask().execute((Void)null);
                break;
            case PROFILE_TOPALBUMS: //"Top Albums"
                new LoadTopAlbumsTask().execute((Void)null);
                break;
            case PROFILE_TOPTRACKS: //"Top Tracks"
                new LoadTopTracksTask().execute((Void)null);
                break;
            case PROFILE_RECENTLYPLAYED: //"Recently Played"
                new LoadRecentTracksTask().execute((Void)null);
                break;
            case PROFILE_EVENTS: //"Events"
                new LoadEventsTask().execute((Void)null);
                break;
            case PROFILE_FRIENDS: //"Friends"
                new LoadFriendsTask().execute((Void)null);
                break;
            case PROFILE_TAGS: //"Tags"
                new LoadTagsTask().execute((Void)null);
                break;
            default: 
                break;
            
            }
            
        }
        
    };
    
    private class LoadTopArtistsTask extends UserTask<Void, Void, Boolean> {
        
        public Boolean doInBackground(Void...params) {
            boolean success = false;
            
            mTopArtistsAdapter = new ListAdapter(Profile.this, getImageCache());
            mTopArtistsList.setOnItemClickListener(new OnItemClickListener() {

                public void onItemClick(AdapterView<?> l, View v,
                        int position, long id) {
                    Artist artist = (Artist)mTopArtistsAdapter.getItem(position);
                    mTopArtistsAdapter.enableLoadBar(position);
                    LastFMApplication.getInstance().playRadioStation(Profile.this, "lastfm://artist/"+Uri.encode(artist.getName())+"/similarartists");
                }
                
            });

            try {
                Artist[] topartists = mServer.getUserTopArtists(mUser.getName(), "overall");
                if(topartists.length == 0 )
                    return false;
                ArrayList<ListEntry> iconifiedEntries = new ArrayList<ListEntry>();
                for(int i=0; i< ((topartists.length < 10) ? topartists.length : 10); i++)
                {
                	String url = null;
                	try 
                	{
                		ImageUrl[] urls = topartists[i].getImages();
                		url = urls[0].getUrl();
                	}
                	catch (ArrayIndexOutOfBoundsException e)
                	{}
                                	
                    ListEntry entry = new ListEntry(topartists[i], 
                            R.drawable.artist_icon, 
                            topartists[i].getName(), 
                            url,
                            R.drawable.radio_icon);
                    iconifiedEntries.add(entry);
                }
                mTopArtistsAdapter.setSourceIconified(iconifiedEntries);
                success = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return success;
        }

        @Override
        public void onPostExecute(Boolean result) {
            if(result) {
                mTopArtistsList.setAdapter(mTopArtistsAdapter);
            } else {
                String[] strings = new String[]{"No Top Artists"};
                mTopArtistsList.setAdapter(new ArrayAdapter<String>(Profile.this, 
                        R.layout.list_row, R.id.row_label, strings)); 
            }
            mViewHistory.push(mNestedViewFlipper.getDisplayedChild()); // Save the current view
            mNestedViewFlipper.setDisplayedChild(PROFILE_TOPARTISTS + 1);
        }
    }

    private class LoadTopAlbumsTask extends UserTask<Void, Void, Boolean> {
        
        @Override
        public Boolean doInBackground(Void...params) {
            boolean success = false;
            
            mTopAlbumsAdapter = new ListAdapter(Profile.this, getImageCache());
            mTopAlbumsList.setOnItemClickListener(new OnItemClickListener() {

                public void onItemClick(AdapterView<?> l, View v,
                        int position, long id) {
                    Album album = (Album) mTopAlbumsAdapter.getItem(position);
                    showAlbumDialog(album);
                }
                
            });

            try {
                Album[] topalbums = mServer.getUserTopAlbums(mUser.getName(), "overall");
                if(topalbums.length == 0 )
                    return false;
                ArrayList<ListEntry> iconifiedEntries = new ArrayList<ListEntry>();
                for(int i=0; i< ((topalbums.length < 10) ? topalbums.length : 10); i++)
                {
                	String url = null;
                	try 
                	{
                		ImageUrl[] urls = topalbums[i].getImages();
                		url = urls[ urls.length > 1 ? 1 : 0 ].getUrl();
                	}
                	catch (ArrayIndexOutOfBoundsException e)
                	{}
                	
                    ListEntry entry = new ListEntry(topalbums[i], 
                            R.drawable.no_artwork, 
                            topalbums[i].getTitle(),  
                            url,
                            topalbums[i].getArtist());
                    iconifiedEntries.add(entry);
                }
                mTopAlbumsAdapter.setSourceIconified(iconifiedEntries);
                success = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return success;
        }

        @Override
        public void onPostExecute(Boolean result) {
            if(result) {
                mTopAlbumsList.setAdapter(mTopAlbumsAdapter);
            } else {
                String[] strings = new String[]{"No Top Albums"};
                mTopAlbumsList.setAdapter(new ArrayAdapter<String>(Profile.this, 
                        R.layout.list_row, R.id.row_label, strings)); 
            }
            mViewHistory.push(mNestedViewFlipper.getDisplayedChild()); // Save the current view
            mNestedViewFlipper.setDisplayedChild(PROFILE_TOPALBUMS + 1); 
        }
    }
    
    private class LoadTopTracksTask extends UserTask<Void, Void, Boolean> {

        @Override
        public Boolean doInBackground(Void...params) {
            boolean success = false;

            mTopTracksAdapter = new ListAdapter(Profile.this, getImageCache());
            mTopTracksList.setOnItemClickListener(new OnItemClickListener() {

                public void onItemClick(AdapterView<?> l, View v,
                        int position, long id) {
                    Track track = (Track) mTopTracksAdapter.getItem(position);
                    showTrackDialog(track);
                }

            });

            try {
                Track[] toptracks = mServer.getUserTopTracks(mUser.getName(), "overall");
                if(toptracks.length == 0 )
                    return false;
                ArrayList<ListEntry> iconifiedEntries = new ArrayList<ListEntry>();
                for(int i=0; i< ((toptracks.length < 10) ? toptracks.length : 10); i++){
                    ListEntry entry = new ListEntry(toptracks[i],
                            R.drawable.song_icon,
                            toptracks[i].getName(), 
                            toptracks[i].getImages().length == 0 ? "" : toptracks[i].getImages()[0].getUrl(), // some tracks don't have images
                            toptracks[i].getArtist().getName());
                    iconifiedEntries.add(entry);
                }
                mTopTracksAdapter.setSourceIconified(iconifiedEntries);
                success = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return success;
        }

        @Override
        public void onPostExecute(Boolean result) {
            if(result) {
                mTopTracksList.setAdapter(mTopTracksAdapter);
                            } else {
                String[] strings = new String[]{"No Top Tracks"};
                mTopTracksList.setAdapter(new ArrayAdapter<String>(Profile.this,
                        R.layout.list_row, R.id.row_label, strings));
            }
            mViewHistory.push(mNestedViewFlipper.getDisplayedChild()); // Save the current view
            mNestedViewFlipper.setDisplayedChild(PROFILE_TOPTRACKS + 1);
        }
    }
    
    private class LoadRecentTracksTask extends UserTask<Void, Void, Boolean> {

        @Override
        public Boolean doInBackground(Void...params) {
            boolean success = false;

            mRecentTracksAdapter = new ListAdapter(Profile.this, getImageCache());
            mRecentTracksList.setOnItemClickListener(new OnItemClickListener() {

                public void onItemClick(AdapterView<?> l, View v,
                        int position, long id) {
                    Track track = (Track) mRecentTracksAdapter.getItem(position);
                    showTrackDialog(track);
                }

            });

            try {
                Track[] recenttracks = mServer.getUserRecentTracks(mUser.getName(), 10);
                if(recenttracks.length == 0 )
                    return false;
                ArrayList<ListEntry> iconifiedEntries = new ArrayList<ListEntry>();
                for(int i=0; i< ((recenttracks.length < 10) ? recenttracks.length : 10); i++){
                    ListEntry entry = new ListEntry(recenttracks[i],
                            R.drawable.song_icon,
                            recenttracks[i].getName(), 
                            recenttracks[i].getImages().length == 0 ? "" : recenttracks[i].getImages()[0].getUrl(), // some tracks don't have images
                            recenttracks[i].getArtist().getName());
                    iconifiedEntries.add(entry);
                }
                mRecentTracksAdapter.setSourceIconified(iconifiedEntries);
                success = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return success;
        }

        @Override
        public void onPostExecute(Boolean result) {
            if(result) {
                mRecentTracksList.setAdapter(mRecentTracksAdapter);
            } else {
                String[] strings = new String[]{"No Recent Tracks"};
                mRecentTracksList.setAdapter(new ArrayAdapter<String>(Profile.this,
                        R.layout.list_row, R.id.row_label, strings));
            }
            mViewHistory.push(mNestedViewFlipper.getDisplayedChild()); // Save the current view
            mNestedViewFlipper.setDisplayedChild(PROFILE_RECENTLYPLAYED + 1);
        }
    }
    
    private class LoadEventsTask extends UserTask<Void, Void, Boolean> {

        @Override
        public Boolean doInBackground(Void...params) {
            boolean success = false;

            mEventsAdapter = new EventListAdapter(Profile.this);
            try {
                fm.last.api.Event[] events = mServer.getUserEvents(mUser.getName());
                mEventsAdapter.setEventsSource(events);
                if(events.length > 0)
                    success = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return success;
        }

        @Override
        public void onPostExecute(Boolean result) {
            if(result) {
                mEventsList.setAdapter(mEventsAdapter);
                //mEventsList.setOnScrollListener(mEventsAdapter.getOnScrollListener());
                mEventsList.setOnItemClickListener(mEventOnItemClickListener);
            } else {
                String[] strings = new String[]{"No Upcoming Events"};
                mEventsList.setAdapter(new ArrayAdapter<String>(Profile.this,
                        R.layout.list_row, R.id.row_label, strings));
            }
            mViewHistory.push(mNestedViewFlipper.getDisplayedChild()); // Save the current view
            mNestedViewFlipper.setDisplayedChild(PROFILE_EVENTS + 1);
        }
    }
    
    private OnItemClickListener mEventOnItemClickListener = new OnItemClickListener(){

        public void onItemClick(final AdapterView<?> parent, final View v,
                final int position, long id) {
            Intent intent = new Intent( Profile.this, fm.last.android.activity.Event.class );
            Event event = (Event)mEventsAdapter.getItem(position);
            intent.putExtra("lastfm.event.id", Integer.toString(event.getId()));
            intent.putExtra("lastfm.event.title", event.getTitle());
            String artists = "";
            for(String artist : event.getArtists()) {
                if(artists.length() > 0)
                    artists += ", ";
                artists += artist;
            }
            for(ImageUrl image : event.getImages()) {
                if(image.getSize().contentEquals("large"))
                    intent.putExtra("lastfm.event.poster", image.getUrl());
            }
            intent.putExtra("lastfm.event.artists", artists);
            intent.putExtra("lastfm.event.venue", event.getVenue().getName());
            intent.putExtra("lastfm.event.street", event.getVenue().getLocation().getStreet());
            intent.putExtra("lastfm.event.city", event.getVenue().getLocation().getCity());
            intent.putExtra("lastfm.event.postalcode", event.getVenue().getLocation().getPostalcode());
            intent.putExtra("lastfm.event.country", event.getVenue().getLocation().getCountry());
            intent.putExtra("lastfm.event.month", new SimpleDateFormat("MMM").format(event.getStartDate()));
            intent.putExtra("lastfm.event.day", new SimpleDateFormat("d").format(event.getStartDate()));
            try {
                Event[] events = mServer.getUserEvents(((Session)LastFMApplication.getInstance().map.get("lastfm_session")).getName());
                for(Event e : events) {
                    System.out.printf("Comparing id %d (%s) to %d (%s)\n",e.getId(),e.getTitle(),event.getId(),event.getTitle());
                    if(e.getId() == event.getId()) {
                        System.out.printf("Matched! Status: %s\n", e.getStatus());
                        intent.putExtra("lastfm.event.status", e.getStatus());
                        break;
                    }
                        
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            startActivity( intent );
        }

    };
    
    private class LoadTagsTask extends UserTask<Void, Void, Boolean> {
    	
        @Override
        public Boolean doInBackground(Void...params) {
            boolean success = false;

			mTagsAdapter = new ListAdapter(Profile.this, getImageCache());
			mTagsList.setOnItemClickListener(new OnItemClickListener() {

				public void onItemClick(AdapterView<?> l, View v,
						int position, long id) {
			        Session session = ( Session ) LastFMApplication.getInstance().map
	                .get( "lastfm_session" );
					Tag tag = (Tag)mTagsAdapter.getItem(position);
					mTagsAdapter.enableLoadBar(position);
					if(session.getSubscriber().equals("1"))
						LastFMApplication.getInstance().playRadioStation(Profile.this, "lastfm://usertags/"+mUsername+"/"+Uri.encode(tag.getName()));
					else
						LastFMApplication.getInstance().playRadioStation(Profile.this, "lastfm://globaltags/"+Uri.encode(tag.getName()));
				}
				
			});

    		try {
    			Tag[] tags = mServer.getUserTopTags(mUsername, 10);
    			ArrayList<ListEntry> iconifiedEntries = new ArrayList<ListEntry>();
    			for(int i=0; i< ((tags.length < 10) ? tags.length : 10); i++){
    				ListEntry entry = new ListEntry(tags[i], 
    						-1,
    						tags[i].getName(), 
    						R.drawable.radio_icon);
    				iconifiedEntries.add(entry);
    			}
    			mTagsAdapter.setSourceIconified(iconifiedEntries);
    			success = true;
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
            return success;
        }

        @Override
        public void onPostExecute(Boolean result) {
        	if(result) {
        		mTagsList.setAdapter(mTagsAdapter);
                mViewHistory.push(mNestedViewFlipper.getDisplayedChild()); // Save the current view
                mNestedViewFlipper.setDisplayedChild(PROFILE_TAGS + 1);
        	} else {
    	        String[] strings = new String[]{"No Tags"};
    	        mTagsList.setAdapter(new ArrayAdapter<String>(Profile.this, 
    	                R.layout.list_row, R.id.row_label, strings)); 
        	}
        }
    }

    private class LoadFriendsTask extends UserTask<Void, Void, Boolean> {

        @Override
        public Boolean doInBackground(Void...params) {
            boolean success = false;
            
            mFriendsAdapter = new ListAdapter(Profile.this, getImageCache());
            
            try {
                User[] friends = mServer.getFriends(mUser.getName(), null, null).getFriends();
                if(friends.length == 0 )
                    return false;
                ArrayList<ListEntry> iconifiedEntries = new ArrayList<ListEntry>();
                for(int i=0; i < friends.length; i++){
                    ListEntry entry = new ListEntry(friends[i],
                            R.drawable.profile_unknown,
                            friends[i].getName(), 
                            friends[i].getImages().length == 0 ? "" : friends[i].getImages()[0].getUrl()); // some tracks don't have images
                    iconifiedEntries.add(entry);
                }
                mFriendsAdapter.setSourceIconified(iconifiedEntries);
                success = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return success;
        }

        @Override
        public void onPostExecute(Boolean result) {
            mFriendsList.setOnItemClickListener(new OnItemClickListener() {

                public void onItemClick(AdapterView<?> l, View v,
                        int position, long id) {
                    mFriendsAdapter.enableLoadBar(position);
                    User user = (User) mFriendsAdapter.getItem(position);
                    Intent profileIntent = new Intent(Profile.this, fm.last.android.activity.Profile.class);
                    profileIntent.putExtra("lastfm.profile.username", user.getName());
                    startActivity(profileIntent);
                }
            });
            if(result) {
                mFriendsList.setAdapter(mFriendsAdapter);
            } else {
                String[] strings = new String[]{"No Friends Retrieved"};
                mFriendsList.setAdapter(new ArrayAdapter<String>(Profile.this,
                        R.layout.list_row, R.id.row_label, strings));
            }
            mViewHistory.push(mNestedViewFlipper.getDisplayedChild()); // Save the current view
            mNestedViewFlipper.setDisplayedChild(PROFILE_FRIENDS + 1);
        }
    }
    
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        // Parameters for menu.add are:
        // group -- Not used here.
        // id -- Used only when you want to handle and identify the click yourself.
        // title
        MenuItem logout = menu.add(Menu.NONE, 0, Menu.NONE, "Logout");
        logout.setIcon(R.drawable.logout);

        MenuItem nowPlaying = menu.add(Menu.NONE, 1, Menu.NONE, "Now Playing");
		nowPlaying.setIcon( R.drawable.view_artwork );
        return true;
    }
    
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)  {
		boolean isPlaying = false;
		try {
			if (LastFMApplication.getInstance() != null)
				isPlaying = LastFMApplication.getInstance().player.isPlaying();
		} catch (RemoteException e) {
		}
		
		menu.findItem(1).setEnabled( isPlaying );
		
		return super.onPrepareOptionsMenu(menu);
	}
    
    
    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()) {
        case 0:
            logout();
            return true;
        case 1:
            Intent intent = new Intent( Profile.this, Player.class );
            startActivity( intent );
            return true;
        }
        return false;
    }
    
    private void showTrackDialog(Track track)
    {
        mTrackInfo = track;
        showDialog(DIALOG_TRACK);
    }
    private void showAlbumDialog(Album album)
    {
        mAlbumInfo = album;
        showDialog(DIALOG_ALBUM);
    }
    
    protected Dialog onCreateDialog(int id) 
    {
        final int dialogId = id;
        mDialogList = new ListView(Profile.this);
        mDialogAdapter = new ListAdapter(Profile.this, getImageCache());

        ArrayList<ListEntry> entries = prepareProfileActions(id);
        mDialogAdapter.setSourceIconified(entries);
        mDialogAdapter.setIconsUnscaled();
        mDialogList.setAdapter(mDialogAdapter);
        mDialogList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> l, View v, int position, long id) 
            {
            	if(dialogId == DIALOG_TRACK) {
                    switch (position)
                    {
                        case 0: // Similar
                            mDialogAdapter.enableLoadBar(position);
                            playSimilar(dialogId);
                            break;
                        case 1: // Share
                     	   shareItem();
                     	   break;
                        case 2: // Tag
                            tagItem(dialogId);
                            break;
                        case 3: // Add to Playlist
                      	   addItemToPlaylist();
                      	   break;
                        case 4: // Buy on Amazon
                     	   buyAmazon(dialogId);
                     	   break;
                    }
            	}
            	if(dialogId == DIALOG_ALBUM) {
                    switch (position)
                    {
                        case 0: // Similar
                            mDialogAdapter.enableLoadBar(position);
                            playSimilar(dialogId);
                            break;
                        case 1: // Amazon
                            buyAmazon(dialogId);
                            break;
                    }
            	}
            }
            });
        return new AlertDialog.Builder(Profile.this).setTitle("Select Action").setView(mDialogList).create();
    }
    
    void buyAmazon(int type)
    {
        String query = null;
        int searchType = 0;
        if (type == DIALOG_ALBUM)
        {
            query = mAlbumInfo.getArtist() + " " + mAlbumInfo.getTitle();
            searchType = 1;
        } 
        else if( type == DIALOG_TRACK) 
        {
            query = mTrackInfo.getArtist().getName() + " " + mTrackInfo.getName();
            searchType = 0;
        }
        if( query != null ) {
            try {
                Intent intent = new Intent( Intent.ACTION_SEARCH );
                intent.setComponent(new ComponentName("com.amazon.mp3","com.amazon.mp3.android.client.SearchActivity"));
                intent.putExtra("actionSearchString", query);
                intent.putExtra("actionSearchType", searchType);
                startActivity( intent );
            } catch (Exception e) {
				LastFMApplication.getInstance().presentError(Profile.this, "Amazon Unavailable", "The Amazon MP3 store is not currently available on this device.");
            }
        }
    }
    
    void shareItem() {
        Intent intent = new Intent( this, Share.class );
        intent.putExtra(Share.INTENT_EXTRA_ARTIST, mTrackInfo.getArtist().getName());
        intent.putExtra(Share.INTENT_EXTRA_TRACK, mTrackInfo.getName());
        startActivity( intent );
    }
    
    void addItemToPlaylist() {
        Intent intent = new Intent( this, AddToPlaylist.class );
        intent.putExtra(Share.INTENT_EXTRA_ARTIST, mTrackInfo.getArtist().getName());
        intent.putExtra(Share.INTENT_EXTRA_TRACK, mTrackInfo.getName());
        startActivity( intent );
    }
    
    void tagItem(int type)
    {
        if( type != DIALOG_TRACK)
            return; //temporary until the Tag activity supports albums.
        String artist = null;
        String track = null;
        if (type == DIALOG_ALBUM)
        {
            artist = mAlbumInfo.getArtist();
                        
        } 
        else if( type == DIALOG_TRACK) 
        {
            artist = mTrackInfo.getArtist().getName();
            track = mTrackInfo.getName();
        }
        if( artist != null )
        {
            Intent myIntent = new Intent(this, fm.last.android.activity.Tag.class);
            myIntent.putExtra("lastfm.artist", artist);
            myIntent.putExtra("lastfm.track", track);
            startActivity(myIntent);
        }
    }
    
    void playSimilar(int type)
    {
        String artist = null;
        if (type == DIALOG_ALBUM)
        {
            artist = mAlbumInfo.getArtist();
                        
        } 
        else if( type == DIALOG_TRACK) 
        {
            artist = mTrackInfo.getArtist().getName();
        }
        
        if( artist != null)
            LastFMApplication.getInstance().playRadioStation(Profile.this, "lastfm://artist/"+Uri.encode(artist)+"/similarartists");
//        dismissDialog(type);
        
    }
    
    ArrayList<ListEntry> prepareProfileActions(int type)
    {
        ArrayList<ListEntry> iconifiedEntries = new ArrayList<ListEntry>(); 
        
        ListEntry entry = new ListEntry(R.string.dialog_similar, R.drawable.radio, getResources().getString(R.string.dialog_similar));
        iconifiedEntries.add(entry);

        if( type == DIALOG_TRACK) {
            entry = new ListEntry(R.string.dialog_share, R.drawable.share_dark, getResources().getString(R.string.dialog_share));
            iconifiedEntries.add(entry);

            entry = new ListEntry(R.string.dialog_tagtrack, R.drawable.tag_dark, getResources().getString(R.string.dialog_tagtrack));
            iconifiedEntries.add(entry);

            entry = new ListEntry(R.string.dialog_addplaylist, R.drawable.playlist_dark, getResources().getString(R.string.dialog_addplaylist));
            iconifiedEntries.add(entry);
        }

        entry = new ListEntry(R.string.dialog_amazon, R.drawable.shopping_cart_dark, getResources().getString(R.string.dialog_amazon)); // TODO need amazon icon
        iconifiedEntries.add(entry);
            
        return iconifiedEntries;        
    }
    
    private void logout()
    {
        SharedPreferences settings = getSharedPreferences( LastFm.PREFS, 0 );
        SharedPreferences.Editor editor = settings.edit();
        editor.remove( "lastfm_user" );
        editor.remove( "lastfm_pass" );
        editor.commit();
        SQLiteDatabase db = null;
        try
        {
            db = this.openOrCreateDatabase( LastFm.DB_NAME, MODE_PRIVATE, null );
            db.execSQL( "DROP TABLE IF EXISTS "
                            + LastFm.DB_TABLE_RECENTSTATIONS );
            db.close();

            IRadioPlayer player = LastFMApplication.getInstance().player;
            if(player != null) {
        		player.stop();
        		player.resetScrobbler();
            }
        }
        catch ( Exception e )
        {
            System.out.println( e.getMessage() );
        }
        Intent intent = new Intent( Profile.this, LastFm.class );
        startActivity( intent );
        finish();
    }
    
    private ImageCache getImageCache(){
        if(mImageCache == null){
            mImageCache = new ImageCache();
        }
        return mImageCache;
    }


}
