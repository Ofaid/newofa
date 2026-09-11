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

package ofaid.ahmad.ptt.channel;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import se.lublin.humla.model.IChannel;
import se.lublin.humla.model.IUser;
import se.lublin.humla.model.TalkState;
import se.lublin.humla.model.User;
import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.ofa.OfaLokasi;

/**
 * Simple adapter to display the users in a single channel.
 * Created by andrew on 24/11/13.
 * Tambahan: Lokasi otomatis GPS — OFAID
 */
public class ChannelAdapter extends BaseAdapter {

    private Context mContext;
    private IChannel mChannel;

    // Penampung tampilan — biar rapi & cepat
    static class ViewHolder {
        TextView userName;
        TextView userId;
        TextView userStatus;
        TextView userLokasi;  // ✅ Tambah: Lokasi
        ImageView userState;
    }

    public ChannelAdapter(Context context, IChannel channel) {
        mContext = context;
        mChannel = channel;
    }

    @Override
    public int getCount() {
        return mChannel.getUsers().size();
    }

    @Override
    public Object getItem(int position) {
        return mChannel.getUsers().get(position);
    }

    @Override
    public long getItemId(int position) {
        IUser user = mChannel.getUsers().get(position);
        if (user != null)
            return user.getUserId();
        return -1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        ViewHolder holder;

        if (v == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(mContext);
            v = layoutInflater.inflate(R.layout.channel_user_row, parent, false); // ✅ Pakai layout yang benar

            // Simpan referensi sekali — tidak cari ulang tiap tampil
            holder = new ViewHolder();
            holder.userName = v.findViewById(R.id.user_row_name);
            holder.userId = v.findViewById(R.id.user_row_id);
            holder.userStatus = v.findViewById(R.id.user_row_status);
            holder.userLokasi = v.findViewById(R.id.user_lokasi); // ✅ Lokasi
            holder.userState = v.findViewById(R.id.user_row_state);

            v.setTag(holder);
        } else {
            holder = (ViewHolder) v.getTag();
        }

        User user = (User) getItem(position);

        // === NAMA USER ===
        holder.userName.setText(user.getName());

        // === ID UNIK ===
        if (holder.userId != null) {
            holder.userId.setText("OFA-" + Integer.toHexString(user.getUserId()).toUpperCase());
        }

        // === STATUS ===
        if (holder.userStatus != null) {
            String status = user.getComment();
            if (status == null || status.trim().isEmpty()) {
                status = "Siap / Tersedia";
            }
            holder.userStatus.setText(status);
        }

        // === ✅ LOKASI OTOMATIS DARI GPS ===
        if (holder.userLokasi != null) {
            String lokasi = OfaLokasi.formatLokasiTampil(mContext);
            holder.userLokasi.setText(lokasi);
            holder.userLokasi.setVisibility(View.VISIBLE);
        }

        // === IKON BICARA/DIAM — TETAP SAMA PERSIS ===
        if (user.isSelfDeafened())
            holder.userState.setImageResource(R.drawable.outline_circle_deafened);
        else if (user.isSelfMuted())
            holder.userState.setImageResource(R.drawable.outline_circle_muted);
        else if (user.isDeafened())
            holder.userState.setImageResource(R.drawable.outline_circle_server_deafened);
        else if (user.isMuted())
            holder.userState.setImageResource(R.drawable.outline_circle_server_muted);
        else if (user.isSuppressed())
            holder.userState.setImageResource(R.drawable.outline_circle_suppressed);
        else
        if (user.getTalkState() == TalkState.TALKING)
            holder.userState.setImageResource(R.drawable.outline_circle_talking_on);
        else
            holder.userState.setImageResource(R.drawable.outline_circle_talking_off);

        return v;
    }

    public void setChannel(IChannel channel) {
        mChannel = channel;
        notifyDataSetChanged();
    }

    public IChannel getChannel() {
        return mChannel;
    }
}
