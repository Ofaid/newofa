/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * OFAID 2026
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

import se.lublin.humla.model.IChannel;
import se.lublin.humla.model.IUser;
import se.lublin.humla.util.HumlaObserver;
import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.Settings;
import ofaid.ahmad.ptt.channel.ChannelAdapter;

/**
 * An onscreen interactive overlay displaying the users in the current channel.
 * Created by andrew on 26/09/13.
 */
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
            if (mChannelAdapter != null && mService != null &&
                    user.getChannel() != null &&
                    user.getChannel().equals(mService.getSessionChannel())) {
                mChannelAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onUserJoinedChannel(IUser user, IChannel newChannel, IChannel oldChannel) {
            if (mService == null) return;

            int selfSession;
            try {
                selfSession = mService.getSessionId();
            } catch (IllegalStateException e) {
                Log.d(TAG, "exception in onUserJoinedChannel: " + e);
                return;
            }

            if (user.getSession() == selfSession) {
                mChannelAdapter.setChannel(mService.getSessionChannel());
            } else if (newChannel != null && oldChannel != null &&
                    mService.getSessionChannel() != null &&
                    (newChannel.getId() == mService.getSessionChannel().getId() ||
                     oldChannel.getId() == mService.getSessionChannel().getId())) {
                mChannelAdapter.notifyDataSetChanged();
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
        mTalkButton = (ImageView) mOverlayView.findViewById(R.id.overlay_talk);
        mDragButton = (ImageView) mOverlayView.findViewById(R.id.overlay_drag);
        mCloseButton = (ImageView) mOverlayView.findViewById(R.id.overlay_close);
        mTitleView = mOverlayView.findViewById(R.id.overlay_title);
        mOverlayList = (ListView) mOverlayView.findViewById(R.id.overlay_list);

        mTitleView.setOnTouchListener(new View.OnTouchListener() {
            private final WindowManager mWindowManager =
                    (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
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
            private final WindowManager mWindowManager =
                    (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
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
                // ✅ Cek dulu sebelum pakai — hindari FC
                if (mService == null) return false;

                if (MotionEvent.ACTION_DOWN == event.getAction()) {
                    mService.onTalkKeyDown();
                    return true;
                } else if (MotionEvent.ACTION_UP == event.getAction()) {
                    mService.onTalkKeyUp();
                    return true;
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
        // ✅ Cek dulu — hindari FC
        if (mShown || mService == null) return;

        mShown = true;
        mChannelAdapter = new ChannelAdapter(mService, mService.getSessionChannel());
        mOverlayList.setAdapter(mChannelAdapter);
        mService.registerObserver(mObserver);

        WindowManager windowManager =
                (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
        windowManager.addView(mOverlayView, mOverlayParams);
    }

    public void hide() {
        // ✅ Cek dulu — hindari FC
        if (!mShown || mService == null) return;

        mShown = false;
        mService.unregisterObserver(mObserver);
        mOverlayList.setAdapter(null);

        try {
            WindowManager windowManager =
                    (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            windowManager.removeView(mOverlayView);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "View already removed: " + e.getMessage());
        }
    }

    public void setPushToTalkShown(boolean showPtt) {
        if (mTalkButton != null) {
            mTalkButton.setVisibility(showPtt ? View.VISIBLE : View.GONE);
        }
    }
}
