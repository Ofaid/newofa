/*
 * Copyright (C) 2014 Andrew Comminos modif OFAID 2026*/
 
package ofaid.ahmad.ptt.channel;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.PagerTabStrip;
import androidx.viewpager.widget.ViewPager;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.BarDataSet;

import java.util.ArrayList;
import java.util.List;

import se.lublin.humla.HumlaService;
import se.lublin.humla.IHumlaService;
import se.lublin.humla.IHumlaSession;
import se.lublin.humla.model.IUser;
import se.lublin.humla.model.WhisperTarget;
import se.lublin.humla.util.HumlaDisconnectedException;
import se.lublin.humla.util.HumlaObserver;
import se.lublin.humla.util.IHumlaObserver;
import se.lublin.humla.util.VoiceTargetMode;
import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.Settings;
import ofaid.ahmad.ptt.util.AvatarUtil;
import ofaid.ahmad.ptt.util.HumlaServiceFragment;

/**
 * Class to encapsulate both a ChannelListFragment and ChannelChatFragment.
 * Created by andrew on 02/08/13.
 * Modif OFAID: +Avatar +Visualizer Real-Time
 */
public class ChannelFragment extends HumlaServiceFragment implements SharedPreferences.OnSharedPreferenceChangeListener, ChatTargetProvider {
    private static final String TAG = ChannelFragment.class.getName();

    private static final int KODE_PILIH_AVATAR = 1001;
    
    // === KONFIGURASI VISUALIZER ===
    private static final int JUMLAH_BATANG_VISUAL = 16;
    private static final int WARNA_KIRIM = 0xFF4CAF50;   // Hijau = kamu bicara
    private static final int WARNA_TERIMA = 0xFF2196F3;  // Biru = mereka bicara
    private static final int SAMPLING_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // ===============================

    private ViewPager mViewPager;
    private PagerTabStrip mTabStrip;
    private Button mTalkButton;
    private View mTalkView;
    private Button mAvatarButton;
    
    // === VISUALIZER ===
    private BarChart mVisKirim;
    private BarChart mVisTerima;
    private Handler mVisualHandler;
    private boolean mVisualBerjalan = false;
    private AudioRecord mPerekamSuara;
    private int mUkuranBuffer;
    // ==================

    private View mTargetPanel;
    private ImageView mTargetPanelCancel;
    private TextView mTargetPanelText;

    private ChatTarget mChatTarget;
    private List<OnChatTargetSelectedListener> mChatTargetListeners = new ArrayList<OnChatTargetSelectedListener>();

    private boolean mTalkButtonHidden;
    private Handler mHandler = new Handler();

