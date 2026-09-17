/*
 * Copyright (C) 2014 Andrew Comminos
 * OFAID/AHMAD (C) 2026 — Simpan&Pulih + Izin Penyimpanan
 */
package ofaid.ahmad.ptt.app;

import static java.util.Objects.requireNonNull;

import android.Manifest;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.jetbrains.annotations.NotNull;
import org.spongycastle.util.encoders.Hex;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import info.guardianproject.netcipher.proxy.OrbotHelper;
import se.lublin.humla.IHumlaService;
import se.lublin.humla.IHumlaSession;
import se.lublin.humla.model.Server;
import se.lublin.humla.net.HumlaConnection;
import se.lublin.humla.protobuf.Mumble;
import se.lublin.humla.util.HumlaException;
import se.lublin.humla.util.HumlaObserver;
import se.lublin.humla.util.MumbleURLParser;
import ofaid.ahmad.ptt.BuildConfig;
import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.Settings;
import ofaid.ahmad.ptt.channel.AccessTokenFragment;
import ofaid.ahmad.ptt.channel.ChannelFragment;
import ofaid.ahmad.ptt.channel.ServerInfoFragment;
import ofaid.ahmad.ptt.db.DatabaseCertificate;
import ofaid.ahmad.ptt.db.DatabaseProvider;
import ofaid.ahmad.ptt.db.MumlaDatabase;
import ofaid.ahmad.ptt.db.MumlaSQLiteDatabase;
import ofaid.ahmad.ptt.db.PublicServer;
import ofaid.ahmad.ptt.preference.MumlaCertificateGenerateTask;
import ofaid.ahmad.ptt.preference.SettingsActivity;
import ofaid.ahmad.ptt.servers.FavouriteServerListFragment;
import ofaid.ahmad.ptt.servers.PublicServerListFragment;
import ofaid.ahmad.ptt.servers.ServerEditFragment;
import ofaid.ahmad.ptt.service.IMumlaService;
import ofaid.ahmad.ptt.service.MumlaService;
import ofaid.ahmad.ptt.util.HumlaServiceFragment;
import ofaid.ahmad.ptt.util.HumlaServiceProvider;
import ofaid.ahmad.ptt.util.MumlaTrustStore;
import ofaid.ahmad.ptt.ofa.PilihStatusDialog;

