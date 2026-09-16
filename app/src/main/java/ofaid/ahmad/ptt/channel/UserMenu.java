/*
 * Copyright (C) 2015 Andrew Comminos <andrew@comminos.com>
 *Ofaid/Ahmad — Sistem Peran & Label
 */
 
package ofaid.ahmad.ptt.channel;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import se.lublin.humla.model.IChannel;
import se.lublin.humla.model.IUser;
import se.lublin.humla.net.Permissions;
import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.channel.comment.UserCommentFragment;
// ✅ IMPOR Fitur OFA — TAMBAHAN SAJA, TIDAK UBAH YANG LAIN
import ofaid.ahmad.ptt.ofa.OfaIdentity;
import ofaid.ahmad.ptt.ofa.OfaUserStatus;
import ofaid.ahmad.ptt.ofa.OfaRole;
import ofaid.ahmad.ptt.ofa.PilihStatusDialog;
import ofaid.ahmad.ptt.service.MumlaService;
import ofaid.ahmad.ptt.util.ModelUtils;

/**
 * Created by andrew on 19/11/15.
 * OFA: Ditambahkan fitur Status Pengguna & Peran — terpisah, tidak ganggu fungsi asli
 */
public class UserMenu implements PermissionsPopupMenu.IOnMenuPrepareListener, PopupMenu.OnMenuItemClickListener {
    private static final String TAG = UserMenu.class.getName();

    private final Context mContext;
    private final IUser mUser;
    private final MumlaService mService;
    private final FragmentManager mFragmentManager;
    private final IUserLocalStateListener mStateListener;
    private OnPeranDiubahListener mPeranListener; // ✅ Pembaruan tampilan peran

    public UserMenu(Context context, IUser user, MumlaService service,
                    FragmentManager fragmentManager, IUserLocalStateListener stateListener) {
        mContext = context;
        mUser = user;
        mService = service;
        mFragmentManager = fragmentManager;
        mStateListener = stateListener;
    }

    // ✅ Antarmuka pembaruan peran
    public interface OnPeranDiubahListener {
        void diperbarui();
    }

    public void setOnPeranDiubahListener(OnPeranDiubahListener pendengar) {
        this.mPeranListener = pendengar;
    }

