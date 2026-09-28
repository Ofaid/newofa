/*
 * Copyright (C) 2014 Andrew Comminos
 * OFAID 2026 — Paket disesuaikan
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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
            IChannel myChan;
            try {
                myChan = mService.getSessionChannel();
            } catch (IllegalStateException e) {
                return;
            }
            if (myChan == null || user.getChannel() == null) return;
            if (user.getChannel().equals(myChan)) {
                mChannelAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onUserJoinedChannel(IUser user, IChannel newChannel, IChannel oldChannel) {
            int selfSession;
            try {
                selfSession = mService.getSessionId();
            } catch (IllegalStateException e) {
                return;
            }

            if (user.getSession() == selfSession) {
                IChannel myChan;
                try {
                    myChan = mService.getSessionChannel();
                } catch (IllegalStateException e) {
                    return;
                }
                if (myChan != null && mChannelAdapter != null) {
                    mChannelAdapter.setChannel(myChan);
                }
            } else {
                IChannel myChan;
                try {
                    myChan = mService.getSessionChannel();
                } catch (IllegalStateException e) {
                    return;
                }
                if (myChan == null) return;
                int myId = myChan.getId();
                if ((newChannel != null && newChannel.getId() == myId) ||
                    (oldChannel != null && oldChannel.getId() == myId)) {
                    if (mChannelAdapter != null) {
                        mChannelAdapter.notifyDataSetChanged();
                    }
                }
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
        mOverlayView = View.inflate(service, R.layout.overlay, null);
        
        if (mOverlayView == null) {
            Log.e(TAG, "Gagal muat layout");
            return;
        }
        
        mTalkButton = (ImageView) mOverlayView.findViewById(R.id.overlay_talk);
        mDragButton = (ImageView) mOverlayView.findViewById(R.id.overlay_drag);
        mCloseButton = (ImageView) mOverlayView.findViewById(R.id.overlay_close);
        mTitleView = mOverlayView.findViewById(R.id.overlay_title);
        mOverlayList = (ListView) mOverlayView.findViewById(R.id.overlay_list);

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
                    wm.updateViewLayout(mOverlayView, mOverlayParams);
                    return true;
                }
                return false;
            }
        });

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
                    mOverlayParams.width = (int) (w0 + (e.getRawX() - x0));
                    mOverlayParams.height = (int) (h0 + (e.getRawY() - y0));
                    wm.updateViewLayout(mOverlayView, mOverlayParams);
                    return true;
                }
                return false;
            }
        });

        mTalkButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (mService == null) return false;
                try {
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        mService.setTalkingState(true);
                        return true;
                    } else if (e.getAction() == MotionEvent.ACTION_UP) {
                        mService.setTalkingState(false);
                        return true;
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Error tombol: " + ex.getMessage());
                }
                return false;
            }
        });

        Settings settings = Settings.getInstance(service);
        boolean pttMode = Settings.ARRAY_INPUT_METHOD_PTT.equals(settings.getInputMethod());
        setPushToTalkShown(pttMode);

        mCloseButton.setOnClickListener(v -> hide());

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
        if (mShown || mService == null) return;

        IChannel saluran;
        try {
            saluran = mService.getSessionChannel();
        } catch (IllegalStateException e) {
            Toast.makeText(mService, "Sambung ke server dulu!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (saluran == null) {
            Toast.makeText(mService, "Masuk saluran dulu!", Toast.LENGTH_SHORT).show();
            return;
        }

        mShown = true;
        mChannelAdapter = new ChannelAdapter(mService, saluran);
        mOverlayList.setAdapter(mChannelAdapter);
        mService.registerObserver(mObserver);

        try {
            WindowManager wm = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            wm.addView(mOverlayView, mOverlayParams);
        } catch (Exception e) {
            Toast.makeText(mService, "Gagal tampil: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Sudah hilang: " + e.getMessage());
        }
    }

    public void setPushToTalkShown(boolean tampil) {
        if (mTalkButton != null) {
            mTalkButton.setVisibility(tampil ? View.VISIBLE : View.GONE);
        }
    }
}