public class MumlaActivity extends AppCompatActivity implements ListView.OnItemClickListener,
        FavouriteServerListFragment.ServerConnectHandler, HumlaServiceProvider, DatabaseProvider,
        SharedPreferences.OnSharedPreferenceChangeListener, DrawerAdapter.DrawerDataProvider,
        ServerEditFragment.ServerEditListener,
        PilihStatusDialog.PadaStatusDiubahListener {
    private static final String TAG = MumlaActivity.class.getName();

    public static final String EXTRA_DRAWER_FRAGMENT = "drawer_fragment";

    // ✅ KODE PERMINTAAN IZIN — SESUAI POLA ASLI
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private static final int PERMISSIONS_REQUEST_POST_NOTIFICATIONS = 2;
    private static final int PERMISSIONS_REQUEST_STORAGE = 917; // ✅ Baru

    private IMumlaService mService;
    private MumlaDatabase mDatabase;
    private Settings mSettings;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private DrawerAdapter mDrawerAdapter;

    private Server mServerPendingPerm = null;
    private boolean mPermPostNotificationsAsked = false;

    private AlertDialog mConnectingDialog;
    private AlertDialog mErrorDialog;

    private boolean mIzinPenyimpananDiproses = false; // ✅ Tanda agar tidak berulang

    private final List<HumlaServiceFragment> mServiceFragments = new ArrayList<>();

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((MumlaService.MumlaBinder) service).getService();
            mService.setSuppressNotifications(true);
            mService.registerObserver(mObserver);
            mService.clearChatNotifications();
            mDrawerAdapter.notifyDataSetChanged();

            for (HumlaServiceFragment fragment : mServiceFragments)
                fragment.setServiceBound(true);

            if (getSupportFragmentManager().findFragmentById(R.id.content_frame) instanceof HumlaServiceFragment &&
                    !mService.isConnected()) {
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
            }
            updateConnectionState(getService());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    private final HumlaObserver mObserver = new HumlaObserver() {
        @Override
        public void onConnected() {
            if (mSettings.shouldStartUpInPinnedMode()) {
                loadDrawerFragment(DrawerAdapter.ITEM_PINNED_CHANNELS);
            } else {
                loadDrawerFragment(DrawerAdapter.ITEM_SERVER);
            }
            mDrawerAdapter.notifyDataSetChanged();
            supportInvalidateOptionsMenu();
            updateConnectionState(getService());
        }

        @Override
        public void onConnecting() {
            updateConnectionState(getService());
        }

        @Override
        public void onDisconnected(HumlaException e) {
            if (getSupportFragmentManager().findFragmentById(R.id.content_frame) instanceof HumlaServiceFragment) {
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
            }
            mDrawerAdapter.notifyDataSetChanged();
            supportInvalidateOptionsMenu();
            updateConnectionState(getService());
        }

        @Override
        public void onTLSHandshakeFailed(X509Certificate[] chain) {
            if (chain.length == 0) return;
            final Server lastServer = getService().getTargetServer();
            try {
                final X509Certificate x509 = chain[0];
                View layout = getLayoutInflater().inflate(R.layout.certificate_info, null);
                TextView textView = layout.findViewById(R.id.certificate_info_text);
                try {
                    MessageDigest digest1 = MessageDigest.getInstance("SHA-1");
                    MessageDigest digest2 = MessageDigest.getInstance("SHA-256");
                    String hexDigest1 = new String(Hex.encode(digest1.digest(x509.getEncoded())))
                            .replaceAll("(..)", "$1:");
                    String hexDigest2 = new String(Hex.encode(digest2.digest(x509.getEncoded())))
                            .replaceAll("(..)", "$1:");
                    textView.setText(getString(R.string.certificate_info,
                            x509.getSubjectDN().getName(),
                            x509.getNotBefore().toString(),
                            x509.getNotAfter().toString(),
                            hexDigest1.substring(0, hexDigest1.length() - 1),
                            hexDigest2.substring(0, hexDigest2.length() - 1)));
                } catch (NoSuchAlgorithmException ex) {
                    ex.printStackTrace();
                    textView.setText(x509.toString());
                }
                new MaterialAlertDialogBuilder(MumlaActivity.this)
                        .setTitle(R.string.untrusted_certificate)
                        .setView(layout)
                        .setPositiveButton(R.string.allow, (dialog, which) -> {
                            try {
                                String alias = lastServer.getHost();
                                KeyStore trustStore = MumlaTrustStore.getTrustStore(MumlaActivity.this);
                                trustStore.setCertificateEntry(alias, x509);
                                MumlaTrustStore.saveTrustStore(MumlaActivity.this, trustStore);
                                Toast.makeText(MumlaActivity.this, R.string.trust_added, Toast.LENGTH_LONG).show();
                                connectToServer(lastServer);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                Toast.makeText(MumlaActivity.this, R.string.trust_add_failed, Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } catch (CertificateException ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public void onPermissionDenied(String reason) {
            new MaterialAlertDialogBuilder(MumlaActivity.this)
                    .setTitle(R.string.perm_denied)
                    .setMessage(reason)
                    .show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mSettings = Settings.getInstance(this);

        // === FORCE DEFAULT PTT SAAT PERTAMA KALI — TETAP SAMA ===
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.contains(Settings.PREF_INPUT_METHOD)) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(Settings.PREF_INPUT_METHOD, Settings.ARRAY_INPUT_METHOD_PTT);
            editor.putBoolean("togglePtt", true);
            editor.putString(Settings.PREF_ECHO_CANCELLATION_METHOD, "none");
            editor.putBoolean("first_run_ptt_setup_done", true);
            editor.commit();
        }
        // ========================================================

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mService != null && mService.isConnected()) {
                    new MaterialAlertDialogBuilder(MumlaActivity.this)
                            .setMessage(getString(R.string.disconnectSure, mService.getTargetServer().getName()))
                            .setPositiveButton(R.string.confirm, (dialog, which) -> {
                                mService.disconnect();
                                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        setStayAwake(mSettings.shouldStayAwake());

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);

        mDatabase = new MumlaSQLiteDatabase(this);
        mDatabase.open();

        mDrawerLayout = findViewById(R.id.drawer_layout);
        ListView mDrawerList = findViewById(R.id.left_drawer);

        View headerView = getLayoutInflater().inflate(R.layout.list_drawer_headerlogo, mDrawerList, false);
        mDrawerList.addHeaderView(headerView, null, false);

        if (BuildConfig.FLAVOR.equals("foss")) {
            final int layoutResId = getResources().getIdentifier("list_drawer_headerdonate_foss", "xml", getPackageName());
            final int stringResId = getResources().getIdentifier("donate_link_foss", "string", getPackageName());
            if ((layoutResId != 0) && (stringResId != 0)) {
                View footerView = getLayoutInflater().inflate(layoutResId, mDrawerList, false);
                mDrawerList.addHeaderView(footerView, null, true);
                footerView.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(stringResId)));
                    startActivity(intent);
                    mDrawerLayout.closeDrawers();
                });
            }
        }

        mDrawerList.setOnItemClickListener(this);
        mDrawerAdapter = new DrawerAdapter(this, this);
        mDrawerList.setAdapter(mDrawerAdapter);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) { supportInvalidateOptionsMenu(); }
            @Override
            public void onDrawerStateChanged(int newState) {
                super.onDrawerStateChanged(newState);
                if (getService() != null && getService().isConnected()) {
                    IHumlaSession session = getService().HumlaSession();
                    if (session.isTalking() && !mSettings.isPushToTalkToggle()) {
                        session.setTalkingState(false);
                    }
                }
            }
            @Override
            public void onDrawerOpened(View drawerView) { supportInvalidateOptionsMenu(); }
        };

        mDrawerLayout.setDrawerListener(mDrawerToggle);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // ✅ POLA ASLI DIPERTAHANKAN: cek izin dulu, baru lanjut ke Startup
        if (savedInstanceState == null) {
            if (cekIzinPenyimpanan()) {
                lanjutKeAwal();
            }
        }

        if (getIntent() != null && Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            String url = getIntent().getDataString();
            try {
                Server server = MumbleURLParser.parseURL(url);
                DialogFragment fragment = ServerEditFragment.createServerEditDialog(
                        MumlaActivity.this, server, ServerEditFragment.Action.CONNECT_ACTION, true);
                fragment.show(getSupportFragmentManager(), "url_edit");
            } catch (MalformedURLException e) {
                Toast.makeText(this, getString(R.string.mumble_url_parse_failed), Toast.LENGTH_LONG).show();
            }
        }

        setVolumeControlStream(mSettings.isHandsetMode() ?
                AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
    }

    // ==================================================
    // ✅ CEK IZIN PENYIMPANAN — TAMBAHAN SAJA, TIDAK UBAH ALUR
    // ==================================================
    private boolean cekIzinPenyimpanan() {
        // Android 13+ tidak butuh izin WRITE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return true; // Langsung boleh
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return true; // Sudah ada izin
        }

        if (!mIzinPenyimpananDiproses) {
            mIzinPenyimpananDiproses = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                 Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_STORAGE);
        }
        return false; // Tunggu izin
    }

    private void lanjutKeAwal() {
        if (mSettings.isFirstRun()) {
            showFirstRunGuide();
        } else {
            // ✅ TETAP PAKAI POLA ASLI — TIDAK UBAH StartupAction
            new StartupAction().execute(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length == 0) return;

        // ✅ TANGGAPI IZIN PENYIMPANAN — TETAP KE POLA ASLI
        if (requestCode == PERMISSIONS_REQUEST_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i("OFA_PERM", "✅ Izin penyimpanan DIBERIKAN");
            } else {
                Log.w("OFA_PERM", "⚠️ Izin penyimpanan DITOLAK — sertifikat tetap bekerja tanpa cadangan file luar");
                new MaterialAlertDialogBuilder(this)
                        .setMessage("Tanpa izin akses file, saat install ulang akan buat sertifikat baru. Fitur tetap berjalan.")
                        .setPositiveButton("Mengerti", null)
                        .show();
            }
            lanjutKeAwal(); // ✅ LANGSUNG KE POLA ASLI — TIDAK UBAH
            return;
        }

        // ✅ IZIN LAIN — TETAP SAMA PERSIS
        switch (requestCode) {
            case PERMISSIONS_REQUEST_RECORD_AUDIO:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    connectToServerWithPerm();
                } else {
                    Toast.makeText(MumlaActivity.this, getString(R.string.grant_perm_microphone),
                            Toast.LENGTH_LONG).show();
                }
                break;
            case PERMISSIONS_REQUEST_POST_NOTIFICATIONS:
                mPermPostNotificationsAsked = true;
                if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(MumlaActivity.this,
                            Manifest.permission.POST_NOTIFICATIONS)) {
                        Toast.makeText(MumlaActivity.this,
                                getString(R.string.grant_perm_notifications), Toast.LENGTH_LONG).show();
                    }
                }
                connectToServerWithPerm();
                break;
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent connectIntent = new Intent(this, MumlaService.class);
        bindService(connectIntent, mConnection, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mErrorDialog != null) mErrorDialog.dismiss();
        if (mConnectingDialog != null) mConnectingDialog.dismiss();
        if (mService != null) {
            for (HumlaServiceFragment fragment : mServiceFragments)
                fragment.setServiceBound(false);
            mService.unregisterObserver(mObserver);
            mService.setSuppressNotifications(false);
        }
        unbindService(mConnection);
    }

    @Override
    protected void onDestroy() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        mDatabase.close();
        super.onDestroy();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem disconnectButton = menu.findItem(R.id.action_disconnect);
        disconnectButton.setVisible(mService != null && mService.isConnected());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mumla, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NotNull MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) return true;
        if (item.getItemId() == R.id.action_disconnect) {
            getService().disconnect();
            return true;
        }
        return false;
    }

    @Override
    public void onConfigurationChanged(@NotNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mService != null && keyCode == mSettings.getPushToTalkKey()) {
            mService.onTalkKeyDown();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mService != null && keyCode == mSettings.getPushToTalkKey()) {
            mService.onTalkKeyUp();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mDrawerLayout.closeDrawers();
        loadDrawerFragment((int) id);
    }

    // ==================================================
    // ✅ TAMBAH: PULIHKAN DARI CADANGAN — TETAP PANGGIL StartupAction SESUAI POLA ASLI
    // ==================================================
    private void showFirstRunGuide() {
        if (mSettings.isUsingCertificate()) {
            mSettings.setFirstRun(false);
            new StartupAction().execute(this); // ✅ TETAP PAKAI CARA ASLI
            return;
        }

        MumlaCertificateGenerateTask pulihkanTask = new MumlaCertificateGenerateTask(this) {
            @Override
            protected void onPostExecute(DatabaseCertificate result) {
                super.onPostExecute(result);
                if (result != null) {
                    mSettings.setDefaultCertificateId(result.getId());
                    mSettings.setFirstRun(false);
                    Log.i("OFA_CERT", "✅ Dipulihkan dari cadangan — ID: " + result.getId());
                    new StartupAction().execute(this); // ✅ SESUAI POLA ASLI
                    return;
                }
                tampilkanDialogBuatBaru();
            }
        };
        pulihkanTask.execute();
    }

    private void tampilkanDialogBuatBaru() {
        String msg = getString(R.string.first_run_generate_certificate);
        if (BuildConfig.FLAVOR.equals("donation")) {
            msg = getString(R.string.donation_thanks) + "\n\n" + msg;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.first_run_generate_certificate_title)
                .setMessage(msg)
                .setPositiveButton(R.string.generate, (DialogInterface dialog, int which) -> {
                    MumlaCertificateGenerateTask generateTask = new MumlaCertificateGenerateTask(MumlaActivity.this) {
                        @Override
                        protected void onPostExecute(DatabaseCertificate result) {
                            super.onPostExecute(result);
                            if (result != null) {
                                mSettings.setDefaultCertificateId(result.getId());
                                mSettings.setFirstRun(false);
                                new StartupAction().execute(this); // ✅ SESUAI POLA ASLI
                            }
                        }
                    };
                    generateTask.execute();
                })
                .show();
    }

    private void loadDrawerFragment(int fragmentId) {
        Class<? extends Fragment> fragmentClass = null;
        Bundle args = new Bundle();
        switch (fragmentId) {
            case DrawerAdapter.ITEM_SERVER:
                fragmentClass = ChannelFragment.class;
                break;
            case DrawerAdapter.ITEM_INFO:
                fragmentClass = ServerInfoFragment.class;
                break;
            case DrawerAdapter.ITEM_ACCESS_TOKENS:
                fragmentClass = AccessTokenFragment.class;
                Server connectedServer = getService().getTargetServer();
                args.putLong("server", connectedServer.getId());
                args.putStringArrayList("access_tokens", (ArrayList<String>) mDatabase.getAccessTokens(connectedServer.getId()));
                break;
            case DrawerAdapter.ITEM_PINNED_CHANNELS:
                fragmentClass = ChannelFragment.class;
                args.putBoolean("pinned", true);
                break;
            case DrawerAdapter.ITEM_FAVOURITES:
                fragmentClass = FavouriteServerListFragment.class;
                break;
            case DrawerAdapter.ITEM_PUBLIC:
                fragmentClass = PublicServerListFragment.class;
                break;
            case DrawerAdapter.ITEM_SETTINGS:
                Intent prefIntent = new Intent(this, SettingsActivity.class);
                startActivity(prefIntent);
                return;
            default:
                return;
        }
        Fragment fragment = Fragment.instantiate(this, fragmentClass.getName(), args);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content_frame, fragment, fragmentClass.getName())
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
        requireNonNull(getSupportActionBar()).setTitle(mDrawerAdapter.getItemWithId(fragmentId).title);
    }

    public void connectToServer(final Server server) {
        mServerPendingPerm = server;
        connectToServerWithPerm();
    }

    public void connectToServerWithPerm() {
        if (ContextCompat.checkSelfPermission(MumlaActivity.this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MumlaActivity.this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !mPermPostNotificationsAsked) {
            if (ContextCompat.checkSelfPermission(MumlaActivity.this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MumlaActivity.this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSIONS_REQUEST_POST_NOTIFICATIONS);
                return;
            }
        }

        if (mServerPendingPerm == null) {
            Log.w(TAG, "No pending server after getting permissions");
            return;
        }

        Server server = mServerPendingPerm;
        mServerPendingPerm = null;

        if (mService != null && mService.isConnected()) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.reconnect_dialog_message)
                    .setPositiveButton(R.string.connect, (dialog, which) -> {
                        mService.registerObserver(new HumlaObserver() {
                            @Override
                            public void onDisconnected(HumlaException e) {
                                connectToServer(server);
                                mService.unregisterObserver(this);
                            }
                        });
                        mService.disconnect();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }

        if (mSettings.isTorEnabled()) {
            if (!OrbotHelper.isOrbotInstalled(this)) {
                mSettings.disableTor();
                new MaterialAlertDialogBuilder(MumlaActivity.this)
                        .setMessage(R.string.orbot_not_installed)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            } else {
                if (!isPortOpen(HumlaConnection.TOR_HOST, HumlaConnection.TOR_PORT, 2000)) {
                    new MaterialAlertDialogBuilder(MumlaActivity.this)
                            .setMessage(getString(R.string.orbot_tor_failed, HumlaConnection.TOR_PORT))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }
            }
        }

        ServerConnectTask connectTask = new ServerConnectTask(this, mDatabase);
        connectTask.execute(server);
    }

    private boolean isPortOpen(final String host, final int port, final int timeout) {
        final AtomicBoolean open = new AtomicBoolean(false);
        try {
            Thread thread = new Thread(() -> {
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(host, port), timeout);
                    socket.close();
                    open.set(true);
                } catch (Exception e) {
                    Log.d(TAG, "isPortOpen() run(): " + e);
                }
            });
            thread.start();
            thread.join();
        } catch (Exception e) {
            Log.d(TAG, "isPortOpen(): " + e);
        }
        return open.get();
    }

    public void connectToPublicServer(final PublicServer server) {
        final Settings settings = Settings.getInstance(this);
        final EditText usernameField = new EditText(this);
        usernameField.setHint(settings.getDefaultUsername());
        FrameLayout layout = new FrameLayout(this);
        layout.addView(usernameField);
        int horizontalPadding = (int) getResources().getDimension(R.dimen.padding_medium);
        layout.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        new MaterialAlertDialogBuilder(this)
                .setView(layout)
                .setTitle(R.string.connectToServer)
                .setPositiveButton(R.string.connect, (dialog, which) -> {
                    String username = usernameField.getText().toString();
                    if (username.isEmpty()) username = settings.getDefaultUsername();
                    server.setUsername(username);
                    connectToServer(server);
                })
                .show();
    }

    private void setStayAwake(boolean stayAwake) {
        if (stayAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void updateConnectionState(IHumlaService service) {
        if (mConnectingDialog != null) mConnectingDialog.dismiss();
        if (mErrorDialog != null) mErrorDialog.dismiss();

        switch (mService.getConnectionState()) {
            case CONNECTING:
                Server server = service.getTargetServer();
                mConnectingDialog = new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.connecting_to_server, server.getHost()) + (mSettings.isTorEnabled() ? " (Tor)" : ""))
                        .setView(R.layout.dialog_progress)
                        .setCancelable(true)
                        .setOnCancelListener(dialog -> {
                            mService.disconnect();
                            Toast.makeText(this, R.string.cancelled, Toast.LENGTH_SHORT).show();
                        })
                        .create();
                mConnectingDialog.show();
                break;
            case CONNECTION_LOST:
                if (getService() != null && !getService().isErrorShown()) {
                    if (getService() == null) break;
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
                    builder.setTitle(getString(R.string.connectionRefused) + (mSettings.isTorEnabled() ? " (Tor)" : ""));
                    HumlaException error = getService().getConnectionError();
                    if (error != null && mService.isReconnecting()) {
                        builder.setMessage(error.getMessage() + "\n\n" +
                                getString(R.string.attempting_reconnect,
                                        error.getCause() != null ? error.getCause().getMessage() : "unknown"));
                        builder.setPositiveButton(R.string.cancel_reconnect, (dialog, which) -> {
                            if (getService() != null) {
                                getService().cancelReconnect();
                                getService().markErrorShown();
                            }
                        });
                    } else if (error != null && error.getReason() == HumlaException.HumlaDisconnectReason.REJECT &&
                            (error.getReject().getType() == Mumble.Reject.RejectType.WrongUserPW ||
                             error.getReject().getType() == Mumble.Reject.RejectType.WrongServerPW)) {
                        final EditText passwordField = new EditText(this);
                        passwordField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        passwordField.setHint(R.string.password);
                        builder.setTitle(R.string.invalid_password);
                        builder.setMessage(error.getMessage());
                        builder.setView(passwordField);
                        builder.setPositiveButton(R.string.reconnect, (dialog, which) -> {
                            Server srv = getService().getTargetServer();
                            if (srv == null) return;
                            srv.setPassword(passwordField.getText().toString());
                            if (srv.isSaved()) mDatabase.updateServer(srv);
                            connectToServer(srv);
                        });
                        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                            if (getService() != null) getService().markErrorShown();
                        });
                    } else {
                        String msg = error != null ? error.getMessage() : getString(R.string.unknown);
                        builder.setMessage(msg);
                        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            if (getService() != null) getService().markErrorShown();
                        });
                    }
                    builder.setCancelable(false);
                    mErrorDialog = builder.show();
                }
                break;
        }
    }

    @Override
    public IMumlaService getService() { return mService; }
    @Override
    public MumlaDatabase getDatabase() { return mDatabase; }
    @Override
    public void addServiceFragment(HumlaServiceFragment fragment) { mServiceFragments.add(fragment); }
    @Override
    public void removeServiceFragment(HumlaServiceFragment fragment) { mServiceFragments.remove(fragment); }
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, @Nullable String key) {
        if (key == null) return;
        switch (key) {
            case Settings.PREF_STAY_AWAKE: setStayAwake(mSettings.shouldStayAwake()); break;
            case Settings.PREF_HANDSET_MODE: setVolumeControlStream(mSettings.isHandsetMode() ?
                    AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC); break;
        }
    }
    @Override
    public boolean isConnected() { return mService != null && mService.isConnected(); }
    @Override
    public String getConnectedServerName() {
        if (mService != null && mService.isConnected()) {
            Server s = mService.getTargetServer();
            return s.getName().isEmpty() ? s.getHost() : s.getName();
        }
        return "";
    }
    @Override
    public void onServerEdited(ServerEditFragment.Action action, Server server) {
        switch (action) {
            case ADD_ACTION: mDatabase.addServer(server); loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES); break;
            case EDIT_ACTION: mDatabase.updateServer(server); loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES); break;
            case CONNECT_ACTION: connectToServer(server); break;
        }
    }

    @Override
    public void padaStatusDiubah(int idPengguna, String kodeStatusBaru) {
        Log.d("OFA_STATUS", "Status berubah — id: " + idPengguna + ", kode: " + kodeStatusBaru);
        if (isFinishing() || isDestroyed()) return;
        try {
            runOnUiThread(() -> {
                View root = findViewById(R.id.content_frame);
                if (root != null) { root.invalidate(); root.requestLayout(); }
                supportInvalidateOptionsMenu();
            });
        } catch (Exception e) {
            Log.w("OFA_STATUS", "Pembaruan tertunda: " + e.getMessage());
        }
    }

    // ==================================================
    // ✅ KELAS StartupAction — DIPERTAHANKAN PERSIS SEPERTI ASLINYA
    // ==================================================
    private static class StartupAction extends android.os.AsyncTask<MumlaActivity, Void, Void> {
        private MumlaActivity mActivity;

        public StartupAction(MumlaActivity activity) {
            mActivity = activity;
        }

        @Override
        protected Void doInBackground(MumlaActivity... activities) {
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (mActivity.isFinishing() || mActivity.isDestroyed()) return;
            mActivity.loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
        }
    }
}