    private HumlaObserver mObserver = new HumlaObserver() {
        @Override
        public void onUserTalkStateUpdated(IUser user) {
            if (getService() == null || !getService().isConnected()) return;
            int selfSession;
            try {
                selfSession = getService().HumlaSession().getSessionId();
            } catch (HumlaDisconnectedException|IllegalStateException e) {
                Log.d(TAG, "exception in onUserTalkStateUpdated: " + e);
                return;
            }
            if (user != null && user.getSession() == selfSession) {
                switch (user.getTalkState()) {
                    case TALKING: case SHOUTING: case WHISPERING:
                        mTalkButton.setPressed(true);
                        mulaiVisualizerKirim();
                        break;
                    case PASSIVE:
                        mTalkButton.setPressed(false);
                        hentikanVisualizerKirim();
                        break;
                }
            }
            // === VISUALIZER UNTUK PENERIMA ===
            else if (user != null && user.getTalkState() != IUser.TalkState.PASSIVE) {
                perbaruiVisualTerimaDariServer(user);
            }
        }

        @Override
        public void onUserStateUpdated(IUser user) {
            if (getService() == null || !getService().isConnected()) return;
            int selfSession;
            try {
                selfSession = getService().HumlaSession().getSessionId();
            } catch (IllegalStateException e) {
                Log.d(TAG, "exception in onUserStateUpdated: " + e);
                return;
            }
            if (user != null && user.getSession() == selfSession) {
                configureInput();
            }
        }

        @Override
        public void onVoiceTargetChanged(VoiceTargetMode mode) {
            configureTargetPanel();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mVisualHandler = new Handler(Looper.getMainLooper());
        hitungUkuranBufferSuara();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_channel, container, false);
        mViewPager = (ViewPager) view.findViewById(R.id.channel_view_pager);
        mTabStrip = (PagerTabStrip) view.findViewById(R.id.channel_tab_strip);
        
        if(mTabStrip != null) {
            int[] attrs = new int[] { android.R.attr.colorPrimary, android.R.attr.textColorPrimaryInverse };
            TypedArray a = getActivity().obtainStyledAttributes(attrs);
            int titleStripBackground = a.getColor(0, -1);
            int titleStripColor = a.getColor(1, -1);
            a.recycle();
            mTabStrip.setTextColor(titleStripColor);
            mTabStrip.setTabIndicatorColor(titleStripColor);
            mTabStrip.setBackgroundColor(titleStripBackground);
            mTabStrip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        }

        mTalkView = view.findViewById(R.id.pushtotalk_view);
        mTalkButton = (Button) view.findViewById(R.id.pushtotalk);

        // === VISUALIZER — PASANG GRAFIK ===
        mVisKirim = (BarChart) view.findViewById(R.id.vis_sender);
        mVisTerima = (BarChart) view.findViewById(R.id.vis_receiver);
        if (mVisKirim != null && mVisTerima != null) {
            aturGrafik(mVisKirim, WARNA_KIRIM);
            aturGrafik(mVisTerima, WARNA_TERIMA);
        }
        // ==================================

        // === TOMBOL PILIH AVATAR ===
        mAvatarButton = (Button) view.findViewById(R.id.tombol_pilih_avatar);
        if (mAvatarButton != null) {
            mAvatarButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent bukaGaleri = new Intent(Intent.ACTION_PICK);
                    bukaGaleri.setType("image/*");
                    startActivityForResult(bukaGaleri, KODE_PILIH_AVATAR);
                }
            });
        }
        // ===========================

        mTalkButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (getService() != null) {
                            getService().onTalkKeyDown();
                        }
                        mulaiVisualizerKirim();
                        break;
                    case MotionEvent.ACTION_UP:
                        if (getService() != null) {
                            getService().onTalkKeyUp();
                        }
                        hentikanVisualizerKirim();
                        break;
                }
                return true;
            }
        });

        mTargetPanel = view.findViewById(R.id.target_panel);
        mTargetPanelCancel = (ImageView) view.findViewById(R.id.target_panel_cancel);
        mTargetPanelCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getService() == null || !getService().isConnected()) return;
                IHumlaSession session = getService().HumlaSession();
                if (session.getVoiceTargetMode() == VoiceTargetMode.WHISPER) {
                    byte target = session.getVoiceTargetId();
                    session.setVoiceTargetId((byte) 0);
                    session.unregisterWhisperTarget(target);
                }
            }
        });
        mTargetPanelText = (TextView) view.findViewById(R.id.target_panel_warning);
        configureInput();
        return view;
    }

    // === VISUALIZER — PENGATURAN GRAFIK ===
    private void hitungUkuranBufferSuara() {
        int ukuranMin = AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        mUkuranBuffer = Math.max(1024, ukuranMin);
    }

    private void aturGrafik(BarChart grafik, int warna) {
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
        
        if (AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) > 0) {
            try {
                mPerekamSuara = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLING_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    mUkuranBuffer
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
        mVisualHandler.removeCallbacks(mPembaruanVisual);
        if (mPerekamSuara != null) {
            try {
                mPerekamSuara.stop();
                mPerekamSuara.release();
            } catch (Exception e) { }
            mPerekamSuara = null;
        }
        if (mVisKirim != null) aturGrafik(mVisKirim, WARNA_KIRIM);
    }

    private Runnable mPembaruanVisual = new Runnable() {
        @Override
        public void run() {
            if (!mVisualBerjalan || mPerekamSuara == null) return;
            
            short[] buffer = new short[mUkuranBuffer];
            int dibaca = mPerekamSuara.read(buffer, 0, buffer.length);
            
            if (dibaca > 0) {
                int[] tingkat = hitungTingkatSuara(buffer, dibaca);
                perbaruiGrafik(mVisKirim, tingkat, WARNA_KIRIM);
            }
            
            if (mVisualBerjalan) {
                mVisualHandler.postDelayed(this, 50); // perbarui tiap 50ms
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

    private void perbaruiVisualTerimaDariServer(IUser user) {
        // Nanti disambungkan ke data suara masuk
        // Sementara tampilkan indikator sederhana
        if (mVisTerima == null) return;
        
        // Nanti: ambil level dari aliran suara masuk
        // Sementara pakai acak untuk uji coba
        int[] tingkat = new int[JUMLAH_BATANG_VISUAL];
        for (int i = 0; i < tingkat.length; i++) {
            tingkat[i] = (int)(Math.random() * 40 + 10);
        }
        perbaruiGrafik(mVisTerima, tingkat, WARNA_TERIMA);
    }
    // ==========================================

    // === AVATAR — PROSES SIMPAN & KIRIM ===
    @Override
    public void onActivityResult(int kodePermintaan, int kodeHasil, Intent data) {
        super.onActivityResult(kodePermintaan, kodeHasil, data);
        if (kodePermintaan == KODE_PILIH_AVATAR && kodeHasil == getActivity().RESULT_OK) {
            Uri uriGambar = data.getData();
            Log.i("AvatarLayar", "🟢 Gambar dipilih");

            byte[] dataAvatar = AvatarUtil.olahGambar(getActivity(), uriGambar);
            if (dataAvatar == null) {
                Toast.makeText(getActivity(), "Gagal memproses gambar", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.i("AvatarLayar", "✅ Gambar siap — " + dataAvatar.length + " byte");
            IHumlaService layanan = getService();
            if (layanan == null || !layanan.isConnected()) {
                Toast.makeText(getActivity(), "Belum terhubung ke server", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                IHumlaSession sesi = layanan.HumlaSession();
                int nomorSesi = sesi.getSessionId();
                sesi.setUserTexture(nomorSesi, dataAvatar);
                AvatarUtil.simpanAvatar(getActivity(), dataAvatar);
                Toast.makeText(getActivity(), "Avatar dikirim & disimpan! ✅", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("AvatarLayar", "🔴 Gagal kirim", e);
                Toast.makeText(getActivity(), "Gagal mengirim avatar", Toast.LENGTH_SHORT).show();
            }
        }
    }
    // ======================================

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        preferences.registerOnSharedPreferenceChangeListener(this);

        if(mViewPager != null) {
            ChannelFragmentPagerAdapter pagerAdapter = new ChannelFragmentPagerAdapter(getChildFragmentManager());
            mViewPager.setAdapter(pagerAdapter);
        } else {
            ChannelListFragment listFragment = new ChannelListFragment();
            Bundle listArgs = new Bundle();
            listArgs.putBoolean("pinned", isShowingPinnedChannels());
            listFragment.setArguments(listArgs);
            ChannelChatFragment chatFragment = new ChannelChatFragment();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.list_fragment, listFragment)
                    .replace(R.id.chat_fragment, chatFragment)
                    .commit();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.channel_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Settings settings = Settings.getInstance(getActivity());
        int itemId = item.getItemId();
        if (itemId == R.id.menu_input_voice) {
            settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_VOICE);
            return true;
        } else if (itemId == R.id.menu_input_ptt) {
            settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_PTT);
            return true;
        } else if (itemId == R.id.menu_input_continuous) {
            settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_CONTINUOUS);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        hentikanVisualizerKirim();
        if (getService() != null && getService().isConnected() &&
            !Settings.getInstance(getActivity()).isPushToTalkToggle()) {
            try {
                getService().HumlaSession().setTalkingState(false);
            } catch (Exception e) {
                Log.d(TAG, "onPause gagal ubah state bicara", e);
            }
        }
    }

    @Override
    public void onDestroy() {
        hentikanVisualizerKirim();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public IHumlaObserver getServiceObserver() {
        return mObserver;
    }

    @Override
    public void onServiceBound(IHumlaService service) {
        super.onServiceBound(service);
        if (service.getConnectionState() == HumlaService.ConnectionState.CONNECTED) {
            configureTargetPanel();
            configureInput();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    kirimUlangAvatarTersimpan();
                }
            }, 800);
        }
    }

    private void kirimUlangAvatarTersimpan() {
        if (!isAdded() || getActivity() == null) return;
        byte[] fotoTersimpan = AvatarUtil.ambilAvatarTersimpan(getActivity());
        if (fotoTersimpan == null) {
            Log.i("AvatarOtomatis", "ℹ️ Belum ada foto tersimpan, lewati");
            return;
        }
        try {
            IHumlaService layanan = getService();
            if (layanan == null || !layanan.isConnected()) return;
            IHumlaSession sesi = layanan.HumlaSession();
            int nomorSaya = sesi.getSessionId();
            sesi.setUserTexture(nomorSaya, fotoTersimpan);
            Log.i("AvatarOtomatis", "✅ Foto dikirim ulang otomatis! — " + fotoTersimpan.length + " byte");
        } catch (Exception e) {
            Log.e("AvatarOtomatis", "🔴 Gagal kirim ulang foto", e);
        }
    }

    private void configureTargetPanel() {
        if (getService() == null || !getService().isConnected()) return;
        IHumlaSession session = getService().HumlaSession();
        VoiceTargetMode mode = session.getVoiceTargetMode();
        if (mode == VoiceTargetMode.WHISPER) {
            WhisperTarget target = session.getWhisperTarget();
            mTargetPanel.setVisibility(View.VISIBLE);
            mTargetPanelText.setText(getString(R.string.shout_target, target.getName()));
        } else {
            mTargetPanel.setVisibility(View.GONE);
        }
    }

    private boolean isShowingPinnedChannels() {
        return getArguments() != null && getArguments().getBoolean("pinned");
    }

    private void configureInput() {
        Settings settings = Settings.getInstance(getActivity());
        ViewGroup.LayoutParams params = mTalkView.getLayoutParams();
        params.height = settings.getPTTButtonHeight();
        mTalkButton.setLayoutParams(params);

        boolean muted = false;
        if (getService() != null && getService().isConnected()) {
            IUser self = null;
            try {
                self = getService().HumlaSession().getSessionUser();
            } catch (HumlaDisconnectedException|IllegalStateException e) {
                Log.d(TAG, "exception in configureInput: " + e);
            }
            muted = self == null || self.isMuted() || self.isSuppressed() || self.isSelfMuted();
        }
        boolean showPttButton =
                !muted &&
                settings.isPushToTalkButtonShown() &&
                settings.getInputMethod().equals(Settings.ARRAY_INPUT_METHOD_PTT);
        setTalkButtonHidden(!showPttButton);
    }

    private void setTalkButtonHidden(final boolean hidden) {
        mTalkView.setVisibility(hidden ? View.GONE : View.VISIBLE);
        mTalkButtonHidden = hidden;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(Settings.PREF_INPUT_METHOD.equals(key)
            || Settings.PREF_PUSH_BUTTON_HIDE_KEY.equals(key)
            || Settings.PREF_PTT_BUTTON_HEIGHT.equals(key))
            configureInput();
    }

    @Override
    public ChatTarget getChatTarget() {
        return mChatTarget;
    }

    @Override
    public void setChatTarget(ChatTarget target) {
        mChatTarget = target;
        for(OnChatTargetSelectedListener listener : mChatTargetListeners)
            listener.onChatTargetSelected(target);
    }

    @Override
    public void registerChatTargetListener(OnChatTargetSelectedListener listener) {
        mChatTargetListeners.add(listener);
    }

    @Override
    public void unregisterChatTargetListener(OnChatTargetSelectedListener listener) {
        mChatTargetListeners.remove(listener);
    }

    private class ChannelFragmentPagerAdapter extends FragmentPagerAdapter {
        public ChannelFragmentPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            Fragment fragment = null;
            Bundle args = new Bundle();
            switch (i) {
                case 0:
                    fragment = new ChannelListFragment();
                    args.putBoolean("pinned", isShowingPinnedChannels());
                    break;
                case 1:
                    fragment = new ChannelChatFragment();
                    break;
            }
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0: return getString(R.string.channel).toUpperCase();
                case 1: return getString(R.string.chat).toUpperCase();
                default: return null;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    }
}
