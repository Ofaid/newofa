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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
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
import android.widget.LinearLayout;
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

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.BarDataSet;

import java.util.ArrayList;
import java.util.List;

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

    private static final int JUMLAH_BATANG_VISUAL = 16;
    private static final int WARNA_KIRIM = 0xFF4CAF50;
    private static final int WARNA_TERIMA = 0xFF2196F3;
    private static final int SAMPLING_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private LinearLayout mVisualizerPanel;
    private BarChart mVisKirim;
    private BarChart mVisTerima;
    private Handler mVisualHandler;
    private boolean mVisualBerjalan = false;
    private AudioRecord mPerekamSuara;
    private int mUkuranBufferSuara;

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
                    if (mVisualizerPanel != null) {
                        mVisualizerPanel.setVisibility(View.GONE);
                    }
                })
                .start();
        }
    };

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

    private RecyclerView mChannelView;
    private ChannelListAdapter mChannelListAdapter;
    private ChatTargetProvider mTargetProvider;
    private DatabaseProvider mDatabaseProvider;
    private ActionMode mActionMode;
    private Settings mSettings;

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

    private void hitungUkuranBufferSuara() {
        int ukuranMin = AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        mUkuranBufferSuara = Math.max(1024, ukuranMin);
        mVisualHandler = new Handler(Looper.getMainLooper());
    }

    private void aturGrafik(BarChart grafik, int warna) {
        if (grafik == null) return;
        grafik.setDrawBarShadow(false);
        grafik.setDrawValueAboveBar(false);
        grafik.getDescription().setEnabled(false);
        grafik.setTouchEnabled(false);
        grafik.setDragEnabled(false);
        grafik.setScaleEnabled(false);
        grafik.setPinchZoom(false);
        grafik.getAxisLeft().setEnabled(false);
        grafik.getAxisRight().setEnabled(false);
        grafik.getXAxis().setEnabled(false);
        grafik.getLegend().setEnabled(false);
        grafik.setExtraOffsets(2, 2, 2, 2);
        grafik.setMaxVisibleValueCount(JUMLAH_BATANG_VISUAL);
        
        List<BarEntry> kosong = new ArrayList<>();
        for (int i = 0; i < JUMLAH_BATANG_VISUAL; i++) {
            kosong.add(new BarEntry(i, 0f));
        }
        BarDataSet set = new BarDataSet(kosong, "");
        set.setColor(warna);
        set.setDrawValues(false);
        BarData data = new BarData(set);
        data.setBarWidth(0.6f);
        grafik.setData(data);
        grafik.invalidate();
    }

    private void mulaiVisualizerKirim() {
        if (mVisKirim == null || mVisualBerjalan) return;
        mVisualBerjalan = true;
        if (mVisualizerPanel != null) mVisualizerPanel.setVisibility(View.VISIBLE);
        if (AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) > 0) {
            try {
                mPerekamSuara = new AudioRecord(
                    MediaRecorder.AudioSource.MIC, SAMPLING_RATE,
                    CHANNEL_CONFIG, AUDIO_FORMAT, mUkuranBufferSuara
                );
                mPerekamSuara.startRecording();
                mVisualHandler.post(mPembaruanVisual);
            } catch (Exception e) {
                Log.e(TAG, "Gagal mulai perekam", e);
                mVisualBerjalan = false;
            }
        }
    }

    private void hentikanVisualizerKirim() {
        mVisualBerjalan = false;
        if (mVisualHandler != null) mVisualHandler.removeCallbacks(mPembaruanVisual);
        if (mPerekamSuara != null) {
            try { mPerekamSuara.stop(); mPerekamSuara.release(); } catch (Exception e) {}
            mPerekamSuara = null;
        }
        if (mVisKirim != null) aturGrafik(mVisKirim, WARNA_KIRIM);
    }

    private final Runnable mPembaruanVisual = new Runnable() {
        @Override
        public void run() {
            if (!mVisualBerjalan || mPerekamSuara == null) return;
            short[] buffer = new short[mUkuranBufferSuara];
            int dibaca = mPerekamSuara.read(buffer, 0, buffer.length);
            if (dibaca > 0) {
                int[] tingkat = hitungTingkatSuara(buffer, dibaca);
                perbaruiGrafik(mVisKirim, tingkat, WARNA_KIRIM);
            }
            if (mVisualBerjalan) mVisualHandler.postDelayed(this, 50);
        }
    };

    private int[] hitungTingkatSuara(short[] buffer, int panjang) {
        int[] hasil = new int[JUMLAH_BATANG_VISUAL];
        int perBagian = Math.max(1, panjang / JUMLAH_BATANG_VISUAL);
        for (int b = 0; b < JUMLAH_BATANG_VISUAL; b++) {
            int total = 0;
            int mulai = b * perBagian;
            int akhir = Math.min(mulai + perBagian, panjang);
            for (int i = mulai; i < akhir; i++) total += Math.abs(buffer[i]);
            hasil[b] = Math.min(100, total / perBagian / 8);
        }
        return hasil;
    }

    private void perbaruiGrafik(BarChart grafik, int[] nilai, int warna) {
        if (grafik == null) return;
        List<BarEntry> daftar = new ArrayList<>();
        for (int i = 0; i < nilai.length; i++) daftar.add(new BarEntry(i, nilai[i] / 8.0f));
        BarDataSet set = new BarDataSet(daftar, "");
        set.setColor(warna);
        set.setDrawValues(false);
        BarData data = new BarData(set);
        data.setBarWidth(0.6f);
        grafik.setData(data);
        grafik.invalidate();
    }

    private void perbaruiVisualTerima(IUser user) {
        if (mVisTerima == null) return;
        int[] tingkat = new int[JUMLAH_BATANG_VISUAL];
        for (int i = 0; i < tingkat.length; i++) tingkat[i] = (int)(Math.random() * 40 + 10);
        perbaruiGrafik(mVisTerima, tingkat, WARNA_TERIMA);
    }

    private void hentikanBacaLokasi() {
        if (mLocationManager != null) mLocationManager.removeUpdates(lokasiPendengar);
    }

    private void mintaIzinLokasiOtomatis() {
        if (getContext() == null) return;
        boolean sudahIzin =
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (sudahIzin) { mulaiBacaLokasi(); return; }
        requestPermissions(new String[]{
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        }, KODE_IZIN_LOKASI);
    }

    @Override
    public void onRequestPermissionsResult(int kode, @NonNull String[] izin, @NonNull int[] hasil) {
        super.onRequestPermissionsResult(kode, izin, hasil);
        if (kode == KODE_IZIN_LOKASI) {
            if (hasil.length > 0 && hasil[0] == PackageManager.PERMISSION_GRANTED) {
                mulaiBacaLokasi();
            } else {
                lokasiTerbaca = "📍 Izinkan lokasi agar terlihat";
            }
        }
    }

    private void mulaiBacaLokasi() {
        if (getContext() == null) return;
        mLocationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        boolean gpsNyala = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean jaringanNyala = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (jaringanNyala) mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 500, lokasiPendengar);
                if (gpsNyala && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000, 500, lokasiPendengar);
                }
                Location lokasiTerakhir = jaringanNyala ? mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) : null;
                if (lokasiTerakhir == null && gpsNyala) lokasiTerakhir = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lokasiTerakhir != null) bacaNamaLokasi(lokasiTerakhir);
                else lokasiTerbaca = "📍 Mendapatkan lokasi...";
            }
        } catch (SecurityException e) { Log.e(TAG, "Izin tidak tersedia", e); }
    }

    private void bacaNamaLokasi(Location lokasi) {
        if (getContext() == null) return;
        android.location.Geocoder geocoder = new android.location.Geocoder(requireContext());
        try {
            List<android.location.Address> daftarAlamat = geocoder.getFromLocation(lokasi.getLatitude(), lokasi.getLongitude(), 1);
            if (daftarAlamat != null && !daftarAlamat.isEmpty()) {
                android.location.Address alamat = daftarAlamat.get(0);
                StringBuilder sb = new StringBuilder();
                if (alamat.getSubLocality() != null) sb.append(alamat.getSubLocality()).append(", ");
                if (alamat.getLocality() != null) sb.append(alamat.getLocality()).append(", ");
                if (alamat.getSubAdminArea() != null) sb.append(alamat.getSubAdminArea()).append(", ");
                if (alamat.getAdminArea() != null) sb.append(alamat.getAdminArea());
                lokasiTerbaca = "📍 " + sb.toString().trim().replaceAll(", $", "");
                kirimLokasiKeServer(lokasiTerbaca);
            } else {
                lokasiTerbaca = String.format("📍 %.4f, %.4f", lokasi.getLatitude(), lokasi.getLongitude());
                kirimLokasiKeServer(lokasiTerbaca);
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal baca alamat", e);
            lokasiTerbaca = String.format("📍 %.4f, %.4f", lokasi.getLatitude(), lokasi.getLongitude());
            kirimLokasiKeServer(lokasiTerbaca);
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
            String keteranganBaru;
            if (keteranganLama == null || keteranganLama.trim().isEmpty() || keteranganLama.trim().startsWith("📍")) {
                keteranganBaru = teksLokasi;
            } else {
                keteranganBaru = keteranganLama + "\n" + teksLokasi;
            }
            sesi.setUserComment(sesiSaya, keteranganBaru);
            Log.i(TAG, "Lokasi dikirim: " + teksLokasi);
        } catch (Exception e) { Log.e(TAG, "Gagal kirim lokasi", e); }
    }

    private final IHumlaObserver mServiceObserver = new HumlaObserver() {
        @Override
        public void onDisconnected(HumlaException e) {
            if (mChannelView != null) mChannelView.setAdapter(null);
            hentikanVisualizerKirim();
        }

        @Override
        public void onUserJoinedChannel(IUser user, IChannel newChannel, IChannel oldChannel) {
            if (mChannelListAdapter != null) mChannelListAdapter.updateChannels();
            if (getService() == null || !getService().isConnected()) return;
            try {
                int selfSession = getService().HumlaSession().getSessionId();
                if (user.getSession() == selfSession) scrollToChannel(newChannel.getId());
            } catch (Exception e) { Log.d(TAG, "Error", e); }
        }

        @Override public void onChannelAdded(IChannel channel) { if (mChannelListAdapter != null) mChannelListAdapter.updateChannels(); }
        @Override public void onChannelRemoved(IChannel channel) { if (mChannelListAdapter != null) mChannelListAdapter.updateChannels(); }
        @Override public void onChannelStateUpdated(IChannel channel) { if (mChannelListAdapter != null) mChannelListAdapter.updateChannels(); }
        @Override public void onUserConnected(IUser user) { if (mChannelListAdapter != null) mChannelListAdapter.updateChannels(); }
        @Override public void onUserRemoved(IUser user, String reason) { if (mChannelListAdapter != null) mChannelListAdapter.updateChannels(); }

        @Override
        public void onUserStateUpdated(IUser user) {
            super.onUserStateUpdated(user);
            if (mChannelListAdapter != null && mChannelView != null && user != null) {
                mChannelListAdapter.refreshUserStatus(user.getSession());
                int posisi = mChannelListAdapter.getUserPositionBySession(user.getSession());
                if (posisi != -1) mChannelView.getAdapter().notifyItemChanged(posisi);
            }
            if (getActivity() != null && !isDetached()) getActivity().supportInvalidateOptionsMenu();
        }

        @Override
        public void onUserTalkStateUpdated(IUser user) {
            if (mChannelListAdapter != null && mChannelView != null) mChannelListAdapter.updateUserStates(user, mChannelView);
            if (getActivity() != null && !isDetached()) {
                getActivity().runOnUiThread(() -> {
                    bannerHideHandler.removeCallbacks(bannerHideRunnable);
                    String displayName = user.getName();
                    try {
                        int selfSession = getService().HumlaSession().getSessionId();
                        if (user.getSession() == selfSession) {
                            if (user.isTalking()) {
                                mulaiVisualizerKirim();
                            } else {
                                hentikanVisualizerKirim();
                            }
                        } else {
                            if (user.isTalking() && mVisualizerPanel != null) {
                                mVisualizerPanel.setVisibility(View.VISIBLE);
                                perbaruiVisualTerima(user);
                            }
                        }
                    } catch (Exception e) { Log.d(TAG, "Cek sesi gagal", e); }
                    
                    if (!displayName.equals(currentSpeakerName)) {
                        currentSpeakerName = displayName;
                        if (tvSpeakerName != null) tvSpeakerName.setText(displayName);
                    }
                    if (bannerActiveSpeaker != null && bannerActiveSpeaker.getVisibility() != View.VISIBLE) {
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
            if (getActivity() != null) getActivity().supportInvalidateOptionsMenu();
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        hitungUkuranBufferSuara();
    }

    @Override public void onAttach(Activity activity) {
        super.onAttach(activity);
        try { mTargetProvider = (ChatTargetProvider) getParentFragment(); }
        catch (ClassCastException e) { throw new ClassCastException("Parent must implement ChatTargetProvider"); }
        try { mDatabaseProvider = (DatabaseProvider) activity; }
        catch (ClassCastException e) { throw new ClassCastException("Activity must implement DatabaseProvider"); }
        mSettings = Settings.getInstance(activity);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_channel_list, container, false);
        mChannelView = view.findViewById(R.id.channelUsers);
        mChannelView.setLayoutManager(new LinearLayoutManager(getActivity()));
        bannerActiveSpeaker = view.findViewById(R.id.bannerActiveSpeaker);
        tvSpeakerName = view.findViewById(R.id.tvSpeakerName);
        mVisualizerPanel = view.findViewById(R.id.visualizerPanel);
        mVisKirim = view.findViewById(R.id.vis_sender);
        mVisTerima = view.findViewById(R.id.vis_receiver);
        if (mVisKirim != null && mVisTerima != null) {
            aturGrafik(mVisKirim, WARNA_KIRIM);
            aturGrafik(mVisTerima, WARNA_TERIMA);
        }
        return view;
    }

    @Override public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mintaIzinLokasiOtomatis();
    }

    @Override public void onActivityCreated(Bundle savedInstanceState) {
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
        hentikanVisualizerKirim();
        hentikanBacaLokasi();
        if (getActivity() != null) {
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                .unregisterOnSharedPreferenceChangeListener(this);
        }
        super.onDestroy();
    }

    @Override public IHumlaObserver getServiceObserver() { return mServiceObserver; }

    @Override public void onServiceBound(IHumlaService service) {
        try {
            if (mChannelListAdapter == null) setupChannelList();
            else mChannelListAdapter.setService(service);
        } catch (RemoteException e) { e.printStackTrace(); }
    }

    @Override public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem muteItem = menu.findItem(R.id.menu_mute_button);
        MenuItem deafenItem = menu.findItem(R.id.menu_deafen_button);
        MenuItem statusItem = menu.findItem(R.id.menu_status_pilihan);
        if (getService() != null && getService().isConnected()) {
            IHumlaSession session = getService().HumlaSession();
            int fgColor = requireActivity().getTheme()
                .obtainStyledAttributes(new int[]{android.R.attr.textColorPrimaryInverse})
                .getColor(0, -1);
            IUser self = null;
            try { self = session.getSessionUser(); } catch (Exception ignored) {}
            if (self != null) {
                muteItem.setIcon(self.isSelfMuted() ? R.drawable.ic_action_microphone_muted : R.drawable.ic_action_microphone);
                deafenItem.setIcon(self.isSelfDeafened() ? R.drawable.ic_action_audio_muted : R.drawable.ic_action_audio);
                if (muteItem.getIcon() != null) muteItem.getIcon().mutate().setColorFilter(fgColor, PorterDuff.Mode.MULTIPLY);
                if (deafenItem.getIcon() != null) deafenItem.getIcon().mutate().setColorFilter(fgColor, PorterDuff.Mode.MULTIPLY);
            }
            menu.findItem(R.id.menu_bluetooth).setChecked(session.usingBluetoothSco());
            if (statusItem != null) statusItem.setVisible(true);
        } else {
            if (statusItem != null) statusItem.setVisible(false);
        }
    }

    @Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_channel_list, menu);
        MenuItem searchItem = menu.findItem(R.id.menu_search);
        SearchManager sm = (SearchManager) requireActivity().getSystemService(Context.SEARCH_SERVICE);
        SearchView sv = (SearchView) MenuItemCompat.getActionView(searchItem);
        sv.setSearchableInfo(sm.getSearchableInfo(requireActivity().getComponentName()));
        sv.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override public boolean onSuggestionSelect(int pos) { return false; }
            @Override public boolean onSuggestionClick(int pos) {
                if (getService() == null || !getService().isConnected()) return false;
                CursorWrapper c = (CursorWrapper) sv.getSuggestionsAdapter().getItem(pos);
                String tipe = c.getString(c.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA));
                int id = c.getInt(c.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_DATA));
                IHumlaSession s = getService().HumlaSession();
                if (ChannelSearchProvider.INTENT_DATA_CHANNEL.equals(tipe)) {
                    if (s.getSessionChannel().getId() != id) s.joinChannel(id);
                    else scrollToChannel(id);
                    return true;
                } else if (ChannelSearchProvider.INTENT_DATA_USER.equals(tipe)) {
                    scrollToUser(id);
                    return true;
                }
                return false;
            }
        });
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (getService() == null || !getService().isConnected()) return super.onOptionsItemSelected(item);
        IHumlaSession s = getService().HumlaSession();
        int id = item.getItemId();
        if (id == R.id.menu_status_pilihan) { tampilkanPilihStatus(); return true; }
        else if (id == R.id.menu_mute_button) {
            IUser me = s.getSessionUser();
            if (me != null) {
                boolean mute = !me.isSelfMuted();
                s.setSelfMuteDeafState(mute, mute && me.isSelfDeafened());
            }
            requireActivity().supportInvalidateOptionsMenu();
            return true;
        }
        else if (id == R.id.menu_deafen_button) {
            IUser me = s.getSessionUser();
            if (me != null) s.setSelfMuteDeafState(!me.isSelfDeafened(), !me.isSelfDeafened());
            requireActivity().supportInvalidateOptionsMenu();
            return true;
        }
        else if (id == R.id.menu_bluetooth) {
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
    }

    public void scrollToChannel(int channelId) {
        mChannelView.scrollToPosition(mChannelListAdapter.getChannelPosition(channelId));
    }

    public void scrollToUser(int userId) {
        mChannelView.scrollToPosition(mChannelListAdapter.getUserPosition(userId));
    }

    private boolean isShowingPinnedChannels() {
        Bundle args = getArguments();
        return args != null && args.getBoolean("pinned");
    }

    @Override public void onChannelClick(IChannel ch) {
        ChatTargetProvider.ChatTarget t = mTargetProvider.getChatTarget();
        if (t != null && ch.equals(t.getChannel()) && mActionMode != null) mActionMode.finish();
        else {
            mActionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(
                new ChatTargetActionModeCallback(mTargetProvider, new ChatTargetProvider.ChatTarget(ch)) {
                    @Override public void onDestroyActionMode(ActionMode am) {
                        super.onDestroyActionMode(am);
                        mActionMode = null;
                    }
                });
        }
    }

    @Override public void onUserClick(IUser u) {
        ChatTargetProvider.ChatTarget t = mTargetProvider.getChatTarget();
        if (t != null && u.equals(t.getUser()) && mActionMode != null) mActionMode.finish();
        else {
            mActionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(
                new ChatTargetActionModeCallback(mTargetProvider, new ChatTargetProvider.ChatTarget(u)) {
                    @Override public void onDestroyActionMode(ActionMode am) {
                        super.onDestroyActionMode(am);
                        mActionMode = null;
                    }
                });
        }
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences p, String k) {
        if (Settings.PREF_SHOW_USER_COUNT.equals(k) && mChannelListAdapter != null) {
            mChannelListAdapter.setShowChannelUserCount(mSettings.shouldShowUserCount());
        }
    }
}
