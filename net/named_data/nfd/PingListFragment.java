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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.util.Pair;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.intel.jndn.management.types.FaceStatus;

import net.named_data.jndn_xx.util.FaceUri;
import net.named_data.nfd.utils.G;
import net.named_data.nfd.utils.Nfdc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PingListFragment extends ListFragment {

  public static PingListFragment
  newInstance() {
    return new PingListFragment();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setHasOptionsMenu(true);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);

    @SuppressLint("InflateParams")
    View v = getLayoutInflater(savedInstanceState).inflate(R.layout.fragment_ping_list_list_header, null);
    getListView().addHeaderView(v, null, false);
    getListView().setDivider(getResources().getDrawable(R.drawable.list_item_divider));

    // Get info unavailable view
    m_pingListInfoUnavailableView = v.findViewById(R.id.ping_list_info_unavailable);

    // Get progress bar spinner view
    m_reloadingListProgressBar = (ProgressBar)v.findViewById(R.id.ping_list_reloading_list_progress_bar);

    // Setup list view for deletion
    ListView listView = getListView();
    listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState)
  {
    super.onActivityCreated(savedInstanceState);

    if (m_pingListAdapter == null) {
      m_pingListAdapter = new PingListAdapter(getActivity());
    }
    setListAdapter(m_pingListAdapter);
  }

  @Override
  public void onDestroyView()
  {
    super.onDestroyView();
    setListAdapter(null);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
  {
    super.onCreateOptionsMenu(menu, inflater);
    inflater.inflate(R.menu.menu_ping_list, menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    switch (item.getItemId()) {
      case R.id.ping_list_refresh:
        retrievePingList();
        return true;
      case R.id.ping_list_add:
//         FaceCreateDialogFragment dialog = FaceCreateDialogFragment.newInstance();
//         dialog.setTargetFragment(PingListFragment.this, 0);
//         dialog.show(getFragmentManager(), "FaceCreateFragment");
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onResume() {
    super.onResume();
    startPingListRetrievalTask();
  }

  @Override
  public void onPause() {
    super.onPause();
    stopPingListRetrievalTask();

    if (m_pingDestroyAsyncTask != null) {
      m_pingDestroyAsyncTask.cancel(false);
      m_pingDestroyAsyncTask = null;
    }
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    try {
      m_callbacks = (Callbacks)activity;
    } catch (Exception e) {
      G.Log("Hosting activity must implement this fragment's callbacks: " + e);
    }
  }

  @Override
  public void onDetach() {
    super.onDetach();
    m_callbacks = null;
  }

  @Override
  public void
  onListItemClick(ListView l, View v, int position, long id) {
    if (m_callbacks != null) {
      FaceStatus faceStatus = (FaceStatus)l.getAdapter().getItem(position);
      m_callbacks.onFaceItemSelected(faceStatus);
    }
  }

  /////////////////////////////////////////////////////////////////////////

  /**
   * Updates the underlying adapter with the given list of FaceStatus.
   *
   * Note: This method should only be called from the UI thread.
   *
   * @param list Update ListView with the given List&lt;FaceStatus&gt;
   */
  private void updatePingList(List<FaceStatus> list) {
    if (list == null) {
      m_pingListInfoUnavailableView.setVisibility(View.VISIBLE);
      return;
    }

    ((PingListAdapter)getListAdapter()).updateList(list);
  }

  /**
   * Convenience method that starts the AsyncTask that retrieves the
   * list of available faces.
   */
  private void retrievePingList() {
    // Update UI
    m_pingListInfoUnavailableView.setVisibility(View.GONE);

    // Stop if running; before starting the new Task
    stopPingListRetrievalTask();
    startPingListRetrievalTask();
  }

  /**
   * Create a new AsyncTask for face list information retrieval.
   */
  private void startPingListRetrievalTask() {
    m_pingListAsyncTask = new PingListAsyncTask();
    m_pingListAsyncTask.execute();
  }

  /**
   * Stops a previously started face retrieval AsyncTask.
   */
  private void stopPingListRetrievalTask() {
    if (m_pingListAsyncTask != null) {
      m_pingListAsyncTask.cancel(false);
      m_pingListAsyncTask = null;
    }
  }

  /**
   * Custom adapter for displaying face information in a ListView.
   */
  private static class PingListAdapter extends BaseAdapter {
    private PingListAdapter(Context context) {
      m_layoutInflater = LayoutInflater.from(context);
    }

    private void
    updateList(List<FaceStatus> faces) {
      m_faces = faces;
      notifyDataSetChanged();
    }

    @Override
    public FaceStatus
    getItem(int i)
    {
      assert m_faces != null;
      return m_faces.get(i);
    }

    @Override
    public long getItemId(int i)
    {
      assert m_faces != null;
      return m_faces.get(i).getFaceId();
    }

    @Override
    public int getCount()
    {
      return (m_faces != null) ? m_faces.size() : 0;
    }

    @SuppressLint("InflateParams")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      FaceInfoHolder holder;

      if (convertView == null) {
        holder = new FaceInfoHolder();

        convertView = m_layoutInflater.inflate(R.layout.list_item_face_status_item, null);
        convertView.setTag(holder);

        holder.m_faceUri = (TextView)convertView.findViewById(R.id.list_item_face_uri);
        holder.m_faceId = (TextView)convertView.findViewById(R.id.list_item_face_id);
      } else {
        holder = (FaceInfoHolder)convertView.getTag();
      }

      FaceStatus info = getItem(position);
      holder.m_faceUri.setText(info.getUri());
      holder.m_faceId.setText(String.valueOf(info.getFaceId()));

      return convertView;
    }

    private static class FaceInfoHolder {
      private TextView m_faceUri;
      private TextView m_faceId;
    }

    private final LayoutInflater m_layoutInflater;
    private List<FaceStatus> m_faces;
  }

  /**
   * AsyncTask that gets the list of faces from the running NFD.
   */
  private class PingListAsyncTask extends AsyncTask<Void, Void, Pair<List<FaceStatus>, Exception>> {
    @Override
    protected void
    onPreExecute() {
      // Display progress bar
      m_reloadingListProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    protected Pair<List<FaceStatus>, Exception>
    doInBackground(Void... params) {
      Exception returnException = null;
      Nfdc nfdc = new Nfdc();
      List<FaceStatus> faceStatusList = null;
      try {
        faceStatusList = nfdc.faceList();
      } catch (Exception e) {
        returnException = e;
      }
      nfdc.shutdown();
      return new Pair<>(faceStatusList, returnException);
    }

    @Override
    protected void
    onCancelled() {
      // Remove progress bar
      m_reloadingListProgressBar.setVisibility(View.GONE);

      // Nothing to do here; No change in UI.
    }

    @Override
    protected void
    onPostExecute(Pair<List<FaceStatus>, Exception> result) {
      // Remove progress bar
      m_reloadingListProgressBar.setVisibility(View.GONE);

      if (result.second != null) {
        Toast.makeText(getActivity(), "Error communicating with NFD (" + result.second.getMessage() + ")",
                       Toast.LENGTH_LONG).show();
      }

      updatePingList(result.first);
    }
  }

  /**
   * AsyncTask that destroys faces that are passed in as a list of FaceInfo.
   */
  private class PingDestroyAsyncTask extends AsyncTask<Set<Integer>, Void, Exception> {
    @Override
    protected void
    onPreExecute() {
      // Display progress bar
      m_reloadingListProgressBar.setVisibility(View.VISIBLE);
    }

    @SafeVarargs
    @Override
    protected final Exception
    doInBackground(Set<Integer>... params) {
      Exception retval = null;

      Nfdc nfdc = new Nfdc();
      try {
        for (Set<Integer> faces : params) {
          for (int faceId : faces) {
//            nfdc.faceDestroy(faceId);
          }
        }
      } catch (Exception e) {
        retval = e;
      }
      nfdc.shutdown();

      return retval;
    }

    @Override
    protected void onCancelled() {
      // Remove progress bar
      m_reloadingListProgressBar.setVisibility(View.GONE);

      // Nothing to do here; No change in UI.
    }

    @Override
    protected void onPostExecute(Exception e) {
      // Remove progress bar
      m_reloadingListProgressBar.setVisibility(View.GONE);

      if (e != null) {
        Toast.makeText(getActivity(), "Error communicating with NFD (" + e.getMessage() + ")",
                       Toast.LENGTH_LONG).show();
      }
      else {
        // Reload ping list
        retrievePingList();
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////

  public interface Callbacks {
    /**
     * This method is called when a face is selected and more
     * information about the face should be presented to the user.
     *
     * @param faceStatus FaceStatus instance with information about the face
     */
    public void onFaceItemSelected(FaceStatus faceStatus);
  }

  /////////////////////////////////////////////////////////////////////////

  /** Reference to the most recent AsyncTask that was created for listing faces */
  private PingListAsyncTask m_pingListAsyncTask;

  /** Callback handler of the hosting activity */
  private Callbacks m_callbacks;

  /** Reference to the most recent AsyncTask that was created for destroying faces */
  private PingDestroyAsyncTask m_pingDestroyAsyncTask;

  /** Reference to the view to be displayed when no information is available */
  private View m_pingListInfoUnavailableView;

  /** Progress bar spinner to display to user when destroying faces */
  private ProgressBar m_reloadingListProgressBar;

  private PingListAdapter m_pingListAdapter;
}
