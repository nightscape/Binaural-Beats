package com.ihunda.android.binauralbeat;

/*
 * @author Giorgio Regni
 * @contact @GiorgioRegni on Twitter
 * http://twitter.com/GiorgioRegni
 * 
 * This file is part of Binaural Beats Therapy or BBT.
 *
 *   BBT is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   BBT is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with BBT.  If not, see <http://www.gnu.org/licenses/>.
 *   
 *   BBT project home is at https://github.com/GiorgioRegni/Binaural-Beats
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.ihunda.android.binauralbeat.viz.Black;
import com.ihunda.android.binauralbeat.viz.GLBlack;

public class BBeat extends Activity {
	
	enum eState {START, RUNNING, END};
	enum appState {NONE, SETUP, INPROGRAM};
	
	private static final int MAX_STREAMS = 5;

	public static final float W_DELTA_FREQ = 2.00f;
	public static final float W_THETA_FREQ = 6.00f;
	public static final float W_ALPHA_FREQ = 10.00f;
	public static final float W_BETA_FREQ = 20.00f;
	public static final float W_GAMMA_FREQ = 60.00f;
	
	private static final long ANIM_TIME_MS = 600;
	
	private LinearLayout mPresetView;
	private LinearLayout mInProgram;
	private View mVizV;
	private FrameLayout mVizHolder;
	private TextView mStatus;
	private ListView mPresetList;
	private ToggleButton mPlayPause;
	
	private appState state;
	
	private int soundWhiteNoise;
	private int soundUnity;
	private SoundPool mSoundPool;
	
	private NotificationManager mNotificationManager;
	private static final int NOTIFICATION_STARTED = 1;
	
	private PowerManager mPm;
	private PowerManager.WakeLock  mWl;
	
	private Handler mHandler = new Handler();
	private RunProgram programFSM;
	private long pause_time = -1;
	
	private Vector<StreamVoice> playingStreams;
	private int playingBackground = -1;

	private SeekBar soundBeatV;
	private float mSoundBeatVolume;
	private SeekBar soundBGV;
	private float mSoundBGVolume;
	
	private static final String SOURCE_CODE_URL = "http://bit.ly/BBeats";
	private static final String BLOG_URL = "http://bit.ly/BBeatsBlog";
	private static final String HELP_URL = "http://bit.ly/BBeatsHelp";
	private static final String FORUM_URL = "http://bit.ly/BBTForum";
	private static final String FACEBOOK_URL = "http://www.facebook.com/pages/Binaural-Beat-Therapy/121737064536801";
	private static final String CONTACT_EMAIL = "binaural-beats@ihunda.com";
	private static final String LOGBBEAT = "BBT-MAIN";
	
	/* All dialogs declaration go here */
	private static final int DIALOG_WELCOME = 1;
	private static final int DIALOG_CONFIRM_RESET = 2;
	private static final int DIALOG_GETTING_INVOLVED = 3;
	private static final int DIALOG_JOIN_COMMUNITY = 4;
	private static final int DIALOG_PROGRAM_PREVIEW = 5;

	private static final float DEFAULT_VOLUME = 0.6f;

	private static final float BG_VOLUME_RATIO = 0.4f;
	

	private static final float FADE_INOUT_PERIOD = 2f;
	private static final float FADE_MIN = 0.7f;

	private static final String PREFS_NAME = "BBT";
	private static final String PREFS_VIZ = "VIZ";
	
	private VoicesPlayer vp;

	boolean glMode = false;
	boolean vizEnabled = true;
	
	
	/* 
	 * Not sure this is the best way to do it but it seems to work
	 * Some of the vizualisation need to get pointer to resources to load iamges, sounds, etc...
	 *  */
	private static BBeat instance;
	
	/*
	 * Keeps a reference to a selected program only for the purpose of the
	 * preview dialog, should always be null when the preview dialog is not
	 * displayed
	 */
	private static Program _tmp_program_holder;
	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        /* Init sounds */
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        /*
         * Sets up power management, device should not go to sleep during a program
         */
        mPm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWl = mPm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "BBTherapy");
        
        /* Setup all buttons */
        Button b = (Button) findViewById(R.id.MenuForum);
        b.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				gotoForum();
			}
		});
        
        b = (Button) findViewById(R.id.MenuHelp);
        b.setOnClickListener(new OnClickListener() {
			
			public void onClick(View v) {
				gotoHelp();
			}
		});
        
        b = (Button) findViewById(R.id.Menu);
        b.setOnClickListener(new OnClickListener() {
			
			public void onClick(View v) {
				openOptionsMenu();
			}
		});
        
        b = (Button) findViewById((R.id.likeButton));
        b.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		gotoFacebook();
        	}
        });
        
        TextView t = (TextView) findViewById((R.id.jointhecommunityText));
        t.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		showDialog(DIALOG_JOIN_COMMUNITY);
        	}
        });
        
        pause_time = -1;
        mPlayPause = (ToggleButton) findViewById((R.id.MenuPause));
        mPlayPause.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				pauseOrResume();
			}
        	
        });

        /* Set up volume bar */
        soundBeatV = (SeekBar) findViewById((R.id.soundVolumeBar));
        soundBeatV.setMax(100);
        mSoundBeatVolume = DEFAULT_VOLUME;
        soundBeatV.setProgress((int) (mSoundBeatVolume*100));
        soundBeatV.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			public void onStopTrackingTouch(SeekBar seekBar) {
			}
			
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				mSoundBeatVolume = ((float) progress)/100.f;
				resetAllVolumes();
			}
		});
        /* Set up background volume bar */
        soundBGV = (SeekBar) findViewById((R.id.soundBGVolumeBar));
        soundBGV.setMax(100);
        mSoundBGVolume = mSoundBeatVolume * BG_VOLUME_RATIO;
        soundBGV.setProgress((int) (mSoundBGVolume*100));
        soundBGV.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
        	public void onStopTrackingTouch(SeekBar seekBar) {
        	}
        	public void onStartTrackingTouch(SeekBar seekBar) {
        	}
        	public void onProgressChanged(SeekBar seekBar, int progress,

        			boolean fromUser) {

        		mSoundBGVolume = ((float) progress)/100.f;

        		resetAllVolumes();
        	}
        });
        
        mInProgram = (LinearLayout) findViewById(R.id.inProgramLayout);
        mPresetView = (LinearLayout) findViewById(R.id.presetLayout);

        mVizHolder = (FrameLayout) findViewById(R.id.VisualizationView);
        mStatus = (TextView) findViewById(R.id.Status);
        
        // Set a static pointer to this instance so that vizualisation can access it
        setInstance(this);
        
        
        mPresetList = (ListView) findViewById(R.id.presetListView);
        final List<String> programs = new ArrayList<String>(DefaultProgramsBuilder.getProgramMethods(this).keySet());
        programs.add(getString(R.string.getting_involved));

		mPresetList.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, programs));
        mPresetList.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				selectProgram(programs.get(arg2));
			}
		});
        
        showDialog(DIALOG_WELCOME);
        
        _load_config();
        
        initSounds();
        
        state = appState.NONE;
        goToState(appState.SETUP);
    }
    
    /*
     * Takes the current setup and saves it to preference for next start
     */
    private void _save_config() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PREFS_VIZ, vizEnabled);
    }
    
    private void _load_config() {
    	SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
    	vizEnabled = settings.getBoolean(PREFS_VIZ, true);
    }
    
    void initSounds() {
		if (mSoundPool != null) {
			mSoundPool.release();
			mSoundPool = null;
		}
		
        mSoundPool = new SoundPool(MAX_STREAMS, AudioManager.STREAM_MUSIC, 0);
        soundWhiteNoise = mSoundPool.load(this, R.raw.whitenoise, 1);
        soundUnity = mSoundPool.load(this, R.raw.unity, 1);
		
        playingStreams = new Vector<StreamVoice>(MAX_STREAMS);
        playingBackground = -1;
    }
    
    void startVoicePlayer() {
        if (vp == null) {
        	vp =  new VoicesPlayer();
        	vp.start();
        }
    }
    
    void stopVoicePlayer() {
		vp.shutdown();
		vp = null;
    }
    
	@Override
	protected void onStart() {
		super.onStart();
	}
	
	@Override
	protected void onStop() {
		super.onStop();
	}
    
	 @Override
	    public boolean onKeyDown(int keyCode, KeyEvent event)  {
	        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
	            // ask for confirmation during workout
	        	if (state == appState.INPROGRAM) {
	        		showDialog(DIALOG_CONFIRM_RESET);
	        		return true;
	        	}
	        }

	        return super.onKeyDown(keyCode, event);
	    }
	 
	 public boolean onCreateOptionsMenu(Menu menu) {
		    MenuInflater inflater = getMenuInflater();
		    inflater.inflate(R.menu.inprogram, menu);
		    
		    return true;
		}
		
		@Override public boolean onPrepareOptionsMenu(Menu menu) {
			if (state != appState.INPROGRAM)
				return false;
			
			return true;
		}
	    	
		
		/* Handles item selections */
		public boolean onOptionsItemSelected(MenuItem item) {
			switch (item.getItemId()) {
				case R.id.stop: {
					showDialog(DIALOG_CONFIRM_RESET);
					return true;
				}
				case R.id.togglegraphics:
					setGraphicsEnabled(!vizEnabled);
					break;
			}
			return false;
		}
    

	private void goToState(appState newState) {
		switch(state) {
		case NONE:
			mInProgram.setVisibility(View.GONE);
			mPresetView.setVisibility(View.GONE);
			break;
		case INPROGRAM:
	    	if (mWl.isHeld())
	    		mWl.release();

			mVizHolder.removeAllViews();
			mVizV = null;

			runGoneAnimationOnView(mInProgram);
			_cancel_all_notifications();
						
			stopVoicePlayer();
			
			/* Reinit all sounds */
			initSounds();
			break;
		case SETUP:
			runGoneAnimationOnView(mPresetView);
			break;
		default:
			break;
		}
		
		state = newState;
		switch(state) {
		case SETUP:
			runComeBackAnimationOnView(mPresetView);
			mPresetList.invalidate();
			mVizHolder.setVisibility(View.GONE);
			break;
		case INPROGRAM:
			// Start voice player thread
			startVoicePlayer();
			
			// Acquire power management lock
			if (vizEnabled)
				mWl.acquire();
			
			_start_notification(programFSM.getProgram().getName());
			runComeBackAnimationOnView(mInProgram);
			mVizHolder.setVisibility(View.VISIBLE);

			glMode = programFSM.pR.doesUseGL();
			if (glMode)
				mVizV = new GLVizualizationView(getBaseContext());
			else
				mVizV = new CanvasVizualizationView(getBaseContext());
			mVizHolder.addView(mVizV);
			
			mPlayPause.setChecked(true);
			pause_time = -1;
			break;
		}
	}
	
    private boolean isPaused() {
    	if (pause_time > 0)
    		return true;
    	else
    		return false;
    }
    
    private void pauseOrResume() {
    	if (state == appState.INPROGRAM) {
    		if (pause_time > 0) {
    			long delta = System.currentTimeMillis() - pause_time;
    			programFSM.catchUpAfterPause(delta);
    			pause_time = -1;
    			unmuteAll();

    		} else {
    			/* This is a pause time */
    			pause_time = System.currentTimeMillis();
    			muteAll();
    		}
    	}
    }
    
    private void setGraphicsEnabled(boolean on) {
    	if (state == appState.INPROGRAM) {
    		if (vizEnabled && on == false) {
    			// Disable Viz
    			Period p = programFSM.getCurrentPeriod();
    			Visualization v;
    			
    			if (((Object) mVizV).getClass() == GLVizualizationView.class)
    				v = new GLBlack();
    			else
    				v = new Black();
    			
    			((VizualisationView) mVizV).stopVisualization();
    			((VizualisationView) mVizV).startVisualization(v, p.getLength());
    			((VizualisationView) mVizV).setFrequency(p.getVoices().get(0).freqStart);
    			vizEnabled = false;
    			
    			if (mWl.isHeld())
    	    		mWl.release();
    			
    			ToastText(R.string.graphics_off);
    		} else
    		if (!vizEnabled && on == true) {
    			// Enable viz
    			Period p = programFSM.getCurrentPeriod();
    			((VizualisationView) mVizV).stopVisualization();
    			((VizualisationView) mVizV).startVisualization(p.getV(), p.getLength());
    			((VizualisationView) mVizV).setFrequency(p.getVoices().get(0).freqStart);
    			vizEnabled = true;
    			
    			if (mWl.isHeld() == false)
    				mWl.acquire();
    			
    			ToastText(R.string.graphics_on);
    		}
    	}
    	_save_config();
    }

	@Override
	protected void onDestroy() {
		panic();
		_cancel_all_notifications();
    	
		super.onDestroy();
	}


	@Override
	protected void onPause() {
		super.onPause();
	}


	@Override
	protected void onResume() {
		super.onResume();
	}
    
	@Override
	protected Dialog onCreateDialog(int id) {
		
		switch(id) {
		case DIALOG_WELCOME: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.welcome_text)
			.setCancelable(true)
			.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			});

			AlertDialog alert = builder.create();
			return alert;
		}
		
		case DIALOG_CONFIRM_RESET: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    	builder.setMessage(R.string.confirm_reset)
	    	       .setCancelable(true)
	    	       .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
	    	           public void onClick(DialogInterface dialog, int id) {
	    	        	   BBeat.this.stopProgram();
	    	           }
	    	       })
	    	       .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
	    	           public void onClick(DialogInterface dialog, int id) {
	    	                dialog.cancel();
	    	           }
	    	       });
	    	AlertDialog alert = builder.create();
	    	return alert;
		}
		
		case DIALOG_GETTING_INVOLVED: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    	builder.setMessage(R.string.getting_involved_dialog)
	    	       .setCancelable(true)
	    	       .setPositiveButton(R.string.contact, new DialogInterface.OnClickListener() {
	    	           public void onClick(DialogInterface dialog, int id) {
	    	        	   shareWith(getString(R.string.app_name), getString(R.string.share_text));
	    	           }
	    	       })
	    	       .setNegativeButton(R.string.rate_on_market, new DialogInterface.OnClickListener() {
	    	           public void onClick(DialogInterface dialog, int id) {
	    	                gotoMarket();
	    	           }
	    	       });
	    	AlertDialog alert = builder.create();
	    	return alert;
		}
		
		case DIALOG_JOIN_COMMUNITY: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    	builder.setMessage(R.string.getting_involved_dialog)
	    	       .setCancelable(true)
	    	       .setPositiveButton(R.string.contact, new DialogInterface.OnClickListener() {
	    	           public void onClick(DialogInterface dialog, int id) {
	    	        	   shareWith(getString(R.string.app_name), getString(R.string.share_text));
	    	           }
	    	       })
	    	       .setNegativeButton(R.string.blog, new DialogInterface.OnClickListener() {
	    	           public void onClick(DialogInterface dialog, int id) {
	    	                gotoBlog();
	    	           }
	    	       });
	    	AlertDialog alert = builder.create();
	    	return alert;
		}
		
		case DIALOG_PROGRAM_PREVIEW: {
			Program p = _tmp_program_holder;
			if (p == null)
				return null;
			
			int length = p.getLength();
			
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(p.getName())
			.setIcon(android.R.drawable.ic_dialog_info)
			.setMessage(p.getDescription() + " " + p.getAuthor() +
			String.format(" %sh%smin.",
					formatTimeNumberwithLeadingZero(length/60/60),
					formatTimeNumberwithLeadingZero((length/60)%60))		
			)
	    	       .setCancelable(true)
	    	       .setPositiveButton(R.string.start, new DialogInterface.OnClickListener() {
	    	           public void onClick(DialogInterface dialog, int id) {
	    	        	   StartPreviouslySelectedProgram();
	    	        	   removeDialog(DIALOG_PROGRAM_PREVIEW);
	    	           }
	    	       })
	    	       .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
	    	           public void onClick(DialogInterface dialog, int id) {
	    	        	   removeDialog(DIALOG_PROGRAM_PREVIEW);
	    	           }
	    	       });
	    	AlertDialog alert = builder.create();
	    	return alert;
		}
		
		}
		
		return null;
	};
	
	private void panic() {
		// Stop all sounds
		for (StreamVoice v: playingStreams)
			mSoundPool.stop(v.streamID);
		playingStreams.clear();
		if (vp != null)
			vp.stopVoices();
	}
	
    private void _start_notification(String programName) {
    	Notification notification = new Notification(R.drawable.icon, getString(R.string.notif_started), System.currentTimeMillis());
    	
    	Context context = getApplicationContext();
    	CharSequence contentTitle = getString(R.string.notif_started);
    	CharSequence contentText = getString(R.string.notif_descr, programName);
    	Intent notificationIntent = this.getIntent(); //new Intent(this, hiit.class);
    	PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

    	notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
    	notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
    	
    	mNotificationManager.notify(NOTIFICATION_STARTED, notification);
    }
    
	
    private void _cancel_all_notifications() {
    	mNotificationManager.cancelAll();
    }
	
	private void selectProgram(String name) {
		if (programFSM != null)
			programFSM.stopProgram();
		
		
		if (name.equals(getString(R.string.getting_involved)))
		{
			// Special hook to show getting involved dialog
			showDialog(DIALOG_GETTING_INVOLVED);
			return;
		}
		

		Program p = DefaultProgramsBuilder.getProgram(name, this);
		_tmp_program_holder = p;
		
		showDialog(DIALOG_PROGRAM_PREVIEW);
	}
	
	private void StartPreviouslySelectedProgram() {
		Program p = _tmp_program_holder;
		_tmp_program_holder = null;
		
		((TextView) findViewById(R.id.programName)).setText(p.getName());
		
		programFSM = new RunProgram(p, mHandler);
		goToState(appState.INPROGRAM);
	}
	
	private void stopProgram() {
		if (programFSM != null) {
			programFSM.stopProgram();
			programFSM = null;
		}
		panic();
		goToState(appState.SETUP);
	}
	
	int play(int soundID, float leftVolume, float rightVolume, int priority, int loop, float rate) {
		int id = mSoundPool.play(soundID, leftVolume * mSoundBeatVolume, rightVolume * mSoundBeatVolume,
				priority, loop, rate);
		
		/*
		 * Record all playing stream ids to be able to stop sound on pause/panic
		 */
		playingStreams.add(new StreamVoice(id, leftVolume, rightVolume, loop, rate));
		if (playingStreams.size() > MAX_STREAMS) {
			 StreamVoice v = playingStreams.remove(0);
			 mSoundPool.stop(v.streamID);
		}
		
		return id;
	}
    
	void stop(int soundID) {
		mSoundPool.stop(soundID);
		playingStreams.removeElement(new Integer(soundID));
	}
	
	/**
	 * Loop through all playing voices and set regular volume back
	 */
	void resetAllVolumes() {
		if (playingStreams != null &&
				mSoundPool != null)
			for (StreamVoice v: playingStreams) {
				if (v.streamID == playingBackground)
					mSoundPool.setVolume(v.streamID, v.leftVol * mSoundBGVolume, v.rightVol * mSoundBGVolume);
				else
					mSoundPool.setVolume(v.streamID, v.leftVol * mSoundBeatVolume, v.rightVol * mSoundBeatVolume);
			}
		if (vp != null)
			vp.setVolume(mSoundBeatVolume);
	}
	
	/**
	 * Loop through all playing voices and lower volume to 0 but do not stop
	 */
	void muteAll() {
		if (playingStreams != null &&
				mSoundPool != null)
			for (StreamVoice v: playingStreams) {
				mSoundPool.setVolume(v.streamID, 0, 0);
			}
		vp.setVolume(0);
	}
	
	/**
	 * Loop through all playing voices and set volume back
	 */
	void unmuteAll() {
		resetAllVolumes();
	}
	
	private void playBackgroundSample(SoundLoop background, float vol) {
		
		switch(background) {
		case WHITE_NOISE:
			playingBackground = play(soundWhiteNoise, vol, vol, 2, -1, 1.0f);
			break;
		case UNITY:
			playingBackground = play(soundUnity, vol, vol, 2, -1, 1.0f);
			break;
		case NONE:
			playingBackground = -1;
			break;
		default:
			playingBackground = -1;
			break;
		}
		
		if (playingBackground != -1)
			mSoundPool.setVolume(playingBackground, vol * mSoundBGVolume, vol * mSoundBGVolume);
	}
	
	private void stopBackgroundSample() {
		if (playingBackground != -1)
			stop(playingBackground);
		playingBackground = -1;
	}
	
	/**
	 * Go through a list of voices and start playing them with their start frequency
	 * @param voices list of voices to play
	 */
	protected void playVoices(ArrayList<BinauralBeatVoice> voices) {
		vp.playVoices(voices);
		vp.setVolume(mSoundBeatVolume);
		
	}
	
	/**
	 * @param voices
	 * @param pos
	 * @param length
	 * @return beat frequency of first voice
	 */
	protected float skewVoices(ArrayList<BinauralBeatVoice> voices, float pos, float length, boolean doskew) {
		int i = 0;
		float res = 1;
		
		float freqs[] = new float[voices.size()];
		for (BinauralBeatVoice v: voices) {
			float ratio = (v.freqEnd - v.freqStart) / length;
			
			res = ratio*pos + v.freqStart;
			
			freqs[i] = res;
				
			i++;
		}
		if (doskew) {
			if (pos < FADE_INOUT_PERIOD)
				vp.setFade(FADE_MIN + pos/FADE_INOUT_PERIOD*(1-FADE_MIN));
			else if (length - pos < FADE_INOUT_PERIOD) {
				float fade = FADE_MIN + (length-pos)/FADE_INOUT_PERIOD*(1-FADE_MIN);
				if (fade < FADE_MIN)
					fade = FADE_MIN;
				vp.setFade(fade);
			}
			else
				vp.setFade(1f);
			
			vp.setFreqs(freqs);
		}	
		
		return res;
	}
	
	/**
	 * Go through all currently running beat voices and stop them
	 */
	protected void stopAllVoices() {
		vp.stopVoices();
	}
	
	class RunProgram implements Runnable {

		private static final long TIMER_FSM_DELAY = 1000 / 20;
		
		private Program pR; 
		private Iterator<Period> periodsIterator;
		private Period currentPeriod;
		private long cT; // current Period start time
		private long startTime;
		private long programLength;
		private String sProgramLength;
		private String formatString;
		private long oldDelta; // Utilized to reduce the amount of redraw for the program legend
		private eState s;
		private Handler h;
		
		public RunProgram(Program pR, Handler h) {
			this.pR = pR;
			this.h = h;
			
			programLength = pR.getLength();
			sProgramLength = getString(R.string.time_format, 
					formatTimeNumberwithLeadingZero((int) programLength/60),
					formatTimeNumberwithLeadingZero((int) programLength%60));
			formatString = getString(R.string.info_timing);
			startTime = System.currentTimeMillis();
			oldDelta = -1;
			
			s = eState.START;
			
			h.postDelayed(this, TIMER_FSM_DELAY);
		}
		
		public Period getCurrentPeriod() {
			return currentPeriod;
		}
		
		public void stopProgram() {
			stopAllVoices();
			endPeriod();
			h.removeCallbacks(this);
		}
		
		private void startPeriod(Period p) {
			if (vizEnabled)
				((VizualisationView) mVizV).startVisualization(p.getV(), p.getLength());
			else
				((VizualisationView) mVizV).startVisualization(new Black(), p.getLength());
			
			((VizualisationView) mVizV).setFrequency(p.getVoices().get(0).freqStart);
			playVoices(p.voices);
			vp.setFade(FADE_MIN);
			playBackgroundSample(p.background, p.getBackgroundvol());
			
			Log.v(LOGBBEAT, String.format("New Period - duration %d", p.length));
		}

		private void inPeriod(long now, Period p, float pos) {
			long delta = (now - startTime) / 20; // Do not refresh too often
			
			float freq = skewVoices(p.voices, pos, p.length, oldDelta != delta);
			
			((VizualisationView) mVizV).setFrequency(freq);
			((VizualisationView) mVizV).setProgress(pos);
			
			if (oldDelta != delta) {
				oldDelta = delta;
				delta = delta/50; // Down to seconds
				mStatus.setText(String.format(formatString, 
						freq,
						formatTimeNumberwithLeadingZero((int) delta/60),
						formatTimeNumberwithLeadingZero((int) delta%60)
				)
				+
				sProgramLength
				);
			}
		}
		
		private void endPeriod() {
			//stopAllVoices();
			stopBackgroundSample();
			((VizualisationView) mVizV).stopVisualization();
		}
		
		public void catchUpAfterPause(long delta) {
			cT += delta;
		}
		
		public void run() {
			long now = System.currentTimeMillis();
			
			switch(s) {
			case START:
				s = eState.RUNNING;
				periodsIterator = pR.getPeriodsIterator();
				cT = now;
				nextPeriod();
			break;
			
			case RUNNING:
				if (isPaused())
					break;
				
				float pos = (now - cT) / 1000f;
				
				if (pos > currentPeriod.length) {
					endPeriod();
					
					// Current period is over
					if (!periodsIterator.hasNext()) {
						// Finished
						s = eState.END;
					} else {
						// this is a new period
						cT = now;
						nextPeriod();
					}
				} else {
					/**
					 * In the middle of current period, adjust each beat voice
					 */
					inPeriod(now, currentPeriod, pos);
				}
				break;
				
			case END:
				BBeat.this.stopProgram();
				return;
			}
			
			h.postDelayed(this, TIMER_FSM_DELAY);
		}

		private void nextPeriod() {
			currentPeriod = periodsIterator.next();
			startPeriod(currentPeriod);
		}

		public Program getProgram() {
			return pR;
		}
	}

	public Animation runGoneAnimationOnView(View target) {
  	  Animation animation = AnimationUtils.loadAnimation(this,
  	                                                     android.R.anim.slide_out_right );
  	  animation.setDuration(ANIM_TIME_MS);
  	  final View mTarget = target;
  	  animation.setAnimationListener(new AnimationListener() {

			public void onAnimationEnd(Animation animation) {
				mTarget.setVisibility(View.GONE);
			}

			public void onAnimationRepeat(Animation animation) {
			}

			public void onAnimationStart(Animation animation) {
			}
  		  
  	  });
  	  target.startAnimation(animation);
  	  
  	  return animation;
  	}
  
  public Animation runComeBackAnimationOnView(View target) {
	  Animation animation = AnimationUtils.loadAnimation(this,
	                                                     android.R.anim.slide_in_left );
	  animation.setDuration(ANIM_TIME_MS);
	  final View mTarget = target;

	  mTarget.setVisibility(View.VISIBLE); // to be compatible with Android 1.5
		
	  animation.setAnimationListener(new AnimationListener() {

			public void onAnimationEnd(Animation animation) {
			}

			public void onAnimationRepeat(Animation animation) {
			}

			public void onAnimationStart(Animation animation) {
			}
		  
	  });
	  target.startAnimation(animation);
	  
	  return animation;
	}
  
	private String formatTimeNumberwithLeadingZero(int t) {
    	if (t > 9)
    		return String.format("%2d", t);
    	else
    		return String.format("0%1d", t);
    }
	
    private void gotoBlog() {
    	gotoURL(BLOG_URL);
    }
    
    @SuppressWarnings("unused")
	private void gotoSourceCode() {
    	gotoURL(SOURCE_CODE_URL);
    }
    
    private void gotoForum() {
    	gotoURL(FORUM_URL);
    }
    
    private void gotoFacebook() {
    	gotoURL(FACEBOOK_URL);
    }
    
    private void gotoHelp() {
    	/*Intent i = new Intent(this, Comments.class);
    	i.putExtra("ID", "yoyoma");
    	startActivity(i);*/
    	gotoURL(HELP_URL);
    }
    
    private void gotoMarket() {
    	gotoURL("market://details?id=com.ihunda.android.binauralbeat");
    }
    
    private void gotoURL(String URL) {
    	try {
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(URL));
			startActivity(i);
		}
		catch(Exception e) {
			
		}
    }
    
    public void shareWith(String subject,String text) {
    	 final Intent intent = new Intent(Intent.ACTION_SEND);

    	 String aEmailList[] = {CONTACT_EMAIL};  
    	 intent.setType("text/plain");
    	 intent.putExtra(android.content.Intent.EXTRA_EMAIL, aEmailList);  
    	 intent.putExtra(Intent.EXTRA_SUBJECT, subject);
    	 intent.putExtra(Intent.EXTRA_TEXT, text);

    	 startActivity(Intent.createChooser(intent, getString(R.string.share)));
    	}

	private static void setInstance(BBeat instance) {
		BBeat.instance = instance;
	}

	public static BBeat getInstance() {
		return instance;
	}
	
	public  String readRawTextFile(int resId)
    {
         InputStream inputStream = this.getResources().openRawResource(resId);

            InputStreamReader inputreader = new InputStreamReader(inputStream);
            BufferedReader buffreader = new BufferedReader(inputreader);
             String line;
             StringBuilder text = new StringBuilder();

             try {
               while (( line = buffreader.readLine()) != null) {
                   text.append(line);
                   text.append('\n');
                 }
           } catch (IOException e) {
               return null;
           }
             return text.toString();
    }
	
	private void ToastText(int id) {
		Toast.makeText(this, getString(id), Toast.LENGTH_SHORT).show();
	}
	
	
}