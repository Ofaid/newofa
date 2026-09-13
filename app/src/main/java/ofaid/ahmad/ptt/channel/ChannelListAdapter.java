/*Edit By Ofaid/Ahmd-jr 9-9-2026*/
/* Copyright (C) 2014 Andrew Comminos */

package ofaid.ahmad.ptt.channel;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import se.lublin.humla.HumlaService;
import se.lublin.humla.IHumlaService;
import se.lublin.humla.IHumlaSession;
import se.lublin.humla.model.IChannel;
import se.lublin.humla.model.IUser;
import se.lublin.humla.model.Server;
import se.lublin.humla.model.TalkState;
import se.lublin.humla.util.HumlaDisconnectedException;
import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.db.MumlaDatabase;
import ofaid.ahmad.ptt.drawable.CircleDrawable;
import ofaid.ahmad.ptt.service.MumlaService;
import ofaid.ahmad.ptt.ofa.OfaUserStatus;
import ofaid.ahmad.ptt.ofa.OfaLokasi;

public class ChannelListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements UserMenu.IUserLocalStateListener {
    private static final String TAG = ChannelListAdapter.class.getName();

    public static final long CHANNEL_ID_MASK = (0x1L << 32);
    public static final long USER_ID_MASK = (0x1L << 33);

    private Context mContext;
    private IHumlaService mService;
    private MumlaDatabase mDatabase;
    private List<Integer> mRootChannels;
    private List<Node> mNodes;
    private HashMap<Integer, Boolean> mExpandedChannels;
    private OnUserClickListener mUserClickListener;
    private OnChannelClickListener mChannelClickListener;
    private boolean mShowChannelUserCount;
    private final FragmentManager mFragmentManager;
    private RecyclerView mAttachedRecyclerView;

    public ChannelListAdapter(Context context, IHumlaService service, MumlaDatabase database,
                              FragmentManager fragmentManager, boolean showPinnedOnly,
                              boolean showChannelUserCount) throws RemoteException {
        setHasStableIds(true);
        mContext = context;
        mService = service;
        mDatabase = database;
        mFragmentManager = fragmentManager;
        mShowChannelUserCount = showChannelUserCount;

        mRootChannels = new ArrayList<Integer>();
        if(showPinnedOnly) {
            mRootChannels = mDatabase.getPinnedChannels(mService.getTargetServer().getId());
        } else {
            mRootChannels.add(0);
        }

        mNodes = new LinkedList<Node>();
        mExpandedChannels = new HashMap<Integer, Boolean>();
        updateChannels();
    }