    @Override
    public void onMenuPrepare(Menu menu, int permissions) {
        // === KODE ASLI — TETAP UTUH, TIDAK DIUBAH SATU BARIS PUN 🛡️ ===
        boolean self;
        try {
            self = mUser.getSession() == mService.getSessionId();
        } catch (IllegalStateException e) {
            Log.d(TAG, "exception in onMenuPrepare: " + e);
            return;
        }
        int perms = mService.getPermissions();
        IChannel channel = mUser.getChannel();
        if (channel == null) {
            Log.d(TAG, "mUser.getChannel()==null in onMenuPrepare");
            return;
        }
        int channelPerms = channel.getId() != 0 ? channel.getPermissions() : perms;

        menu.findItem(R.id.context_kick).setVisible(
                !self && (perms & (Permissions.Kick | Permissions.Ban | Permissions.Write)) > 0);
        menu.findItem(R.id.context_ban).setVisible(
                !self && (perms & (Permissions.Ban | Permissions.Write)) > 0);
        menu.findItem(R.id.context_mute).setVisible(
                ((channelPerms & (Permissions.Write | Permissions.MuteDeafen)) > 0 &&
                        (!self || mUser.isMuted() || mUser.isSuppressed())));
        menu.findItem(R.id.context_deafen).setVisible(
                ((channelPerms & (Permissions.Write | Permissions.MuteDeafen)) > 0 &&
                        (!self || mUser.isDeafened())));
        menu.findItem(R.id.context_priority).setVisible(
                ((channelPerms & (Permissions.Write | Permissions.MuteDeafen)) > 0));
        menu.findItem(R.id.context_move).setVisible(
                !self && (perms & Permissions.Move) > 0);
        menu.findItem(R.id.context_change_comment).setVisible(self);
        menu.findItem(R.id.context_reset_comment).setVisible(
                !self && ((mUser.getComment() != null && !mUser.getComment().isEmpty()) ||
                        (mUser.getCommentHash() != null)) &&
                        (perms & (Permissions.Move | Permissions.Write)) > 0);
        menu.findItem(R.id.context_view_comment).setVisible(
                (mUser.getComment() != null && !mUser.getComment().isEmpty()) ||
                        (mUser.getCommentHash() != null));
        menu.findItem(R.id.context_register).setVisible(mUser.getUserId() < 0 &&
                (mUser.getHash() != null && !mUser.getHash().isEmpty()) &&
                (perms & ((self ? Permissions.SelfRegister : Permissions.Register) | Permissions.Write)) > 0);
        menu.findItem(R.id.context_local_mute).setVisible(!self);
        menu.findItem(R.id.context_ignore_messages).setVisible(!self);

        // ✅ === TAMBAH TOMBOL PILIH STATUS — HANYA UNTUK DIRI SENDIRI ===
        MenuItem itemPilihStatus = menu.add(0, R.id.menu_pilih_status, 0, R.string.pilih_status);
        itemPilihStatus.setVisible(self);

        // ✅ === TAMBAH TETAPKAN PERAN — HANYA PEMILIK UTAMA ===
        boolean adalahPemilik = false;
        try {
            String idSaya = OfaIdentity.getGlobalOfaId(mContext);
            adalahPemilik = OfaRole.adalahPemilikUtama(idSaya);
        } catch (Exception e) {
            adalahPemilik = false;
        }
        MenuItem itemTetapkanPeran = menu.add(0, R.id.menu_tetapkan_peran, 1, "📋 Tetapkan Peran");
        itemTetapkanPeran.setVisible(self && adalahPemilik); // 🔒 HANYA KAMU YANG LIHAT!

        // Highlight toggles — tetap asli, tidak diubah
        menu.findItem(R.id.context_mute).setChecked(mUser.isMuted() || mUser.isSuppressed());
        menu.findItem(R.id.context_deafen).setChecked(mUser.isDeafened());
        menu.findItem(R.id.context_priority).setChecked(mUser.isPrioritySpeaker());
        menu.findItem(R.id.context_local_mute).setChecked(mUser.isLocalMuted());
        menu.findItem(R.id.context_ignore_messages).setChecked(mUser.isLocalIgnored());
    }

