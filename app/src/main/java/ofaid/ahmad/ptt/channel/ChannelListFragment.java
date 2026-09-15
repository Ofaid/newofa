/*
 * Copyright (C) 2014 Andrew Comminos
 * Modif By Ofaid 2026
 */

package ofaid.ahmad.ptt.channel;

import static android.content.Context.RECEIVER_NOT_EXPORTED;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.CursorWrapper;
import android.graphics.PorterDuff;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

import se.lublin.humla.IHumlaService;
import se.lublin.humla.IHumlaSession;
import se.lublin.humla.model.IChannel;
import se.lublin.humla.model.IUser;
import se.lublin.humla.util.HumlaDisconnectedException;
import se.lublin.humla.util.HumlaException;
import se.lublin.humla.util.HumlaObserver;
import se.lublin.humla.util.IHumlaObserver;
import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.Settings;
import ofaid.ahmad.ptt.db.DatabaseProvider;
import ofaid.ahmad.ptt.ofa.OfaIdentity;
import ofaid.ahmad.ptt.ofa.PilihStatusDialog;
import ofaid.ahmad.ptt.ofa.NeonVisualizerView;
// === HAPUS: OfaVisualizerView dihilangkan ===
import ofaid.ahmad.ptt.util.HumlaServiceFragment;
import ofaid.ahmad.ptt.service.MumlaService;
import ofaid.ahmad.ptt.channel.ChannelListAdapter.OnChannelClickListener;
import ofaid.ahmad.ptt.channel.ChannelListAdapter.OnUserClickListener;

