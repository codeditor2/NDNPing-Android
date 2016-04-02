/* -*- Mode:jde; c-file-style:"gnu"; indent-tabs-mode:nil; -*- */
/**
 * Copyright (c) 2015 Regents of the University of California
 *
 * This file is part of NFD (Named Data Networking Forwarding Daemon) Android.
 * See AUTHORS.md for complete list of NFD Android authors and contributors.
 *
 * NFD Android is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * NFD Android is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * NFD Android, e.g., in COPYING.md file.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.named_data.nfd;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import com.intel.jndn.utils.SimpleClient;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnTimeout;
import com.intel.jndn.management.types.FaceStatus;
import com.intel.jndn.management.types.RibEntry;

import net.named_data.nfd.utils.G;
import net.named_data.jndn.Face;
import java.util.ArrayList;


/**
 * Main activity that is loaded for the NFD app.
 */
public class MainActivity extends ActionBarActivity
    implements DrawerFragment.DrawerCallbacks,
               LogcatFragment.Callbacks,
               FaceListFragment.Callbacks,
               RouteListFragment.Callbacks,
               PingListFragment.Callbacks
{

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    FragmentManager fragmentManager = getSupportFragmentManager();

    if (savedInstanceState != null) {
      m_drawerFragment = (DrawerFragment)fragmentManager.findFragmentByTag(DrawerFragment.class.toString());
    }

    if (m_drawerFragment == null) {
      ArrayList<DrawerFragment.DrawerItem> items = new ArrayList<DrawerFragment.DrawerItem>();

      items.add(new DrawerFragment.DrawerItem(R.string.drawer_item_general, 0, DRAWER_ITEM_GENERAL));
      items.add(new DrawerFragment.DrawerItem(R.string.drawer_item_faces, 0, DRAWER_ITEM_FACES));
      items.add(new DrawerFragment.DrawerItem(R.string.drawer_item_routes, 0, DRAWER_ITEM_ROUTES));
      //    items.add(new DrawerFragment.DrawerItem(R.string.drawer_item_strategies, 0,
      //                                            DRAWER_ITEM_STRATEGIES));
      items.add(new DrawerFragment.DrawerItem(R.string.drawer_item_logcat, 0, DRAWER_ITEM_LOGCAT));
      items.add(new DrawerFragment.DrawerItem(R.string.drawer_item_ping, 0, DRAWER_ITEM_PING));

      m_drawerFragment = DrawerFragment.newInstance(items);

      fragmentManager
        .beginTransaction()
        .replace(R.id.navigation_drawer, m_drawerFragment, DrawerFragment.class.toString())
        .commit();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    G.Log("onCreateOptionsMenu" + String.valueOf(m_drawerFragment.shouldHideOptionsMenu()));
    if (!m_drawerFragment.shouldHideOptionsMenu()) {
      updateActionBar();
      return super.onCreateOptionsMenu(menu);
    }
    else
      return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    return super.onOptionsItemSelected(item);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Convenience method that updates and display the current title in the Action Bar
   */
  @SuppressWarnings("deprecation")
  private void updateActionBar() {
    ActionBar actionBar = getSupportActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    actionBar.setDisplayShowTitleEnabled(true);
    if (m_actionBarTitleId != -1) {
      actionBar.setTitle(m_actionBarTitleId);
    }
  }

  /**
   * Convenience method that replaces the main fragment container with the
   * new fragment and adding the current transaction to the backstack.
   *
   * @param fragment Fragment to be displayed in the main fragment container.
   */
  private void replaceContentFragmentWithBackstack(Fragment fragment) {
    FragmentManager fragmentManager = getSupportFragmentManager();
    fragmentManager.beginTransaction()
        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        .replace(R.id.main_fragment_container, fragment)
        .addToBackStack(null)
        .commit();
  }

  //////////////////////////////////////////////////////////////////////////////

  @Override
  public void
  onDrawerItemSelected(int itemCode, int itemNameId) {

    String fragmentTag = "net.named-data.nfd.content-" + String.valueOf(itemCode);
    FragmentManager fragmentManager = getSupportFragmentManager();

    // Create fragment according to user's selection
    Fragment fragment = fragmentManager.findFragmentByTag(fragmentTag);
    if (fragment == null) {
      switch (itemCode) {
        case DRAWER_ITEM_GENERAL:
          fragment = MainFragment.newInstance();
          break;
        case DRAWER_ITEM_FACES:
          fragment = FaceListFragment.newInstance();
          break;
        case DRAWER_ITEM_ROUTES:
          fragment = RouteListFragment.newInstance();
          break;
        // TODO: Placeholders; Fill these in when their fragments have been created
        //    case DRAWER_ITEM_STRATEGIES:
        //      break;
        case DRAWER_ITEM_LOGCAT:
          fragment = LogcatFragment.newInstance();
          break;
        case DRAWER_ITEM_PING:
	  Toast.makeText(MainActivity.this, "ping clicked", Toast.LENGTH_LONG).show();
	  fragment = PingListFragment.newInstance();
//         	NetThread thread = new NetThread();
// 		thread.start();
        default:
          // Invalid; Nothing else needs to be done
          return;
      }
    }

    // Update ActionBar title
    m_actionBarTitleId = itemNameId;

    fragmentManager.beginTransaction()
      .replace(R.id.main_fragment_container, fragment, fragmentTag)
      .commit();
  }
  
  	private class PingTimer implements OnData, OnTimeout {

		private long startTime;

		public void onData(Interest interest, Data data) {
			++ callbackCount_;

//			Log.i(TAG, "Got data packet with name " + data.getName().toUri());

			long elapsedTime = System.currentTimeMillis() - this.startTime;

			String name = data.getName().toUri();

			String pingTarget = name.substring(0, name.lastIndexOf("/"));

			String contentStr = pingTarget + ": " + String.valueOf(elapsedTime) + " ms";

//			Log.i(TAG, "Content " + contentStr);

			// Send a result to Screen

			Message msg = new Message();

			msg.what = 200; // Result Code ex) Success code: 200 , Fail Code:
							// 400 ...

			msg.obj = contentStr; // Result Object

			actionHandler.sendMessage(msg);

		}

		public int callbackCount_ = 0;

		public void onTimeout(Interest interest) {

			++callbackCount_;

//			Log.i(TAG, "Time out for interest " + interest.getName().toUri());

		}

		public void startUp() {
			startTime = System.currentTimeMillis();
		}

	}
  
  
	private class NetThread extends Thread {
		public NetThread() { }

		@Override
		public void run() {
		    Face face = new Face();
		    String pingName = "/ndn/org/caida/ping/" + Math.floor(Math.random() * 100000);
		    Name name = new Name(pingName);	    
		    // build interest
		    Interest interest = new Interest(name);
		    interest.setInterestLifetimeMilliseconds(2000);
		    interest.setMustBeFresh(true);	    
		    
		    System.out.println("Express name " + name.toUri());
		    PingTimer timer = new PingTimer();
		    timer.startUp();
		    try {
			face.expressInterest(interest, timer, timer);
		    } catch (IOException e) {
		      	System.out.println("IO failure while sending interest: ");
		    }
		    
		    // The main event loop.
		    while (timer.callbackCount_ < 1) {
		    try{
			Thread.sleep(5);
			} catch (InterruptedException exception) {
			System.out.println("IO failure while sending interest: ");
		    }
		    }

		}
	}
	
	private static Handler actionHandler = new Handler() {

		public void handleMessage(Message msg) {

			String viewMsg = "Empty";
			switch (msg.what) { // Result Code
			case 200: // Result Code Ex) Success: 200
				viewMsg = (String) msg.obj; // Result Data..
				break;
			default:
				viewMsg = "Error Code: " + msg.what;

				break;
			}			
//		      Toast.makeText(MainActivity.this, viewMsg, Toast.LENGTH_LONG).show();
		}
	};

  @Override
  public void onDisplayLogcatSettings() {
    replaceContentFragmentWithBackstack(LogcatSettingsFragment.newInstance());
  }

  @Override
  public void onFaceItemSelected(FaceStatus faceStatus) {
    replaceContentFragmentWithBackstack(FaceStatusFragment.newInstance(faceStatus));
  }

  @Override
  public void onRouteItemSelected(RibEntry ribEntry)
  {
    replaceContentFragmentWithBackstack(RouteInfoFragment.newInstance(ribEntry));
  }

  //////////////////////////////////////////////////////////////////////////////

  /** Reference to drawer fragment */
  private DrawerFragment m_drawerFragment;

  /** Title that is to be displayed in the ActionBar */
  private int m_actionBarTitleId = -1;

  /** Item code for drawer items: For use in onDrawerItemSelected() callback */
  public static final int DRAWER_ITEM_GENERAL = 1;
  public static final int DRAWER_ITEM_FACES = 2;
  public static final int DRAWER_ITEM_ROUTES = 3;
  public static final int DRAWER_ITEM_STRATEGIES = 4;
  public static final int DRAWER_ITEM_LOGCAT = 5;
  public static final int DRAWER_ITEM_PING = 6;
}