    @Override
    public boolean onMenuItemClick(final MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        
        // ✅ === PILIH STATUS ===
        if (itemId == R.id.menu_pilih_status) {
            int idPengguna = mUser.getSession();
            String namaPengguna = mUser.getName();
            PilihStatusDialog dialog = PilihStatusDialog.buat(idPengguna, namaPengguna);
            dialog.show(mFragmentManager, "PilihStatusDialog");
            return true;
        }

        // ✅ === TETAPKAN PERAN ===
        if (itemId == R.id.menu_tetapkan_peran) {
            tampilkanPilihanPeran();
            return true;
        }

        // === SEMUA KODE ASLI — TETAP BERJALAN PERSIS SEPERTI SEMULA! 🛡️ TIDAK DIUBAH SATU BARIS PUN ===
        if (itemId == R.id.context_ban || itemId == R.id.context_kick) {
            final EditText reasonField = new EditText(mContext);
            reasonField.setHint(R.string.hint_reason);
            new MaterialAlertDialogBuilder(mContext)
                    .setTitle(R.string.user_menu_kick)
                    .setView(reasonField)
                    .setPositiveButton(R.string.user_menu_kick, (dialog, which) ->
                            mService.kickBanUser(mUser.getSession(), reasonField.getText().toString(), menuItem.getItemId() == R.id.context_ban))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else if (itemId == R.id.context_mute) {
            mService.setMuteDeafState(mUser.getSession(), !(mUser.isMuted() || mUser.isSuppressed()), mUser.isDeafened());
        } else if (itemId == R.id.context_deafen) {
            mService.setMuteDeafState(mUser.getSession(), mUser.isMuted(), !mUser.isDeafened());
        } else if (itemId == R.id.context_move) {
            showChannelMoveDialog();
        } else if (itemId == R.id.context_priority) {
            mService.setPrioritySpeaker(mUser.getSession(), !mUser.isPrioritySpeaker());
        } else if (itemId == R.id.context_local_mute) {
            mUser.setLocalMuted(!mUser.isLocalMuted());
            mStateListener.onLocalUserStateUpdated(mUser);
        } else if (itemId == R.id.context_ignore_messages) {
            mUser.setLocalIgnored(!mUser.isLocalIgnored());
            mStateListener.onLocalUserStateUpdated(mUser);
        } else if (itemId == R.id.context_change_comment) {
            showUserComment(true);
        } else if (itemId == R.id.context_view_comment) {
            showUserComment(false);
        } else if (itemId == R.id.context_reset_comment) {
            new MaterialAlertDialogBuilder(mContext)
                    .setMessage(mContext.getString(R.string.confirm_reset_comment, mUser.getName()))
                    .setPositiveButton(R.string.confirm, (dialog, which) ->
                            mService.setUserComment(mUser.getSession(), ""))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else if (itemId == R.id.context_register) {
            mService.registerUser(mUser.getSession());
        } else {
            return false;
        }
        return true;
    }

    // ✅ === PILIHAN PERAN BARU ===
    private void tampilkanPilihanPeran() {
        // 🔒 CEK: HANYA PEMILIK UTAMA YANG BISA BUKA INI
        String idSaya = OfaIdentity.getGlobalOfaId(mContext);
        if (!OfaRole.adalahPemilikUtama(idSaya)) {
            return; // Orang lain langsung ditutup, tidak tampil sama sekali!
        }

        int uid = mUser.getUserId();
        final String ofaId = "OFA-" + (Math.abs((uid * 7591 + uid * 31)) % 90000 + 10000);
        final String namaUser = mUser.getName();

        final String[] pilihan = {
            "💚 Tetapkan Sebagai Warga",
            "🏡 Tetapkan Sebagai Lurah",
            "👑 Tetapkan Sebagai Pemimpin CH",
            "❌ Hapus Peran"
        };

        new MaterialAlertDialogBuilder(mContext)
            .setTitle("Atur Peran — " + namaUser)
            .setItems(pilihan, (dialog, which) -> {
                switch (which) {
                    case 0: // Warga
                        OfaRole.setPeranUser(mContext, ofaId, OfaRole.ROLE_WARGA, "");
                        break;
                    case 1: // Lurah
                        OfaRole.setPeranUser(mContext, ofaId, OfaRole.ROLE_LURAH, "");
                        break;
                    case 2: // Pemimpin CH
                        OfaRole.setPeranUser(mContext, ofaId, OfaRole.ROLE_PEMIMPIN_CH, "");
                        break;
                    case 3: // Hapus Peran
                        OfaRole.hapusPeranUser(mContext, ofaId);
                        break;
                }
                // Segarkan tampilan langsung
                if (mPeranListener != null) {
                    mPeranListener.diperbarui();
                }
            })
            .show();
    }

    // === SEMUA METODE ASLI — TETAP UTUH, TIDAK DIUBAH! 🛡️ ===
    private void showUserComment(final boolean edit) {
        Bundle args = new Bundle();
        args.putInt("session", mUser.getSession());
        args.putString("comment", mUser.getComment());
        args.putBoolean("editing", edit);
        UserCommentFragment fragment = (UserCommentFragment) Fragment.instantiate(mContext, UserCommentFragment.class.getName(), args);
        fragment.show(mFragmentManager, UserCommentFragment.class.getName());
    }

    private void showChannelMoveDialog() {
        final List<IChannel> channels = ModelUtils.getChannelList(mService.getRootChannel());
        final CharSequence[] channelNames = new CharSequence[channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            channelNames[i] = channels.get(i).getName();
        }
        new MaterialAlertDialogBuilder(mContext)
                .setTitle(R.string.user_menu_move)
                .setItems(channelNames, (dialog, which) -> {
                    IChannel channel = channels.get(which);
                    mService.moveUserToChannel(mUser.getSession(), channel.getId());
                })
                .show();
    }

    public void showPopup(View anchor) {
        PermissionsPopupMenu popupMenu = new PermissionsPopupMenu(mContext, anchor,
                R.menu.context_user, this, this, mUser.getChannel(), mService);
        popupMenu.show();
    }

    /**
     * A listener notified whenever the user's local state changes.
     */
    public interface IUserLocalStateListener {
        void onLocalUserStateUpdated(IUser user);
    }
}
