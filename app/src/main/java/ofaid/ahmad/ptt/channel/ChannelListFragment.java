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

import java.util.ArrayList;
import java.util.List;

public class ChannelListFragment extends HumlaServiceFragment
        implements OnChannelClickListener, OnUserClickListener,
                   SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = ChannelListFragment.class.getName();
    private static final int KODE_IZIN_LOKASI = 1001;

    // === VISUALIZER ===
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
    // ==================

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
                    if (mVisualizerPanel != null) {
                        mVisualizerPanel.setVisibility(View.GONE);
                    }
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
    }

    // ========== VISUALIZER — PENGATURAN ==========
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
        
        if (mVisualizerPanel != null) {
            mVisualizerPanel.setVisibility(View.VISIBLE);
        }
        
        if (AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) > 0) {
            try {
                mPerekamSuara = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLING_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    mUkuranBufferSuara
                );
                mPerekamSuara.startRecording();
                mVisualHandler.post(mPembaruanVisual);
            } catch (Exception e) {
                Log.e(TAG, "Gagal mulai perekam visualizer", e);
                mVisualBerjalan = false;
            }
        }
    }

    private void hentikanVisualizerKirim() {
        mVisualBerjalan = false;
        if (mVisualHandler != null) {
            mVisualHandler.removeCallbacks(mPembaruanVisual);
        }
        if (mPerekamSuara != null) {
            try {
                mPerekamSuara.stop();
                mPerekamSuara.release();
            } catch (Exception e) { }
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
            
            if (mVisualBerjalan) {
                mVisualHandler.postDelayed(this, 50);
            }
        }
    };

    private int[] hitungTingkatSuara(short[] buffer, int panjang) {
        int[] hasil = new int[JUMLAH_BATANG_VISUAL];
        int perBagian = panjang / JUMLAH_BATANG_VISUAL;
        if (perBagian < 1) perBagian = 1;

        for (int b = 0; b < JUMLAH_BATANG_VISUAL; b++) {
            int total = 0;
            int mulai = b * perBagian;
            int akhir = Math.min(mulai + perBagian, panjang);
            for (int i = mulai; i < akhir; i++) {
                total += Math.abs(buffer[i]);
            }
            hasil[b] = Math.min(100, total / perBagian / 8);
        }
        return hasil;
    }

    private void perbaruiGrafik(BarChart grafik, int[] nilai, int warna) {
        if (grafik == null) return;
        List<BarEntry> daftar = new ArrayList<>();
        for (int i = 0; i < nilai.length; i++) {
            daftar.add(new BarEntry(i, nilai[i] / 8.0f));
        }
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
        for (int i = 0; i < tingkat.length; i++) {
            tingkat[i] = (int)(Math.random() * 40 + 10);
        }
        perbaruiGrafik(mVisTerima, tingkat, WARNA_TERIMA);
    }
    // ==========================================

    // ========== HENTIKAN BACA LOKASI ==========
    private void hentikanBacaLokasi() {
        if (mLocationManager != null) {
            mLocationManager.removeUpdates(lokasiPendengar);
        }
    }

    // ========== MULAI BACA LOKASI ==========
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
            }
        }
    }

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
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Izin lokasi tidak tersedia", e);
        }
    }

    // ========== BACA NAMA LOKASI & KIRIM KE SERVER ==========
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
                kirimLokasiKeServer(lokasiTerbaca);
            } else {
                lokasiTerbaca = String.format("📍 %.4f, %.4f", lokasi.getLatitude(), lokasi.getLongitude());
                kirimLokasiKeServer(lokasiTerbaca);
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal baca nama lokasi", e);
            lokasiTerbaca = String.format("📍 %.4f, %.4f", lokasi.getLatitude(), lokasi.getLongitude());
            kirimLokasiKeServer(lokasiTerbaca);
        }
    }

    private void kirimLokasiKeServer(String teksLokasi) {
        if (getService() == null || !getService().isConnected()) {
            Log.w(TAG, "Belum terhubung — lokasi belum dikirim");
            return;
        }
        try {
            IHumlaSession sesi = getService().HumlaSession();
            IUser saya = sesi.getSessionUser();
            if (saya == null) return;
            
            int sesiSaya = saya.getSession();
            String keteranganLama = saya.getComment();
            String keteranganBaru;
            
            if (keteranganLama == null || keteranganLama.trim().isEmpty()) {
                keteranganBaru = teksLokasi;
            } else if (keteranganLama.trim().startsWith("📍")) {
                keteranganBaru = teksLokasi;
            } else {
                keteranganBaru = keteranganLama + "\n" + teksLokasi;
            }
            
            sesi.setUserComment(sesiSaya, keteranganBaru);
            Log.i(TAG, "✅ Lokasi dikirim: " + teksLokasi);
            
        } catch (Exception e) {
            Log.e(TAG, "Gagal kirim lokasi", e);
        }
    }

    // ========== PEMANTAU PERUBAHAN ==========
    private final IHumlaObserver mServiceObserver = new HumlaObserver() {
        @Override
        public void onDisconnected(HumlaException e) {
            if (mChannelView != null) mChannelView.setAdapter(null);
            hentikanVisualizerKirim();
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
                    
                    // === VISUALIZER — DIPERBAIKI ✅ ===
                    try {
                        int selfSession = getService().HumlaSession().getSessionId();
                        // Pakai angka langsung: 0 = PASSIVE, 1 = TALKING
                        int state = user.getTalkState();
                        
                        if (user.getSession() == selfSession) {
                            // Saya yang bicara
                            if (state != 0) {
                                mulaiVisualizerKirim();
                            } else {
                                hentikanVisualizerKirim();
                            }
                        } else {
                            // Orang lain bicara
                            if (state != 0) {
                                if (mVisualizerPanel != null) {
                                    mVisualizerPanel.setVisibility(View.VISIBLE);
                                }
                                perbaruiVisualTerima(user);
                            }
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "Cek sesi visualizer gagal", e);
                    }
                    // ================================
                    
                    if (!displayName.equals(currentSpeakerName)) {
                        currentSpeakerName = displayName;
                        if (tvSpeakerName != null) tvSpeakerName.setText(displayName);
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
        hitungUkuranBufferSuara();
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
        
        mVisualizerPanel = view.findViewById(R.id.visualizerPanel);
        mVisKirim = view.findViewById(R.id.vis_sender);
        mVisTerima = view.findViewById(R.id.vis_receiver);
        
        if (mVisKirim != null && mVisTerima != null) {
            aturGrafik(mVisKirim, WARNA_KIRIM);
            aturGrafik(mVisTerima, WARNA_TERIMA);
        }
        
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
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
        hentikanVisualizerKirim();
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
            if (statusItem != null) statusItem.setVisible(true);
        } else {
            if (statusItem != null) statusItem.setVisible(false);
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
