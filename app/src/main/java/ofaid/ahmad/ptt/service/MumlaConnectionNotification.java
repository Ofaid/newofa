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

package ofaid.ahmad.ptt.service;

import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.app.DrawerAdapter;
import ofaid.ahmad.ptt.app.MumlaActivity;

public class MumlaConnectionNotification {
    private static final String TAG = "OFAID-Notif";
    private static final int NOTIFICATION_ID = 1;
    private static final String BROADCAST_MUTE = "b_mute";
    private static final String BROADCAST_DEAFEN = "b_deafen";
    private static final String BROADCAST_OVERLAY = "b_overlay";

    private Service mService;
    private OnActionListener mListener;
    private String mCustomContentText;
    private boolean mActionsShown;
    private boolean mReceiverRegistered = false; // ✅ Cek sudah terdaftar

    private BroadcastReceiver mNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) return;
            switch (intent.getAction()) {
                case BROADCAST_MUTE:
                    if (mListener != null) mListener.onMuteToggled();
                    break;
                case BROADCAST_DEAFEN:
                    if (mListener != null) mListener.onDeafenToggled();
                    break;
                case BROADCAST_OVERLAY:
                    if (mListener != null) mListener.onOverlayToggled();
                    break;
            }
        }
    };

    public static MumlaConnectionNotification create(Service service, String contentText,
                                                     OnActionListener listener) {
        return new MumlaConnectionNotification(service, contentText, listener);
    }

    private MumlaConnectionNotification(Service service, String contentText,
                                        OnActionListener listener) {
        mService = service;
        mListener = listener;
        mCustomContentText = contentText;
        mActionsShown = false;
    }

    public void setCustomContentText(String text) {
        mCustomContentText = text;
    }

    public void setActionsShown(boolean actionsShown) {
        mActionsShown = actionsShown;
    }

    public void show() {
        createNotification();

        // ✅ TIDAK daftar ulang kalau sudah ada
        if (mReceiverRegistered) {
            Log.d(TAG, "Penerima sudah terdaftar");
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_DEAFEN);
        filter.addAction(BROADCAST_MUTE);
        filter.addAction(BROADCAST_OVERLAY);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mService.registerReceiver(mNotificationReceiver, filter, RECEIVER_NOT_EXPORTED);
            } else {
                mService.registerReceiver(mNotificationReceiver, filter);
            }
            mReceiverRegistered = true; // ✅ Tandai sudah terdaftar
            Log.i(TAG, "✅ Penerima tombol aktif");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Penerima sudah terdaftar: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Gagal daftar penerima: " + e.getMessage());
        }
    }

    public void hide() {
        // ✅ TIDAK lepas kalau belum terdaftar
        if (!mReceiverRegistered) return;

        try {
            mService.unregisterReceiver(mNotificationReceiver);
            mReceiverRegistered = false; // ✅ Tandai sudah dilepas
            Log.i(TAG, "✅ Penerima tombol dinonaktifkan");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Penerima sudah dilepas: " + e.getMessage());
            mReceiverRegistered = false;
        } catch (Exception e) {
            Log.e(TAG, "Gagal lepas penerima: " + e.getMessage());
            mReceiverRegistered = false;
        }

        try {
            mService.stopForeground(true);
        } catch (Exception e) {
            Log.e(TAG, "Gagal sembunyikan notifikasi: " + e.getMessage());
        }
    }

    private Notification createNotification() {
        String channelId = "connected_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelName = mService.getString(R.string.connected);
            NotificationChannel chan = new NotificationChannel(channelId, channelName,
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager =
                    (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(chan);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mService, channelId);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            builder.setContentTitle(mService.getString(R.string.app_name));
        }
        builder.setContentText(mCustomContentText);
        builder.setSmallIcon(R.drawable.ic_stat_notify);
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        builder.setCategory(NotificationCompat.CATEGORY_CALL);
        builder.setShowWhen(false);
        builder.setOngoing(true);

        if (mActionsShown) {
            Intent muteIntent = new Intent(BROADCAST_MUTE);
            muteIntent.setPackage(mService.getPackageName());
            Intent deafenIntent = new Intent(BROADCAST_DEAFEN);
            deafenIntent.setPackage(mService.getPackageName());
            Intent overlayIntent = new Intent(BROADCAST_OVERLAY);
            overlayIntent.setPackage(mService.getPackageName());

            builder.addAction(R.drawable.ic_action_microphone,
                    mService.getString(R.string.mute), PendingIntent.getBroadcast(
                            mService, 1, muteIntent,
                            FLAG_CANCEL_CURRENT | FLAG_IMMUTABLE));
            builder.addAction(R.drawable.ic_action_audio,
                    mService.getString(R.string.deafen), PendingIntent.getBroadcast(
                            mService, 1, deafenIntent,
                            FLAG_CANCEL_CURRENT | FLAG_IMMUTABLE));
            builder.addAction(R.drawable.ic_action_channels,
                    mService.getString(R.string.overlay), PendingIntent.getBroadcast(
                            mService, 2, overlayIntent,
                            FLAG_CANCEL_CURRENT | FLAG_IMMUTABLE));
        }

        Intent channelListIntent = new Intent(mService, MumlaActivity.class);
        channelListIntent.putExtra(MumlaActivity.EXTRA_DRAWER_FRAGMENT, DrawerAdapter.ITEM_SERVER);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                mService, 0, channelListIntent,
                FLAG_CANCEL_CURRENT | FLAG_IMMUTABLE);
        builder.setContentIntent(pendingIntent);

        Notification notification = builder.build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mService.startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            mService.startForeground(NOTIFICATION_ID, notification);
        }

        return notification;
    }

    public interface OnActionListener {
        void onMuteToggled();
        void onDeafenToggled();
        void onOverlayToggled();
    }
}
