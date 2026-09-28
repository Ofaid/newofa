/*
 * Copyright (C) 2014 Andrew Comminos
 * OFAID 2026
 */
package ofaid.ahmad.ptt.service;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import se.lublin.humla.model.IChannel;
import se.lublin.humla.model.IUser;
import se.lublin.humla.util.HumlaObserver;
import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.Settings;
import ofaid.ahmad.ptt.channel.ChannelAdapter;

public class MumlaOverlay {
    private static final String TAG = "OFAID-Overlay";

    public static final int DEFAULT_WIDTH = 200;
    public static final int DEFAULT_HEIGHT = 240;

    private HumlaObserver mObserver = new HumlaObserver() {
        @Override
        public void onUserTalkStateUpdated(IUser user) {
            if (mChannelAdapter != null) mChannelAdapter.notifyDataSetChanged();
        }

        @Override
        public void onUserStateUpdated(IUser user) {
            if (mChannelAdapter == null || mService == null) return;
            try {
                IChannel myChan = mService.getSessionChannel();
                if (myChan != null && user.getChannel() != null && user.getChannel().equals(myChan)) {
                    mChannelAdapter.notifyDataSetChanged();
                }
            } catch (Exception e) {
                Log.d(TAG, "Cek saluran gagal: " + e.getMessage());
            }
        }

        @Override
        public void onUserJoinedChannel(IUser user, IChannel newCh, IChannel oldCh) {
            if (mService == null) return;
            try {
                int self = mService.getSessionId();
                if (user.getSession() == self) {
                    IChannel curr = mService.getSessionChannel();
                    if (curr != null && mChannelAdapter != null) {
                        mChannelAdapter.setChannel(curr);
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Cek pengguna gagal: " + e.getMessage());
            }
        }
    };

    private View mOverlayView;
    private ListView mOverlayList;
    private ChannelAdapter mChannelAdapter;
    private ImageView mTalkButton;
    private ImageView mCloseButton;
    private ImageView mDragButton;
    private View mTitleView;
    private WindowManager.LayoutParams mOverlayParams;
    private boolean mShown = false;
    private MumlaService mService;

    public MumlaOverlay(MumlaService service) {
        mService = service;
        try {
            mOverlayView = View.inflate(service, R.layout.overlay, null);
        } catch (Exception e) {
            Log.e(TAG, "❌ Gagal memuat layout overlay: " + e.getMessage());
            return;
        }
        
        if (mOverlayView == null) {
            Log.e(TAG, "❌ Overlay view null!");
            return;
        }

        mTalkButton = (ImageView) mOverlayView.findViewById(R.id.overlay_talk);
        mDragButton = (ImageView) mOverlayView.findViewById(R.id.overlay_drag);
        mCloseButton = (ImageView) mOverlayView.findViewById(R.id.overlay_close);
        mTitleView = mOverlayView.findViewById(R.id.overlay_title);
        mOverlayList = (ListView) mOverlayView.findViewById(R.id.overlay_list);

        // ✅ DRAG TITLE BAR
        mTitleView.setOnTouchListener(new View.OnTouchListener() {
            private final WindowManager wm = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            private float x0, y0;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (mOverlayParams == null) return false;
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    x0 = e.getRawX() - mOverlayParams.x;
                    y0 = e.getRawY() - mOverlayParams.y;
                    return true;
                } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    mOverlayParams.x = (int) (e.getRawX() - x0);
                    mOverlayParams.y = (int) (e.getRawY() - y0);
                    try { wm.updateViewLayout(mOverlayView, mOverlayParams); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }
        });

        // ✅ RESIZE HANDLE
        mDragButton.setOnTouchListener(new View.OnTouchListener() {
            private final WindowManager wm = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            private float x0, y0, w0, h0;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (mOverlayParams == null) return false;
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    x0 = e.getRawX(); y0 = e.getRawY();
                    w0 = mOverlayView.getWidth(); h0 = mOverlayView.getHeight();
                    return true;
                } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    mOverlayParams.width = Math.max(100, (int) (w0 + (e.getRawX() - x0)));
                    mOverlayParams.height = Math.max(100, (int) (h0 + (e.getRawY() - y0)));
                    try { wm.updateViewLayout(mOverlayView, mOverlayParams); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }
        });

        // ✅ PTT BUTTON DENGAN SAFETY CHECK LENGKAP
        mTalkButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (mService == null) {
                    Log.e(TAG, "Service null saat touch PTT");
                    return false;
                }
                
                try {
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        if (!mService.isConnectionEstablished()) {
                            Toast.makeText(mService, "Belum connect ke server!", Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        mService.onTalkKeyDown();
                        return true;
                    } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                        if (!mService.isConnectionEstablished()) return false;
                        mService.onTalkKeyUp();
                        return true;
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "CRASH SAAT PTT: " + ex.getMessage(), ex);
                    Toast.makeText(mService, "Error Mic: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                }
                return false;
            }
        });

        mCloseButton.setOnClickListener(v -> hide());

        Settings s = Settings.getInstance(service);
        setPushToTalkShown(Settings.ARRAY_INPUT_METHOD_PTT.equals(s.getInputMethod()));

        DisplayMetrics dm = mService.getResources().getDisplayMetrics();
        int tipeJendela = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        
        mOverlayParams = new WindowManager.LayoutParams(
                (int) (DEFAULT_WIDTH * dm.density),
                (int) (DEFAULT_HEIGHT * dm.density),
                tipeJendela,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        mOverlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        mOverlayParams.windowAnimations = android.R.style.Animation_Dialog;
    }

    public boolean isShown() { return mShown; }

    public void show() {
        if (mShown || mService == null || mOverlayView == null) {
            Log.d(TAG, "Belum siap tampil");
            return;
        }

        IChannel saluran = null;
        try {
            if (!mService.isConnectionEstablished()) {
                Toast.makeText(mService, "Sambung ke server dulu!", Toast.LENGTH_SHORT).show();
                return;
            }
            saluran = mService.getSessionChannel();
        } catch (Exception e) {
            Log.e(TAG, "Gagal ambil channel: " + e.getMessage());
            Toast.makeText(mService, "Error ambil data channel", Toast.LENGTH_SHORT).show();
            return;
        }

        if (saluran == null) {
            Toast.makeText(mService, "Masuk saluran dulu!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            mShown = true;
            mChannelAdapter = new ChannelAdapter(mService, saluran);
            mOverlayList.setAdapter(mChannelAdapter);
            mService.registerObserver(mObserver);
            
            WindowManager wm = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            wm.addView(mOverlayView, mOverlayParams);
            Log.i(TAG, "✅ Overlay tampil sukses");
        } catch (Exception e) {
            Log.e(TAG, "Gagal tampilkan overlay: " + e.getMessage(), e);
            Toast.makeText(mService, "Gagal tampil overlay", Toast.LENGTH_SHORT).show();
            mShown = false;
        }
    }

    public void hide() {
        if (!mShown || mService == null) return;
        mShown = false;
        try { mService.unregisterObserver(mObserver); } catch (Exception ignored) {}
        mOverlayList.setAdapter(null);
        mChannelAdapter = null;
        try {
            WindowManager wm = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(mOverlayView);
        } catch (Exception ignored) {}
    }

    public void setPushToTalkShown(boolean tampil) {
        if (mTalkButton != null) {
            mTalkButton.setVisibility(tampil ? View.VISIBLE : View.GONE);
        }
    }
}