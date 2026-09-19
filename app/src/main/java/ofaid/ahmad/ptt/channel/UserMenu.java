/*
 * Copyright (C) 2015 Andrew Comminos <andrew@comminos.com>
 *Ofaid/Ahmad — Sinkron ID + Sistem Peran
 */
 
package ofaid.ahmad.ptt.channel;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.view.View;  // ← TAMBAHKAN INI!

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
import ofaid.ahmad.ptt.ofa.OfaIdentity;
import ofaid.ahmad.ptt.ofa.OfaRole;
import ofaid.ahmad.ptt.ofa.PilihStatusDialog;
import ofaid.ahmad.ptt.service.MumlaService;
import ofaid.ahmad.ptt.util.ModelUtils;

public class UserMenu implements PermissionsPopupMenu.IOnMenuPrepareListener, PopupMenu.OnMenuItemClickListener {
    private static final String TAG = UserMenu.class.getName();

    private final Context mContext;
    private final IUser mUser;
    private final MumlaService mService;
    private final FragmentManager mFragmentManager;
    private final IUserLocalStateListener mStateListener;
    private OnPeranDiubahListener mPeranListener;

    public UserMenu(Context context, IUser user, MumlaService service,
                    FragmentManager fragmentManager, IUserLocalStateListener stateListener) {
        mContext = context;
        mUser = user;
        mService = service;
        mFragmentManager = fragmentManager;
        mStateListener = stateListener;
    }

    public interface OnPeranDiubahListener {
        void diperbarui();
    }

    public void setOnPeranDiubahListener(OnPeranDiubahListener pendengar) {
        this.mPeranListener = pendengar;
    }