public class ChannelListFragment extends HumlaServiceFragment
        implements OnChannelClickListener, OnUserClickListener,
                   SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = ChannelListFragment.class.getName();
    private static final int KODE_IZIN_LOKASI = 1001;

    // --- BANNER INDIKATOR PEMBICARA ---
    private FrameLayout bannerActiveSpeaker;
    private TextView tvSpeakerName;
    private String currentSpeakerName = null;

    private final Handler bannerHideHandler = new Handler(Looper.getMainLooper());
    private final Runnable bannerHideRunnable = () -> {
        if (bannerActiveSpeaker != null && bannerActiveSpeaker.getVisibility() == View.VISIBLE) {
            bannerActiveSpeaker.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    bannerActiveSpeaker.setVisibility(View.GONE);
                    tvSpeakerName.setText("");
                    currentSpeakerName = null;
                })
                .start();
        }
    };

    // --- VISUALIZER ---
    private NeonVisualizerView mVisualNeon;
    // === HAPUS: OfaVisualizerView dihilangkan ===
    private BroadcastReceiver mPenerimaLevel;

    // --- LOKASI OTOMATIS ---
    private LocationManager mLocationManager;
    private String lokasiTerbaca = null;
    private ChannelListAdapter mChannelListAdapter;

    private final LocationListener lokasiPendengar = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            bacaNamaLokasi(location);
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(@NonNull String provider) {}
        @Override public void onProviderDisabled(@NonNull String provider) {}
    };

    // --- KOMPONEN UTAMA ---
    private RecyclerView mChannelView;
    private ChatTargetProvider mTargetProvider;
    private DatabaseProvider mDatabaseProvider;
    private ActionMode mActionMode;
    private Settings mSettings;

    // ========== FUNGSI STATUS & ID OFA ==========
    private String getMyOfaId() {
        Context ctx = getContext();
        if (ctx == null) return null;
        return ctx.getSharedPreferences("ofa_identity_prefs", Context.MODE_PRIVATE)
                .getString("ofa_id", null);
    }

    private void kirimStatusPengguna(String statusTeks) {
        String idOFA = getMyOfaId();
        if (idOFA == null || idOFA.trim().isEmpty()) {
            Log.w(TAG, "ID OFA belum tersedia");
            return;
        }
        IHumlaService service = getService();
        if (service instanceof MumlaService) {
            ((MumlaService) service).kirimStatusDenganId(idOFA, statusTeks);
            Log.i(TAG, "Status dikirim: " + idOFA + " | " + statusTeks);
        }
    }

    private void tampilkanPilihStatus() {
        Log.i(TAG, "Tombol Status ditekan — ID: " + getMyOfaId());
    }

    private void hentikanBacaLokasi() {
        if (mLocationManager != null) {
            mLocationManager.removeUpdates(lokasiPendengar);
        }
    }

    private void mintaIzinLokasiOtomatis() {
        if (getContext() == null) return;
        boolean sudahIzin =
            ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (sudahIzin) {
            mulaiBacaLokasi();
            return;
        }
        requestPermissions(
            new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            },
            KODE_IZIN_LOKASI
        );
    }

    @Override
    public void onRequestPermissionsResult(int kode, @NonNull String[] izin,
                                             @NonNull int[] hasil) {
        super.onRequestPermissionsResult(kode, izin, hasil);
        if (kode == KODE_IZIN_LOKASI && hasil.length > 0 && hasil[0] == PackageManager.PERMISSION_GRANTED) {
            mulaiBacaLokasi();
        }
    }

    private void mulaiBacaLokasi() {
        if (getContext() == null) return;
        mLocationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        boolean gpsNyala = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean jaringanNyala = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        
        lokasiTerbaca = "📍 Mendapatkan lokasi...";
        perbaruiTampilanLokasi();
        
        try {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (jaringanNyala) {
                    mLocationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 30000, 500, lokasiPendengar);
                }
                if (gpsNyala && ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mLocationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 30000, 500, lokasiPendengar);
                }
                Location lokasiTerakhir = null;
                if (jaringanNyala) lokasiTerakhir = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (lokasiTerakhir == null && gpsNyala) lokasiTerakhir = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lokasiTerakhir != null) {
                    bacaNamaLokasi(lokasiTerakhir);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Izin lokasi tidak tersedia", e);
        }
    }

    private void bacaNamaLokasi(Location lokasi) {
        if (getContext() == null) return;
        new Thread(() -> {
            String hasil = "Tidak diketahui";
            try {
                Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                List<Address> daftarAlamat = geocoder.getFromLocation(
                    lokasi.getLatitude(), lokasi.getLongitude(), 1);
                if (daftarAlamat != null && !daftarAlamat.isEmpty()) {
                    Address alamat = daftarAlamat.get(0);
                    String kab = alamat.getSubAdminArea();
                    String prov = alamat.getAdminArea();
                    StringBuilder sb = new StringBuilder();
                    if (kab != null && !kab.trim().isEmpty()) sb.append(kab.trim());
                    if (prov != null && !prov.trim().isEmpty()) {
                        if (sb.length() > 0) sb.append(". ");
                        sb.append(prov.trim());
                    }
                    if (sb.length() > 0) hasil = sb.toString();
                }
            } catch (Exception e) {
                Log.e(TAG, "Gagal baca lokasi", e);
            }
            String lokasiAkhir = hasil;
            new Handler(Looper.getMainLooper()).post(() -> {
                lokasiTerbaca = lokasiAkhir;
                kirimLokasiKeServer(lokasiTerbaca);
                perbaruiTampilanLokasi();
            });
        }).start();
    }

    private void perbaruiTampilanLokasi() {
        if (mChannelListAdapter != null && lokasiTerbaca != null) {
            mChannelListAdapter.setLokasiSaya(lokasiTerbaca);
        }
    }

    private void kirimLokasiKeServer(String teksLokasi) {
        if (getService() == null || !getService().isConnected()) return;
        try {
            IHumlaSession sesi = getService().HumlaSession();
            IUser saya = sesi.getSessionUser();
            if (saya == null) return;
            int sesiSaya = saya.getSession();
            String keteranganLama = saya.getComment();
            String keteranganBaru = (keteranganLama == null || keteranganLama.trim().isEmpty())
                ? teksLokasi
                : keteranganLama + "\n" + teksLokasi;
            sesi.setUserComment(sesiSaya, keteranganBaru);
        } catch (Exception e) {
            Log.e(TAG, "Gagal kirim lokasi", e);
        }
    }

