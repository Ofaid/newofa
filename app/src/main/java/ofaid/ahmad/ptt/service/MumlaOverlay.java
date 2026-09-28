/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package se.lublin.mumla.service;

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
import se.lublin.mumla.R;
import se.lublin.mumla.Settings;
import se.lublin.mumla.channel.ChannelAdapter;

public class MumlaOverlay {
    private static final String TAG = MumlaOverlay.class.getName();

    public static final int DEFAULT_WIDTH = 200;
    public static final int DEFAULT_HEIGHT = 240;

    private HumlaObserver mObserver = new HumlaObserver() {
        @Override
        public void onUserTalkStateUpdated(IUser user) {
            if (mChannelAdapter != null) {
                mChannelAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onUserStateUpdated(IUser user) {
            // ✅ DIPERBAIKI: Cek tidak null dulu
            IChannel myChan;
            try {
                myChan = mService.getSessionChannel();
            } catch (IllegalStateException e) {
                return;
            }
            if (myChan == null || user.getChannel() == null) {
                return;
            }
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
                Log.d(TAG, "exception in onUserJoinedChannel: " + e);
                return;
            }

            if (user.getSession() == selfSession) {
                // ✅ DIPERBAIKI: Cek tidak null dulu
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
                // ✅ DIPERBAIKI: Cek tidak null sebelum panggil .getId()
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
        
        // ✅ Cek layout berhasil dimuat
        if (mOverlayView == null) {
            Log.e(TAG, "Gagal memuat layout overlay");
            return;
        }
        
        mTalkButton = (ImageView) mOverlayView.findViewById(R.id.overlay_talk);
        mDragButton = (ImageView) mOverlayView.findViewById(R.id.overlay_drag);
        mCloseButton = (ImageView) mOverlayView.findViewById(R.id.overlay_close);
        mTitleView = mOverlayView.findViewById(R.id.overlay_title);
        mOverlayList = (ListView) mOverlayView.findViewById(R.id.overlay_list);

        mTitleView.setOnTouchListener(new View.OnTouchListener() {
            private final WindowManager mWindowManager = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            private float mInitialX;
            private float mInitialY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mOverlayParams == null) return false;
                if (MotionEvent.ACTION_DOWN == event.getAction()) {
                    mInitialX = event.getRawX() - mOverlayParams.x;
                    mInitialY = event.getRawY() - mOverlayParams.y;
                    return true;
                } else if (MotionEvent.ACTION_MOVE == event.getAction()) {
                    mOverlayParams.x = (int) (event.getRawX() - mInitialX);
                    mOverlayParams.y = (int) (event.getRawY() - mInitialY);
                    mWindowManager.updateViewLayout(mOverlayView, mOverlayParams);
                    return true;
                }
                return false;
            }
        });

        mDragButton.setOnTouchListener(new View.OnTouchListener() {
            private final WindowManager mWindowManager = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            private float mInitialX;
            private float mInitialY;
            private float mInitialWidth;
            private float mInitialHeight;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mOverlayParams == null) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mInitialX = event.getRawX();
                        mInitialY = event.getRawY();
                        mInitialWidth = mOverlayView.getWidth();
                        mInitialHeight = mOverlayView.getHeight();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        mOverlayParams.width = (int) (mInitialWidth + (event.getRawX() - mInitialX));
                        mOverlayParams.height = (int) (mInitialHeight + (event.getRawY() - mInitialY));
                        mWindowManager.updateViewLayout(mOverlayView, mOverlayParams);
                        return true;
                }
                return false;
            }
        });

        mTalkButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mService == null) return false;
                try {
                    if (MotionEvent.ACTION_DOWN == event.getAction()) {
                        mService.setTalkingState(true);
                        return true;
                    } else if (MotionEvent.ACTION_UP == event.getAction()) {
                        mService.setTalkingState(false);
                        return true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error tombol bicara: " + e.getMessage());
                }
                return false;
            }
        });

        Settings settings = Settings.getInstance(service);
        boolean usingPtt = Settings.ARRAY_INPUT_METHOD_PTT.equals(settings.getInputMethod());
        setPushToTalkShown(usingPtt);

        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });

        DisplayMetrics metrics = mService.getResources().getDisplayMetrics();
        mOverlayParams = new WindowManager.LayoutParams(
                (int) (DEFAULT_WIDTH * metrics.density),
                (int) (DEFAULT_HEIGHT * metrics.density),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        mOverlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        mOverlayParams.windowAnimations = android.R.style.Animation_Dialog;
    }

    public boolean isShown() {
        return mShown;
    }

    public void show() {
        if (mShown) return;
        
        // ✅ DIPERBAIKI: Cek sudah masuk saluran dulu
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
            WindowManager windowManager = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            windowManager.addView(mOverlayView, mOverlayParams);
        } catch (Exception e) {
            Log.e(TAG, "Gagal tampilkan overlay: " + e.getMessage());
            mShown = false;
        }
    }

    public void hide() {
        if (!mShown) return;
        mShown = false;
        mService.unregisterObserver(mObserver);
        mOverlayList.setAdapter(null);
        mChannelAdapter = null;
        try {
            WindowManager windowManager = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            windowManager.removeView(mOverlayView);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Overlay sudah hilang: " + e.getMessage());
        }
    }

    public void setPushToTalkShown(boolean showPtt) {
        if (mTalkButton != null) {
            mTalkButton.setVisibility(showPtt ? View.VISIBLE : View.GONE);
        }
    }
}