      // =============================================
    // ✅ AMBIL ID — DIRI SENDIRI PAKAI YANG TERKUNCI 100%
    // =============================================
    private String ambilOfaIdDariUser(IUser user) {
        try {
            int sesiUser = user.getSession();
            int sesiSaya = mService.getSessionId();
            
            if (sesiUser == sesiSaya) {
                // ✅ DIRI SENDIRI — LANGSUNG DARI SUMBER TETAP 🔒
                String idPenuh = OfaIdentity.getGlobalOfaId(mContext);
                // Bentuk singkat tampilan
                if (idPenuh != null && idPenuh.length() > 10) {
                    return idPenuh.substring(0, 10);
                }
                return idPenuh;
            } else {
                // ORANG LAIN — dari nomor server
                int uid = user.getUserId();
                return "OFA-" + (Math.abs((uid * 7591 + uid * 31)) % 90000 + 10000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal ambil ID", e);
            return null;
        }
    }


    @Override
    public void onMenuPrepare(Menu menu, int permissions) {
        // === KODE ASLI — TETAP UTUH, TIDAK DIHAPUS 🛡️ ===
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

        // =============================================
        // ✅ DAFTARKAN OTOMATIS KE APLIKASI
        // =============================================
        String ofaIdTarget = ambilOfaIdDariUser(mUser);
        if (ofaIdTarget != null) {
            if (!OfaRole.sudahTerdaftar(mContext, ofaIdTarget)) {
                OfaRole.daftarkanOtomatis(mContext, ofaIdTarget, mUser.getName());
                Log.i("OFA_AUTOREG", "🆕 Terdaftar otomatis: " + mUser.getName());
            }
        }

        // =============================================
        // ✅ OTOMATIS DAFTAR KE SERVER
        // =============================================
        if (mUser.getUserId() < 0 &&
            mUser.getHash() != null && !mUser.getHash().isEmpty() &&
            (perms & ((self ? Permissions.SelfRegister : Permissions.Register) | Permissions.Write)) > 0) {

            if (mService != null && mService.isConnected()) {
                mService.registerUser(mUser.getSession());
                Log.i("OFA_AUTOREG", "📤 Kirim daftar ke server: " + mUser.getName());
            }
        }

        // =============================================
        // ✅ TAMBAH MENU KITA
        // =============================================
        MenuItem itemStatus = menu.findItem(R.id.menu_pilih_status);
        if (itemStatus == null) {
            itemStatus = menu.add(0, R.id.menu_pilih_status, 0, R.string.pilih_status);
        }
        itemStatus.setVisible(self);

        MenuItem itemRegOfa = menu.findItem(R.id.menu_registrasi);
        if (itemRegOfa == null) {
            itemRegOfa = menu.add(0, R.id.menu_registrasi, 1, "Registrasi");
        }
        boolean sudahDaftar = ofaIdTarget != null && OfaRole.sudahTerdaftar(mContext, ofaIdTarget);
        itemRegOfa.setVisible(sudahDaftar);

        MenuItem itemPeran = menu.findItem(R.id.menu_tetapkan_peran);
        if (itemPeran == null) {
            boolean adalahPemilik = false;
            try {
                String idSaya = OfaIdentity.getGlobalOfaId(mContext);
                adalahPemilik = OfaRole.adalahPemilikUtama(idSaya);
            } catch (Exception e) {
                adalahPemilik = false;
            }
            itemPeran = menu.add(0, R.id.menu_tetapkan_peran, 2, "📋 Tetapkan Peran");
            itemPeran.setVisible(adalahPemilik);
        }

        // Highlight asli — tetap aman
        menu.findItem(R.id.context_mute).setChecked(mUser.isMuted() || mUser.isSuppressed());
        menu.findItem(R.id.context_deafen).setChecked(mUser.isDeafened());
        menu.findItem(R.id.context_priority).setChecked(mUser.isPrioritySpeaker());
        menu.findItem(R.id.context_local_mute).setChecked(mUser.isLocalMuted());
        menu.findItem(R.id.context_ignore_messages).setChecked(mUser.isLocalIgnored());
    }

    @Override
    public boolean onMenuItemClick(final MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        
        if (itemId == R.id.menu_pilih_status) {
            int idPengguna = mUser.getSession();
            String namaPengguna = mUser.getName();
            PilihStatusDialog dialog = PilihStatusDialog.buat(idPengguna, namaPengguna);
            dialog.show(mFragmentManager, "PilihStatusDialog");
            return true;
        }

      if (itemId == R.id.menu_registrasi) {
    String ofaId = ambilOfaIdDariUser(mUser);
    new MaterialAlertDialogBuilder(mContext)
        .setTitle("✅ Sudah Terdaftar")
        .setMessage("ID: " + ofaId + "\nNama: " + mUser.getName())
        .setPositiveButton("Oke", null)
        .show();
    return true;
}


        if (itemId == R.id.menu_tetapkan_peran) {
            tampilkanPilihanPeran();
            return true;
        }

        // === KLIK ASLI — TETAP UTUH ===
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

    private void tampilkanPilihanPeran() {
        String idSaya;
        try {
            idSaya = OfaIdentity.getGlobalOfaId(mContext);
        } catch (Exception e) {
            return;
        }
        if (!OfaRole.adalahPemilikUtama(idSaya)) {
            return;
        }

        String ofaId = ambilOfaIdDariUser(mUser);
        if (ofaId == null) return;
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
                    case 0:
                        OfaRole.setPeranUser(mContext, ofaId, OfaRole.ROLE_WARGA, "");
                        break;
                    case 1:
                        OfaRole.setPeranUser(mContext, ofaId, OfaRole.ROLE_LURAH, "");
                        break;
                    case 2:
                        OfaRole.setPeranUser(mContext, ofaId, OfaRole.ROLE_PEMIMPIN_CH, "");
                        break;
                    case 3:
                        OfaRole.hapusPeranUser(mContext, ofaId);
                        break;
                }
                if (mPeranListener != null) {
                    mPeranListener.diperbarui();
                }
            })
            .show();
    }

    private void showUserComment(final boolean edit) {
        Bundle args = new Bundle();
        args.putInt("session", mUser.getSession());
        args.putString("comment", mUser.getComment());
        args.putBoolean("editing", edit);
        UserCommentFragment fragment = (UserCommentFragment) Fragment.instantiate(mContext, UserCommentFragment.class.getName(), args);
        fragment.show(mFragmentManager, "UserCommentFragment");
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

    public interface IUserLocalStateListener {
        void onLocalUserStateUpdated(IUser user);
    }
}