/*========================= PEMANTAU =========================*/
    private final IHumlaObserver mServiceObserver = new HumlaObserver() {
        @Override
        public void onDisconnected(HumlaException e) {
            if (mChannelView != null) mChannelView.setAdapter(null);
        }

        @Override
        public void onUserJoinedChannel(IUser user, IChannel newChannel, IChannel oldChannel) {
            if (mChannelListAdapter != null) mChannelListAdapter.updateChannels();
            if (getService() == null || !getService().isConnected()) return;
            try {
                if (user.getSession() == getService().HumlaSession().getSessionId()) {
                    scrollToChannel(newChannel.getId());
                    perbaruiTampilanLokasi();
                }
            } catch (Exception e) {
                Log.d(TAG, "error: " + e);
            }
        }

        @Override public void onChannelAdded(IChannel channel) {
            if (mChannelListAdapter != null) mChannelListAdapter.updateChannels();
        }
        @Override public void onChannelRemoved(IChannel channel) {
            if (mChannelListAdapter != null) mChannelListAdapter.updateChannels();
        }
        @Override public void onChannelStateUpdated(IChannel channel) {
            if (mChannelListAdapter != null) mChannelListAdapter.updateChannels();
        }
        @Override public void onUserConnected(IUser user) {
            if (mChannelListAdapter != null) mChannelListAdapter.updateChannels();
        }
        @Override public void onUserRemoved(IUser user, String reason) {
            if (mChannelListAdapter != null) mChannelListAdapter.updateChannels();
        }

        @Override
        public void onUserStateUpdated(IUser user) {
            super.onUserStateUpdated(user);
            if (mChannelListAdapter != null && mChannelView != null && user != null) {
                mChannelListAdapter.refreshUserStatus(user.getSession());
                int posisi = mChannelListAdapter.getUserPositionBySession(user.getSession());
                if (posisi != -1) {
                    mChannelView.getAdapter().notifyItemChanged(posisi);
                }
            }
            if (getActivity() != null && !isDetached()) {
                getActivity().supportInvalidateOptionsMenu();
            }
        }
/*======================*/
       @Override
    public void onUserTalkStateUpdated(IUser user) {
        if (mChannelListAdapter != null && mChannelView != null) {
            mChannelListAdapter.updateUserStates(user, mChannelView);
        }
        if (getActivity() != null && !isDetached()) {
            getActivity().runOnUiThread(() -> {
                // =============================================
                // ✅ MONITOR — PAKAI isUserTalking DARI ADAPTER
                // =============================================
                boolean sedangBicara = mChannelListAdapter.isUserTalking(user.getSession());
                float levelMonitor = sedangBicara ? 0.85f : 0f;

                // Kalau nanti sudah pasang tampilan monitor, buka komentar di bawah:
                // if (mVisualMonitor != null) {
                //     mVisualMonitor.setAudioLevel(levelMonitor);
                // }
                // ============= AKHIR TAMBAHAN ================

                // === BANNER — TETAP ASLI, TIDAK DIUBAH ===
                bannerHideHandler.removeCallbacks(bannerHideRunnable);
                String displayName = user.getName();
                if (!displayName.equals(currentSpeakerName)) {
                    currentSpeakerName = displayName;
                    if (tvSpeakerName != null) tvSpeakerName.setText(displayName);
                }
                if (bannerActiveSpeaker != null &&
                    bannerActiveSpeaker.getVisibility() != View.VISIBLE) {
                    bannerActiveSpeaker.setVisibility(View.VISIBLE);
                    bannerActiveSpeaker.setAlpha(1f);
                }
                bannerHideHandler.postDelayed(bannerHideRunnable, 500);
            });
        }
    }

/*=======================*/

    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() != null) getActivity().supportInvalidateOptionsMenu();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mTargetProvider = (ChatTargetProvider) getParentFragment();
            mDatabaseProvider = (DatabaseProvider) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Harus implementasi provider");
        }
        mSettings = Settings.getInstance(activity);
        PreferenceManager.getDefaultSharedPreferences(activity)
            .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_channel_list, container, false);
        mChannelView = view.findViewById(R.id.channelUsers);
        mChannelView.setLayoutManager(new LinearLayoutManager(getActivity()));
        bannerActiveSpeaker = view.findViewById(R.id.bannerActiveSpeaker);
        tvSpeakerName = view.findViewById(R.id.tvSpeakerName);
        
        mVisualNeon = view.findViewById(R.id.neonVisualizer);
        // === HAPUS: ofaVisualizer tidak dipakai ===
        
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mintaIzinLokasiOtomatis();
        
        mPenerimaLevel = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("ofaid.ahmad.ptt.LEVEL_SUARA".equals(intent.getAction())) {
                    float level = intent.getFloatExtra("level", 0f);
                    if (mVisualNeon != null) {
                        mVisualNeon.setAudioLevel(level);
                    }
                }
            }
        };
        requireContext().registerReceiver(mPenerimaLevel, new IntentFilter("ofaid.ahmad.ptt.LEVEL_SUARA"));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mPenerimaLevel != null) requireContext().unregisterReceiver(mPenerimaLevel);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        registerForContextMenu(mChannelView);
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getActivity().registerReceiver(mBluetoothReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            getActivity().registerReceiver(mBluetoothReceiver, filter);
        }
    }

    @Override public void onDetach() {
        if (getActivity() != null) getActivity().unregisterReceiver(mBluetoothReceiver);
        super.onDetach();
    }

    @Override public void onDestroy() {
        hentikanBacaLokasi();
        if (getActivity() != null) {
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                .unregisterOnSharedPreferenceChangeListener(this);
        }
        super.onDestroy();
    }

    @Override public IHumlaObserver getServiceObserver() { return mServiceObserver; }

    @Override
    public void onServiceBound(IHumlaService service) {
        try {
            if (mChannelListAdapter == null) {
                setupChannelList();
            } else {
                mChannelListAdapter.setService(service);
                perbaruiTampilanLokasi();
            }
        } catch (RemoteException e) { e.printStackTrace(); }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem muteItem = menu.findItem(R.id.menu_mute_button);
        MenuItem deafenItem = menu.findItem(R.id.menu_deafen_button);
        MenuItem statusItem = menu.findItem(R.id.menu_status_pilihan);

        if (getService() != null && getService().isConnected()) {
            IHumlaSession session = getService().HumlaSession();
            int warna = requireActivity().getTheme()
                .obtainStyledAttributes(new int[]{android.R.attr.textColorPrimaryInverse})
                .getColor(0, -1);
            try {
                IUser self = session.getSessionUser();
                if (self != null) {
                    muteItem.setIcon(self.isSelfMuted() ?
                        R.drawable.ic_action_microphone_muted : R.drawable.ic_action_microphone);
                    deafenItem.setIcon(self.isSelfDeafened() ?
                        R.drawable.ic_action_audio_muted : R.drawable.ic_action_audio);
                    if (muteItem.getIcon() != null) muteItem.getIcon().mutate().setColorFilter(warna, PorterDuff.Mode.MULTIPLY);
                    if (deafenItem.getIcon() != null) deafenItem.getIcon().mutate().setColorFilter(warna, PorterDuff.Mode.MULTIPLY);
                }
            } catch (Exception ignored) {}
            menu.findItem(R.id.menu_bluetooth).setChecked(session.usingBluetoothSco());
            if (statusItem != null) statusItem.setVisible(true);
        } else {
            if (statusItem != null) statusItem.setVisible(false);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_channel_list, menu);
        SearchManager sm = (SearchManager) requireActivity().getSystemService(Context.SEARCH_SERVICE);
        SearchView sv = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.menu_search));
        sv.setSearchableInfo(sm.getSearchableInfo(requireActivity().getComponentName()));
        sv.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override public boolean onSuggestionSelect(int pos) { return false; }
            @Override public boolean onSuggestionClick(int pos) {
                if (getService() == null || !getService().isConnected()) return false;
                CursorWrapper c = (CursorWrapper) sv.getSuggestionsAdapter().getItem(pos);
                String tipe = c.getString(c.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA));
                int id = c.getInt(c.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_DATA));
                try {
                    IHumlaSession s = getService().HumlaSession();
                    if ("channel".equals(tipe)) {
                        if (s.getSessionChannel().getId() != id) s.joinChannel(id);
                        else scrollToChannel(id);
                    } else if ("user".equals(tipe)) scrollToUser(id);
                } catch (Exception e) { return false; }
                return true;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (getService() == null || !getService().isConnected()) return super.onOptionsItemSelected(item);
        IHumlaSession s = getService().HumlaSession();
        int id = item.getItemId();
        if (id == R.id.menu_status_pilihan) {
            tampilkanPilihStatus();
            return true;
        } else if (id == R.id.menu_mute_button) {
            try {
                IUser me = s.getSessionUser();
                if (me != null) {
                    boolean m = !me.isSelfMuted();
                    s.setSelfMuteDeafState(m, m && me.isSelfDeafened());
                }
            } catch (Exception e) {}
            requireActivity().supportInvalidateOptionsMenu();
            return true;
        } else if (id == R.id.menu_deafen_button) {
            try {
                IUser me = s.getSessionUser();
                if (me != null) s.setSelfMuteDeafState(me.isSelfDeafened(), !me.isSelfDeafened());
            } catch (Exception e) {}
            requireActivity().supportInvalidateOptionsMenu();
            return true;
        } else if (id == R.id.menu_bluetooth) {
            item.setChecked(!item.isChecked());
            if (item.isChecked()) s.enableBluetoothSco(); else s.disableBluetoothSco();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupChannelList() throws RemoteException {
        mChannelListAdapter = new ChannelListAdapter(
            requireActivity(), getService(), mDatabaseProvider.getDatabase(),
            getChildFragmentManager(), isShowingPinnedChannels(), mSettings.shouldShowUserCount());
        mChannelListAdapter.attachRecyclerView(mChannelView);
        mChannelListAdapter.setOnChannelClickListener(this);
        mChannelListAdapter.setOnUserClickListener(this);
        mChannelView.setAdapter(mChannelListAdapter);
        mChannelListAdapter.notifyDataSetChanged();
        perbaruiTampilanLokasi();
    }

    private boolean isShowingPinnedChannels() {
        Bundle a = getArguments();
        return a != null && a.getBoolean("pinned");
    }

    public void scrollToChannel(int cid) {
        int p = mChannelListAdapter.getChannelPosition(cid);
        mChannelView.scrollToPosition(p);
    }

    public void scrollToUser(int uid) {
        int p = mChannelListAdapter.getUserPosition(uid);
        mChannelView.scrollToPosition(p);
    }

    @Override
    public void onChannelClick(IChannel ch) {
        ChatTargetProvider.ChatTarget t = mTargetProvider.getChatTarget();
        if (t != null && ch.equals(t.getChannel()) && mActionMode != null) mActionMode.finish();
        else mActionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(
            new ChatTargetActionModeCallback(mTargetProvider, new ChatTargetProvider.ChatTarget(ch)) {
                @Override public void onDestroyActionMode(ActionMode am) {
                    super.onDestroyActionMode(am);
                    mActionMode = null;
                }
            });
    }

    @Override
    public void onUserClick(IUser u) {
        ChatTargetProvider.ChatTarget t = mTargetProvider.getChatTarget();
        if (t != null && u.equals(t.getUser()) && mActionMode != null) mActionMode.finish();
        else mActionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(
            new ChatTargetActionModeCallback(mTargetProvider, new ChatTargetProvider.ChatTarget(u)) {
                @Override public void onDestroyActionMode(ActionMode am) {
                    super.onDestroyActionMode(am);
                    mActionMode = null;
                }
            });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (Settings.PREF_SHOW_USER_COUNT.equals(key) && mChannelListAdapter != null) {
            mChannelListAdapter.setShowChannelUserCount(mSettings.shouldShowUserCount());
        }
    }
}
