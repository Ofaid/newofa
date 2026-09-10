/*
 * Copyright (C) 2014 Andrew Comminos
 * Lisensi tetap sama...
 */
package ofaid.ahmad.ptt.servers;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.util.List;

import se.lublin.humla.model.Server;
import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.db.DatabaseProvider;
import ofaid.ahmad.ptt.db.PublicServer;
import android.webkit.WebView;
import android.webkit.WebSettings;

public class FavouriteServerListFragment extends Fragment implements AdapterView.OnItemClickListener, FavouriteServerAdapter.FavouriteServerAdapterMenuListener {

    private static final String TAG = "FavServerList";
    
    private ServerConnectHandler mConnectHandler;
    private DatabaseProvider mDatabaseProvider;
    private GridView mServerGrid;
    private ServerAdapter<Server> mServerAdapter;
    private WebView mTiraiBambuWebView;
    private boolean mIsWebViewLoaded = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mConnectHandler = (ServerConnectHandler) activity;
            mDatabaseProvider = (DatabaseProvider) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement ServerConnectHandler!");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tirai_bambu, container, false);

        mTiraiBambuWebView = view.findViewById(R.id.webview_tirai_bambu);

        if (mTiraiBambuWebView != null) {
            mTiraiBambuWebView.setVisibility(View.GONE); 
            
            WebSettings settings = mTiraiBambuWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            
            if (getActivity() != null) {
                OfaWebInterface webInterface = new OfaWebInterface(getActivity(), this);
                mTiraiBambuWebView.addJavascriptInterface(webInterface, "MumlaBridge");
                mTiraiBambuWebView.setTag(R.id.webview_tirai_bambu, webInterface);
            }
        }

        return view;
    }

    private void syncTiraiBambuState() {
        List<Server> servers = getServers();
        
        if (mTiraiBambuWebView == null || getActivity() == null) return;

        if (servers.isEmpty()) {
            mTiraiBambuWebView.setVisibility(View.GONE);
            mIsWebViewLoaded = false;
            
            var prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            boolean shouldShowForm = prefs.getBoolean("ofa_web_add_shown", true);
            
            if (shouldShowForm && !isRemoving() && isAdded()) {
                prefs.edit().putBoolean("ofa_web_add_shown", false).apply();
                
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (isAdded() && !isRemoving() && getServers().isEmpty()) {
                        // ✅ Tampilkan form WebView
                        mTiraiBambuWebView.setVisibility(View.VISIBLE);
                        mTiraiBambuWebView.loadUrl("file:///android_asset/ofa_add_server.html");
                        
                        // ✅ LANGSUNG PANGGIL KELAS ASLI — ServerEditFragment, TIDAK BUAT JALUR BARU!
                        ServerEditFragment.createServerEditDialog(
                            getActivity(), 
                            null,
                            ServerEditFragment.Action.ADD_ACTION, 
                            false
                        ).show(getFragmentManager(), "serverInfo");
                    }
                }, 300);
            }
            
        } else {
            mTiraiBambuWebView.setVisibility(View.VISIBLE);
            
            if (!mIsWebViewLoaded) {
                mTiraiBambuWebView.loadUrl("file:///android_asset/ofa_tirai_bambu.html");
                mIsWebViewLoaded = true;
                
                mTiraiBambuWebView.postDelayed(() -> {
                    injectServerData(servers);
                }, 300);
            } else {
                injectServerData(servers);
            }
        }
    }

    private void injectServerData(List<Server> servers) {
        String jsonData = new Gson().toJson(servers);
        mTiraiBambuWebView.evaluateJavascript(
            "window.serverData = " + jsonData + "; renderCard();", 
            null
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        syncTiraiBambuState();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_server_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_add_server_item) {
            addServer();
            return true;
        } else if (itemId == R.id.menu_quick_connect) {
            ServerEditFragment.createServerEditDialog(getActivity(), null,
                    ServerEditFragment.Action.CONNECT_ACTION, true)
                    .show(getFragmentManager(), "serverInfo");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ✅ TETAP ASLI — TIDAK DIUBAH, TIDAK ADA addServerHotmail ATAU SEJENISNYA!
    public void addServer() {
        // Tampilkan form WebView
        if (mTiraiBambuWebView != null && isAdded() && !isRemoving()) {
            mTiraiBambuWebView.setVisibility(View.VISIBLE);
            mTiraiBambuWebView.loadUrl("file:///android_asset/ofa_add_server.html");
        }
        // Tetap panggil dialog asli — sistem penyimpanan tetap lewat sini! 🛡️
        ServerEditFragment.createServerEditDialog(getActivity(), null,
                ServerEditFragment.Action.ADD_ACTION, false)
                .show(getFragmentManager(), "serverInfo");
    }

    public void editServer(Server server) {
        ServerEditFragment.createServerEditDialog(getActivity(), server,
                ServerEditFragment.Action.EDIT_ACTION, false)
                .show(getFragmentManager(), "serverInfo");
    }

    public void shareServer(Server server) {
        String serverUrl = "mumble://" + server.getHost()
                + (server.getPort() == 0 ? "" : ":" + server.getPort()) + "/";
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.shareMessage, serverUrl));
        intent.setType("text/plain");
        startActivity(intent);
    }

    public void deleteServer(final Server server) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.confirm_delete_server)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    mDatabaseProvider.getDatabase().removeServer(server);
                    if (mServerAdapter != null) mServerAdapter.remove(server);
                    syncTiraiBambuState();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    public void updateServers() {
        List<Server> servers = getServers();
        mServerAdapter = new FavouriteServerAdapter(getActivity(), servers, this);
        
        if (mServerGrid != null) mServerGrid.setAdapter(mServerAdapter);
        
        syncTiraiBambuState();
    }

    public List<Server> getServers() {
        return mDatabaseProvider.getDatabase().getServers();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mServerAdapter != null) {
            mConnectHandler.connectToServer(mServerAdapter.getItem(position));
        }
    }

    public interface ServerConnectHandler {
        void connectToServer(Server server);
        void connectToPublicServer(PublicServer server);
    }
}