    public void attachRecyclerView(RecyclerView view) {
        mAttachedRecyclerView = view;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        LayoutInflater inflater = (LayoutInflater)
                mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(viewType, viewGroup, false);
        if (viewType == R.layout.channel_row) {
            return new ChannelViewHolder(view);
        } else if (viewType == R.layout.channel_user_row) {
            return new UserViewHolder(view);
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        final Node node = mNodes.get(position);
        if (node.isChannel()) {
            final IChannel channel = node.getChannel();
            final ChannelViewHolder cvh = (ChannelViewHolder) viewHolder;
            cvh.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mChannelClickListener != null) {
                        mChannelClickListener.onChannelClick(channel);
                    }
                }
            });

            final boolean expandUsable = channel.getSubchannels().size() > 0 ||
                    channel.getSubchannelUserCount() > 0;
            cvh.mChannelExpandToggle.setImageResource(node.isExpanded() ?
                    R.drawable.ic_action_expanded : R.drawable.ic_action_collapsed);
            cvh.mChannelExpandToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mExpandedChannels.put(channel.getId(), !node.isExpanded());
                    updateChannels();
                    notifyDataSetChanged();
                }
            });
            cvh.mChannelExpandToggle.setEnabled(expandUsable);
            cvh.mChannelExpandToggle.setVisibility(expandUsable ? View.VISIBLE : View.INVISIBLE);
            cvh.mChannelName.setText(channel.getName());

            int nameTypeface = Typeface.NORMAL;
            if (mService != null && mService.isConnected()) {
                IHumlaSession session = mService.HumlaSession();
                IChannel ourChan = null;
                try {
                    ourChan = session.getSessionChannel();
                } catch(IllegalStateException e) {
                    Log.d(TAG, "exception in onBindViewHolder: " + e);
                }
                if (ourChan != null) {
                    if (channel.equals(ourChan)) {
                        nameTypeface |= Typeface.BOLD;
                        if (channel.getLinks().size() > 0) {
                            nameTypeface |= Typeface.ITALIC;
                        }
                    }
                    if (channel.getLinks().contains(ourChan)) {
                        nameTypeface |= Typeface.ITALIC;
                    }
                }
            }
            cvh.mChannelName.setTypeface(null, nameTypeface);

            if (mShowChannelUserCount) {
                cvh.mChannelUserCount.setVisibility(View.VISIBLE);
                int userCount = channel.getSubchannelUserCount();
                cvh.mChannelUserCount.setText(String.format("%d", userCount));
            } else {
                cvh.mChannelUserCount.setVisibility(View.GONE);
            }

            DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
            float margin = node.getDepth() * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25, metrics);
            cvh.mChannelHolder.setPadding((int) margin,
                    cvh.mChannelHolder.getPaddingTop(),
                    cvh.mChannelHolder.getPaddingRight(),
                    cvh.mChannelHolder.getPaddingBottom());

            cvh.mJoinButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mService != null && mService.isConnected()) {
                        mService.HumlaSession().joinChannel(channel.getId());
                    }
                }
            });

            cvh.mMoreButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ChannelMenu menu = new ChannelMenu(mContext, channel, mService, mDatabase, mFragmentManager);
                    menu.showPopup(v);
                }
            });

            cvh.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    cvh.mMoreButton.performClick();
                    return true;
                }
            });
        } else if (node.isUser()) {
            final IUser user = node.getUser();
            final UserViewHolder uvh = (UserViewHolder) viewHolder;
            uvh.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mUserClickListener != null) {
                        mUserClickListener.onUserClick(user);
                    }
                }
            });

            // =============================================
            // ✅ TAMPILKAN NAMA — DIPASTIKAN TERLIHAT!
            // =============================================
            if (uvh.mUserName != null) {
                uvh.mUserName.setText(user.getName());
                uvh.mUserName.setVisibility(View.VISIBLE);
                uvh.mUserName.setTextColor(Color.BLACK);
            }

            // =============================================
            // ✅ TAMPILKAN ID PENGGUNA — DIKEMBALIKAN!
            // =============================================
            if (uvh.mUserIdView != null) {
                int uid = user.getUserId();
                // Buat kode tampilan OFA
                int gabungan = Math.abs((uid * 7591 + uid * 31)) % 90000 + 10000;
                uvh.mUserIdView.setText("OFA-" + gabungan);
                uvh.mUserIdView.setVisibility(View.VISIBLE);
                uvh.mUserIdView.setTextColor(0xFF607D8B); // warna abu-biru lembut
            }

            // =============================================
            // ✅ TAMPILKAN STATUS
            // =============================================
            if (uvh.mUserStatusView != null) {
                String status = OfaUserStatus.dapatStatus(mContext, user.getSession());
                uvh.mUserStatusView.setText(status);
                uvh.mUserStatusView.setVisibility(View.VISIBLE);
                if (status.contains("Sibuk") || status.contains("Jangan")) {
                    uvh.mUserStatusView.setTextColor(0xFFFF5252);
                } else if (status.contains("Next") || status.contains("Fitur Disini")) {
                    uvh.mUserStatusView.setTextColor(0xFF4CAF50);
                } else {
                    uvh.mUserStatusView.setTextColor(0xFFBBBBBB);
                }
            }

            // =============================================
            // ✅ TAMPILKAN LOKASI GPS
            // =============================================
            if (uvh.mUserLokasi != null) {
                String keteranganPengguna = user.getComment();
                String lokasiTampil = null;

                if (keteranganPengguna != null && !keteranganPengguna.trim().isEmpty()) {
                    String[] baris = keteranganPengguna.split("\\r?\\n");
                    for (String b : baris) {
                        if (b.trim().startsWith("📍")) {
                            lokasiTampil = b.trim();
                            break;
                        }
                    }
                }

                if (lokasiTampil != null) {
                    uvh.mUserLokasi.setText(lokasiTampil);
                    uvh.mUserLokasi.setVisibility(View.VISIBLE);
                } else {
                    uvh.mUserLokasi.setVisibility(View.GONE);
                }
            }

            final int typefaceStyle;
            int selfSession = -1;
            try {
                if (mService != null) {
                    selfSession = mService.HumlaSession().getSessionId();
                }
            } catch (HumlaDisconnectedException|IllegalStateException e) {
                Log.d(TAG, "exception in onBindViewHolder: " + e);
            }

            if (mService != null && mService.isConnected() && user.getSession() == selfSession) {
                typefaceStyle = Typeface.BOLD;
            } else {
                typefaceStyle = Typeface.NORMAL;
            }
            if (uvh.mUserName != null) {
                uvh.mUserName.setTypeface(null, typefaceStyle);
            }
            uvh.mUserTalkHighlight.setImageDrawable(getTalkStateDrawable(user));

            DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
            float margin = (node.getDepth() + 1) * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25, metrics);
            uvh.mUserHolder.setPadding((int) margin,
                    uvh.mUserHolder.getPaddingTop(),
                    uvh.mUserHolder.getPaddingRight(),
                    uvh.mUserHolder.getPaddingBottom());

            uvh.mMoreButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UserMenu menu = new UserMenu(mContext, user, (MumlaService) mService,
                            mFragmentManager, ChannelListAdapter.this);
                    menu.showPopup(v);
                }
            });

            uvh.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    uvh.mMoreButton.performClick();
                    return true;
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return mNodes.size();
    }

    @Override
    public int getItemViewType(int position) {
        Node node = mNodes.get(position);
        if (node.isChannel()) {
            return R.layout.channel_row;
        } else if (node.isUser()) {
            return R.layout.channel_user_row;
        }
        return 0;
    }

    @Override
    public long getItemId(int position) {
        try {
            return mNodes.get(position).getId();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void updateChannels() {
        if (mService == null || !mService.isConnected()) return;
        IHumlaSession session = mService.HumlaSession();
        mNodes.clear();
        try {
            for (int cid : mRootChannels) {
                IChannel channel = session.getChannel(cid);
                if (channel != null) constructNodes(null, channel, 0, mNodes);
            }
        } catch (IllegalStateException e) {
            Log.d(TAG, "exception in updateChannels: " + e);
        }
    }

    public void updateUserStates(IUser user, RecyclerView view) {
        long itemId = user.getSession() | USER_ID_MASK;
        UserViewHolder uvh = (UserViewHolder) view.findViewHolderForItemId(itemId);
        if (uvh != null) {
            Drawable newState = getTalkStateDrawable(user);
            ConstantState state = uvh.mUserTalkHighlight.getDrawable().getCurrent().getConstantState();
            if (state != null && !state.equals(newState.getConstantState())) {
                uvh.mUserTalkHighlight.setImageDrawable(newState);
            }
        }
    }

    private Drawable getTalkStateDrawable(IUser user) {
        Resources res = mContext.getResources();
        if (user.isSelfDeafened()) return res.getDrawable(R.drawable.outline_circle_deafened);
        if (user.isDeafened()) return res.getDrawable(R.drawable.outline_circle_server_deafened);
        if (user.isSelfMuted()) return res.getDrawable(R.drawable.outline_circle_muted);
        if (user.isMuted()) return res.getDrawable(R.drawable.outline_circle_server_muted);
        if (user.isSuppressed()) return res.getDrawable(R.drawable.outline_circle_suppressed);
        if (user.getTalkState() == TalkState.TALKING
                || user.getTalkState() == TalkState.SHOUTING
                || user.getTalkState() == TalkState.WHISPERING) {
            return res.getDrawable(R.drawable.outline_circle_talking_on);
        }
        if (user.getTexture() != null) {
            Bitmap bmp = BitmapFactory.decodeByteArray(user.getTexture(), 0, user.getTexture().length);
            if (bmp != null) return new CircleDrawable(mContext.getResources(), bmp);
        }
        return res.getDrawable(R.drawable.outline_circle_talking_off);
    }

    public int getUserPosition(int session) {
        long itemId = session | USER_ID_MASK;
        for (int i = 0; i < mNodes.size(); i++) {
            try {
                if (mNodes.get(i).getId() == itemId) return i;
            } catch (RemoteException e) { e.printStackTrace(); }
        }
        return -1;
    }

    public int getUserPositionBySession(int sessionId) {
        if (sessionId <= 0) return -1;
        long targetItemId = USER_ID_MASK | sessionId;
        for (int i = 0; i < mNodes.size(); i++) {
            Node node = mNodes.get(i);
            if (node.isUser()) {
                try {
                    if (node.getId() == targetItemId) return i;
                } catch (RemoteException e) { e.printStackTrace(); }
            }
        }
        return -1;
    }

    public void refreshUserStatus(int sessionId) {
        if (sessionId <= 0) return;
        long targetItemId = USER_ID_MASK | sessionId;
        for (int i = 0; i < mNodes.size(); i++) {
            Node node = mNodes.get(i);
            if (node.isUser()) {
                try {
                    if (node.getId() == targetItemId) {
                        if (mAttachedRecyclerView != null) {
                            RecyclerView.ViewHolder holder =
                                    mAttachedRecyclerView.findViewHolderForAdapterPosition(i);
                            if (holder instanceof UserViewHolder) {
                                UserViewHolder uvh = (UserViewHolder) holder;
                                if (uvh.mUserStatusView != null) {
                                    String status = OfaUserStatus.dapatStatus(mContext, sessionId);
                                    uvh.mUserStatusView.setText(status);
                                    if (status.contains("Sibuk") || status.contains("Jangan")) {
                                        uvh.mUserStatusView.setTextColor(0xFFFF5252);
                                    } else if (status.contains("Siap") || status.contains("Tersedia")) {
                                        uvh.mUserStatusView.setTextColor(0xFF4CAF50);
                                    } else {
                                        uvh.mUserStatusView.setTextColor(0xFFBBBBBB);
                                    }
                                }
                                // ✅ Segarkan ID juga
                                if (uvh.mUserIdView != null) {
                                    // ID tetap, tidak perlu diubah
                                }
                            }
                        }
                        return;
                    }
                } catch (RemoteException e) { e.printStackTrace(); }
            }
        }
    }

    public int getChannelPosition(int channelId) {
        long itemId = channelId | CHANNEL_ID_MASK;
        for (int i = 0; i < mNodes.size(); i++) {
            try {
                if (mNodes.get(i).getId() == itemId) return i;
            } catch (RemoteException e) { e.printStackTrace(); }
        }
        return -1;
    }

    public void setOnUserClickListener(OnUserClickListener listener) {
        mUserClickListener = listener;
    }

    public void setOnChannelClickListener(OnChannelClickListener listener) {
        mChannelClickListener = listener;
    }

    public void setShowChannelUserCount(boolean showUserCount) {
        mShowChannelUserCount = showUserCount;
        notifyDataSetChanged();
    }

    private void constructNodes(Node parent, IChannel channel, int depth, List<Node> nodes) {
        Node channelNode = new Node(parent, depth, channel);
        nodes.add(channelNode);
        Boolean expandSetting = mExpandedChannels.get(channel.getId());
        if ((expandSetting == null && channel.getSubchannelUserCount() == 0)
                || (expandSetting != null && !expandSetting)) {
            channelNode.setExpanded(false);
            return;
        }
        for (IUser user : channel.getUsers()) {
            if (user != null) nodes.add(new Node(channelNode, depth, user));
        }
        for (IChannel subc : channel.getSubchannels()) {
            constructNodes(channelNode, subc, depth + 1, nodes);
        }
    }

    public void setService(IHumlaService service) {
        mService = service;
        if (service.getConnectionState() == HumlaService.ConnectionState.CONNECTED) {
            updateChannels();
            notifyDataSetChanged();
        }
    }

    @Override
    public void onLocalUserStateUpdated(final IUser user) {
        notifyDataSetChanged();
        final Server server = mService.getTargetServer();
        if (user.getUserId() >= 0 && server.isSaved()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (user.isLocalMuted()) {
                        mDatabase.addLocalMutedUser(server.getId(), user.getUserId());
                    } else {
                        mDatabase.removeLocalMutedUser(server.getId(), user.getUserId());
                    }
                    if (user.isLocalIgnored()) {
                        mDatabase.addLocalIgnoredUser(server.getId(), user.getUserId());
                    } else {
                        mDatabase.removeLocalIgnoredUser(server.getId(), user.getUserId());
                    }
                }
            }).start();
        }
    }

    private static class UserViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout mUserHolder;
        public TextView mUserName;
        public ImageView mUserTalkHighlight;
        public ImageView mMoreButton;
        public TextView mUserIdView;
        public TextView mUserStatusView;
        public TextView mUserLokasi;

        public UserViewHolder(View itemView) {
            super(itemView);
            mUserHolder = (LinearLayout) itemView.findViewById(R.id.user_row_title);
            mUserTalkHighlight = (ImageView) itemView.findViewById(R.id.user_row_talk_highlight);
            mUserName = (TextView) itemView.findViewById(R.id.user_row_name);
            mMoreButton = (ImageView) itemView.findViewById(R.id.user_row_more);
            mUserIdView = (TextView) itemView.findViewById(R.id.user_row_id);
            mUserStatusView = (TextView) itemView.findViewById(R.id.user_row_status);
            mUserLokasi = (TextView) itemView.findViewById(R.id.user_lokasi);
        }
    }

    private static class ChannelViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout mChannelHolder;
        public ImageView mChannelExpandToggle;
        public TextView mChannelName;
        public TextView mChannelUserCount;
        public ImageView mJoinButton;
        public ImageView mMoreButton;

        public ChannelViewHolder(View itemView) {
            super(itemView);
            mChannelHolder = (LinearLayout) itemView.findViewById(R.id.channel_row_title);
            mChannelExpandToggle = (ImageView) itemView.findViewById(R.id.channel_row_expand);
            mChannelName = (TextView) itemView.findViewById(R.id.channel_row_name);
            mChannelUserCount = (TextView) itemView.findViewById(R.id.channel_row_count);
            mJoinButton = (ImageView) itemView.findViewById(R.id.channel_row_join);
            mMoreButton = (ImageView) itemView.findViewById(R.id.channel_row_more);
        }
    }

    private static class Node {
        private Node mParent;
        private IChannel mChannel;
        private IUser mUser;
        private int mDepth;
        private boolean mExpanded;

        public Node(Node parent, int depth, IChannel channel) {
            mParent = parent;
            mChannel = channel;
            mDepth = depth;
            mExpanded = true;
        }

        public Node(Node parent, int depth, IUser user) {
            mParent = parent;
            mUser = user;
            mDepth = depth;
        }

        public boolean isChannel() { return mChannel != null; }
        public boolean isUser() { return mUser != null; }
        public Node getParent() { return mParent; }
        public IChannel getChannel() { return mChannel; }
        public IUser getUser() { return mUser; }

        public Long getId() throws RemoteException {
            if (isChannel()) return CHANNEL_ID_MASK | mChannel.getId();
            if (isUser()) return USER_ID_MASK | mUser.getSession();
            return null;
        }

        public int getDepth() { return mDepth; }
        public boolean isExpanded() { return mExpanded; }
        public void setExpanded(boolean expanded) { mExpanded = expanded; }
    }

    public interface OnUserClickListener {
        void onUserClick(IUser user);
    }

    public interface OnChannelClickListener {
        void onChannelClick(IChannel channel);
    }
}