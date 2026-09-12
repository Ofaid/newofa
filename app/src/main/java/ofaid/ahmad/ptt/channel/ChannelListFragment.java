/*
 * Copyright (C) 2014 Andrew Comminos
 * Modif By Ofaid 2026*/

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

    // --- LOKASI OTOMATIS ---
    private LocationManager mLocationManager;
    private String lokasiTerbaca = null;
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
    private ChannelListAdapter mChannelListAdapter;
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
            Log.w(TAG, "ID OFA belum tersedia — tidak bisa kirim status");
            return;
        }

        IHumlaService service = getService();
        if (service instanceof MumlaService) {
            ((MumlaService) service).kirimStatusDenganId(idOFA, statusTeks);
            Log.i(TAG, "✅ Status dikirim: " + idOFA + " | " + statusTeks);
        } else {
            Log.w(TAG, "Belum terhubung ke layanan");
        }
    }

    private void tampilkanPilihStatus() {
        Log.i(TAG, "Tombol Status ditekan — ID: " + getMyOfaId());
        // Nanti diaktifkan saat PilihStatusDialog siap
    }

    // ========== MINTA IZIN LOKASI OTOMATIS ==========
    private void mintaIzinLokasiOtomatis() {
        if (getContext() == null) return;

        boolean sudahIzin =
            ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (sudahIzin) {
            Log.i(TAG, "✅ Izin lokasi sudah ada — mulai baca lokasi");
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
        if (kode == KODE_IZIN_LOKASI) {
            if (hasil.length > 0 && hasil[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "✅ Izin lokasi DITERIMA");
                mulaiBacaLokasi();
            } else {
                Log.i(TAG, "⚠️ Izin lokasi DITOLAK");
                lokasiTerbaca = "📍 Izinkan lokasi agar terlihat teman-teman";
                if (mChannelListAdapter != null) {
                    mChannelListAdapter.setLokasiTeks(lokasiTerbaca);
                }
            }
        }
    }

    // ========== BACA NAMA LOKASI ==========
    private void mulaiBacaLokasi() {
        if (getContext() == null) return;
        mLocationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);

        boolean gpsNyala = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean jaringanNyala = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

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
                } else {
                    lokasiTerbaca = "📍 Mendapatkan lokasi...";
                    if (mChannelListAdapter != null) {
                        mChannelListAdapter.setLokasiTeks(lokasiTerbaca);
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Izin lokasi tidak tersedia", e);
        }
    }

    private void bacaNamaLokasi(Location lokasi) {
        if (getContext() == null) return;

        android.location.Geocoder geocoder = new android.location.Geocoder(requireContext());
        try {
            java.util.List<android.location.Address> daftarAlamat =
                geocoder.getFromLocation(lokasi.getLatitude(), lokasi.getLongitude(), 1);

            if (daftarAlamat != null && !daftarAlamat.isEmpty()) {
                android.location.Address alamat = daftarAlamat.get(0);
                StringBuilder sb = new StringBuilder();
                if (alamat.getSubLocality() != null) sb.append(alamat.getSubLocality()).append(", ");
                if (alamat.getLocality() != null) sb.append(alamat.getLocality()).append(", ");
                if (alamat.getSubAdminArea() != null) sb.append(alamat.getSubAdminArea()).append(", ");
                if (alamat.getAdminArea() != null) sb.append(alamat.getAdminArea());

                lokasiTerbaca = "📍 " + sb.toString().trim().replaceAll(", $", "");
                Log.i(TAG, "✅ Lokasi: " + lokasiTerbaca);
            } else {
                lokasiTerbaca = String.format("📍 %.4f, %.4f", lokasi.getLatitude(), lokasi.getLongitude());
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal baca nama lokasi", e);
            lokasiTerbaca = String.format("📍 %.4f, %.4f", lokasi.getLatitude(), lokasi.getLongitude());
        }

        if (mChannelListAdapter != null) {
            mChannelListAdapter.setLokasiTeks(lokasiTerbaca);
        }
    }

    private void hentikanBacaLokasi() {
        if (mLocationManager != null) {
            mLocationManager.removeUpdates(lokasiPendengar);
        }
    }

    // ===========================================

    private final IHumlaObserver mServiceObserver = new HumlaObserver() {
        @Override
        public void onDisconnected(HumlaException e) {
            if (mChannelView != null) {
                mChannelView.setAdapter(null);
            }
        }

        @Override
        public void onUserJoinedChannel(IUser user, IChannel newChannel, IChannel oldChannel) {
            if (mChannelListAdapter != null) {
                mChannelListAdapter.updateChannels();
                mChannelListAdapter.notifyDataSetChanged();
            }

            if (getService() == null || !getService().isConnected()) return;

            int selfSession;
            try {
                selfSession = getService().HumlaSession().getSessionId();
            } catch (HumlaDisconnectedException | IllegalStateException e) {
                Log.d(TAG, "exception in onUserJoinedChannel: " + e);
                return;
            }

            if (user.getSession() == selfSession) {
                scrollToChannel(newChannel.getId());
            }
        }

        @Override
        public void onChannelAdded(IChannel channel) {
            if (mChannelListAdapter != null) {
                mChannelListAdapter.updateChannels();
                mChannelListAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onChannelRemoved(IChannel channel) {
            if (mChannelListAdapter != null) {
                mChannelListAdapter.updateChannels();
                mChannelListAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onChannelStateUpdated(IChannel channel) {
            if (mChannelListAdapter != null) {
                mChannelListAdapter.updateChannels();
                mChannelListAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onUserConnected(IUser user) {
            if (mChannelListAdapter != null) {
                mChannelListAdapter.updateChannels();
                mChannelListAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onUserRemoved(IUser user, String reason) {
            if (getService() == null || !getService().isConnected()) return;
            if (mChannelListAdapter != null) {
                mChannelListAdapter.updateChannels();
                mChannelListAdapter.notifyDataSetChanged();
            }
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

        @Override
        public void onUserTalkStateUpdated(IUser user) {
            if (mChannelListAdapter != null && mChannelView != null) {
                mChannelListAdapter.updateUserStates(user, mChannelView);
            }

            if (getActivity() != null && !isDetached()) {
                getActivity().runOnUiThread(() -> {
                    bannerHideHandler.removeCallbacks(bannerHideRunnable);

                    String displayName = user.getName();
                    if (!displayName.equals(currentSpeakerName)) {
                        currentSpeakerName = displayName;
                        if (tvSpeakerName != null) {
                            tvSpeakerName.setText(displayName);
                        }
                    }

                    if (bannerActiveSpeaker != null &&
                        bannerActiveSpeaker.getVisibility() != View.VISIBLE) {
                        bannerActiveSpeaker.setVisibility(View.VISIBLE);
                        bannerActiveSpeaker.setAlpha(1f);
                    }

                    bannerHideHandler.postDelayed(bannerHideRunnable, 2000);
                });
            }
        }
    };

    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getActivity() != null) {
                getActivity().supportInvalidateOptionsMenu();
            }
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
        } catch (ClassCastException e) {
            throw new ClassCastException(getParentFragment().toString() +
                    " must implement ChatTargetProvider");
        }
        try {
            mDatabaseProvider = (DatabaseProvider) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() +
                    " must implement DatabaseProvider");
        }
        mSettings = Settings.getInstance(activity);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_channel_list, container, false);

        mChannelView = view.findViewById(R.id.channelUsers);
        mChannelView.setLayoutManager(new LinearLayoutManager(getActivity()));

        bannerActiveSpeaker = view.findViewById(R.id.bannerActiveSpeaker);
        tvSpeakerName = view.findViewById(R.id.tvSpeakerName);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // ✅ MUNCULKAN PERTANYAAN IZIN LOKASI SAAT LAYAR SIAP!
        mintaIzinLokasiOtomatis();
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

    @Override
    public void onDetach() {
        if (getActivity() != null) {
            getActivity().unregisterReceiver(mBluetoothReceiver);
        }
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        hentikanBacaLokasi();
        if (getActivity() != null) {
            SharedPreferences preferences =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            preferences.unregisterOnSharedPreferenceChangeListener(this);
        }
        super.onDestroy();
    }

    @Override
    public IHumlaObserver getServiceObserver() {
        return mServiceObserver;
    }

    @Override
    public void onServiceBound(IHumlaService service) {
        try {
            if (mChannelListAdapter == null) {
                setupChannelList();
            } else {
                mChannelListAdapter.setService(service);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        MenuItem muteItem = menu.findItem(R.id.menu_mute_button);
        MenuItem deafenItem = menu.findItem(R.id.menu_deafen_button);
        MenuItem statusItem = menu.findItem(R.id.menu_status_pilihan);

        if (getService() != null && getService().isConnected()) {
            IHumlaSession session = getService().HumlaSession();
            int foregroundColor = requireActivity().getTheme()
                    .obtainStyledAttributes(new int[]{android.R.attr.textColorPrimaryInverse})
                    .getColor(0, -1);

            IUser self = null;
            try {
                self = session.getSessionUser();
            } catch (Exception ignored) {}

            if (self != null) {
                muteItem.setIcon(self.isSelfMuted() ?
                        R.drawable.ic_action_microphone_muted :
                        R.drawable.ic_action_microphone);
                deafenItem.setIcon(self.isSelfDeafened() ?
                        R.drawable.ic_action_audio_muted :
                        R.drawable.ic_action_audio);
                if (muteItem.getIcon() != null) {
                    muteItem.getIcon().mutate().setColorFilter(foregroundColor, PorterDuff.Mode.MULTIPLY);
                }
                if (deafenItem.getIcon() != null) {
                    deafenItem.getIcon().mutate().setColorFilter(foregroundColor, PorterDuff.Mode.MULTIPLY);
                }
            }

            MenuItem bluetoothItem = menu.findItem(R.id.menu_bluetooth);
            bluetoothItem.setChecked(session.usingBluetoothSco());

            if (statusItem != null) {
                statusItem.setVisible(true);
            }
        } else {
            if (statusItem != null) {
                statusItem.setVisible(false);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_channel_list, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_search);
        SearchManager searchManager = (SearchManager)
                requireActivity().getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().getComponentName()));

        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) { return false; }

            @Override
            public boolean onSuggestionClick(int position) {
                if (getService() == null || !getService().isConnected()) return false;

                CursorWrapper cursor = (CursorWrapper) searchView.getSuggestionsAdapter().getItem(position);
                int typeColumn = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA);
                int dataIdColumn = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_DATA);
                String itemType = cursor.getString(typeColumn);
                int itemId = cursor.getInt(dataIdColumn);

                IHumlaSession session = getService().HumlaSession();
                if (ChannelSearchProvider.INTENT_DATA_CHANNEL.equals(itemType)) {
                    if (session.getSessionChannel().getId() != itemId) {
                        session.joinChannel(itemId);
                    } else {
                        scrollToChannel(itemId);
                    }
                    return true;
                } else if (ChannelSearchProvider.INTENT_DATA_USER.equals(itemType)) {
                    scrollToUser(itemId);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (getService() == null || !getService().isConnected()) {
            return super.onOptionsItemSelected(item);
        }

        IHumlaSession session = getService().HumlaSession();
        int itemId = item.getItemId();

        if (itemId == R.id.menu_status_pilihan) {
            tampilkanPilihStatus();
            return true;
        } else if (itemId == R.id.menu_mute_button) {
            IUser self = session.getSessionUser();
            if (self != null) {
                boolean muted = !self.isSelfMuted();
                boolean deafened = self.isSelfDeafened();
                deafened &= muted;
                session.setSelfMuteDeafState(muted, deafened);
            }
            requireActivity().supportInvalidateOptionsMenu();
            return true;
        } else if (itemId == R.id.menu_deafen_button) {
            IUser self = session.getSessionUser();
            if (self != null) {
                boolean deafened = !self.isSelfDeafened();
                session.setSelfMuteDeafState(deafened, deafened);
            }
            requireActivity().supportInvalidateOptionsMenu();
            return true;
        } else if (itemId == R.id.menu_search) {
            return false;
        } else if (itemId == R.id.menu_bluetooth) {
            item.setChecked(!item.isChecked());
            if (item.isChecked()) session.enableBluetoothSco();
            else session.disableBluetoothSco();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupChannelList() throws RemoteException {
        mChannelListAdapter = new ChannelListAdapter(
                requireActivity(),
                getService(),
                mDatabaseProvider.getDatabase(),
                getChildFragmentManager(),
                isShowingPinnedChannels(),
                mSettings.shouldShowUserCount());

        mChannelListAdapter.attachRecyclerView(mChannelView);
        mChannelListAdapter.setOnChannelClickListener(this);
        mChannelListAdapter.setOnUserClickListener(this);
        mChannelView.setAdapter(mChannelListAdapter);
        mChannelListAdapter.notifyDataSetChanged();

        // Kirim lokasi yang sudah terbaca ke tampilan
        if (lokasiTerbaca != null) {
            mChannelListAdapter.setLokasiTeks(lokasiTerbaca);
        }
    }

    public void scrollToChannel(int channelId) {
        int posisi = mChannelListAdapter.getChannelPosition(channelId);
        mChannelView.scrollToPosition(posisi);
    }

    public void scrollToUser(int userId) {
        int posisi = mChannelListAdapter.getUserPosition(userId);
        mChannelView.scrollToPosition(posisi);
    }

    private boolean isShowingPinnedChannels() {
        Bundle args = getArguments();
        return args != null && args.getBoolean("pinned");
    }

    @Override
    public void onChannelClick(IChannel channel) {
        ChatTargetProvider.ChatTarget target = mTargetProvider.getChatTarget();
        if (target != null && channel.equals(target.getChannel()) && mActionMode != null) {
            mActionMode.finish();
        } else {
            ActionMode.Callback cb = new ChatTargetActionModeCallback(
                    mTargetProvider,
                    new ChatTargetProvider.ChatTarget(channel)) {
                @Override
                public void onDestroyActionMode(ActionMode actionMode) {
                    super.onDestroyActionMode(actionMode);
                    mActionMode = null;
                }
            };
            mActionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(cb);
        }
    }

    @Override
    public void onUserClick(IUser user) {
        ChatTargetProvider.ChatTarget target = mTargetProvider.getChatTarget();
        if (target != null && user.equals(target.getUser()) && mActionMode != null) {
            mActionMode.finish();
        } else {
            ActionMode.Callback cb = new ChatTargetActionModeCallback(
                    mTargetProvider,
                    new ChatTargetProvider.ChatTarget(user)) {
                @Override
                public void onDestroyActionMode(ActionMode actionMode) {
                    super.onDestroyActionMode(actionMode);
                    mActionMode = null;
                }
            };
            mActionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(cb);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Settings.PREF_SHOW_USER_COUNT.equals(key) && mChannelListAdapter != null) {
            mChannelListAdapter.setShowChannelUserCount(mSettings.shouldShowUserCount());
        }
    }
}
