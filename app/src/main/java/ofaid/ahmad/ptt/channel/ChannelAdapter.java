/*
 * Copyright (C) 2014 Andrew Comminos
 * Modif By Ofaid/Ahmad 12-9-2026
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

public class ChannelAdapter extends BaseAdapter {

    private Context mContext;
    private IChannel mChannel;
    private String lokasiSaya = null; // ✅ Simpan lokasi dari luar

    static class ViewHolder {
        TextView userName;
        TextView userId;
        TextView userStatus;
        TextView userLokasi;
        ImageView userState;
    }

    public ChannelAdapter(Context context, IChannel channel) {
        mContext = context;
        mChannel = channel;
    }

    // ✅ Terima lokasi dari luar & perbarui tampilan
    public void setLokasiTeks(String teksLokasi) {
        this.lokasiSaya = teksLokasi;
        notifyDataSetChanged();
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
            LayoutInflater inflater = LayoutInflater.from(mContext);
            v = inflater.inflate(R.layout.channel_user_row, parent, false);

            holder = new ViewHolder();
            holder.userName = v.findViewById(R.id.user_row_name);
            holder.userId = v.findViewById(R.id.user_row_id);
            holder.userStatus = v.findViewById(R.id.user_row_status);
            holder.userLokasi = v.findViewById(R.id.user_lokasi);
            holder.userState = v.findViewById(R.id.user_row_state);

            v.setTag(holder);
        } else {
            holder = (ViewHolder) v.getTag();
        }

        User user = (User) getItem(position);

        // === NAMA ===
        holder.userName.setText(user.getName());

        // === ID OFA ===
        if (holder.userId != null) {
            holder.userId.setText("OFA-" + Integer.toHexString(user.getUserId()).toUpperCase());
        }

        // === STATUS ===
        if (holder.userStatus != null) {
            String status = user.getComment();
            if (status == null || status.trim().isEmpty()) {
                status = "🟢 Siap / Tersedia";
            }
            holder.userStatus.setText(status);
        }

        // === LOKASI ===
        if (holder.userLokasi != null) {
            String teksTampil = lokasiSaya;
            if (teksTampil == null) {
                teksTampil = OfaLokasi.formatLokasiTampil(mContext);
            }
            if (teksTampil != null && !teksTampil.trim().isEmpty()) {
                holder.userLokasi.setText(teksTampil);
                holder.userLokasi.setVisibility(View.VISIBLE);
            } else {
                holder.userLokasi.setVisibility(View.GONE);
            }
        }

        // === IKON BICARA/DIAM ===
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
        else if (user.getTalkState() == TalkState.TALKING)
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
