/*Edit By Ofaid/Ahmd-jr 9-9-2026 — ID SINKRON & PERAN*/
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
import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.db.MumlaDatabase;
import ofaid.ahmad.ptt.drawable.CircleDrawable;
import ofaid.ahmad.ptt.ofa.OfaIdentity;
import ofaid.ahmad.ptt.ofa.OfaRole;
import ofaid.ahmad.ptt.service.MumlaService;

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

    private String lokasiSaya;

    public void setLokasiSaya(String lokasiTeks) {
        this.lokasiSaya = lokasiTeks;
        notifyDataSetChanged();
    }

    public ChannelListAdapter(Context context, IHumlaService service, MumlaDatabase database,
                              FragmentManager fragmentManager, boolean showPinnedOnly,
                              boolean showChannelUserCount) throws RemoteException {
        setHasStableIds(true);
        mContext = context;
        mService = service;
        mDatabase = database;
        mFragmentManager = fragmentManager;
        mShowChannelUserCount = showChannelUserCount;

        mRootChannels = new ArrayList<>();
        if(showPinnedOnly) {
            mRootChannels = mDatabase.getPinnedChannels(mService.getTargetServer().getId());
        } else {
            mRootChannels.add(0);
        }

        mNodes = new LinkedList<>();
        mExpandedChannels = new HashMap<>();
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
            cvh.itemView.setOnClickListener(v -> {
                if (mChannelClickListener != null) {
                    mChannelClickListener.onChannelClick(channel);
                }
            });

            final boolean expandUsable = channel.getSubchannels().size() > 0 ||
                    channel.getSubchannelUserCount() > 0;
            cvh.mChannelExpandToggle.setImageResource(node.isExpanded() ?
                    R.drawable.ic_action_expanded : R.drawable.ic_action_collapsed);
            cvh.mChannelExpandToggle.setOnClickListener(v -> {
                mExpandedChannels.put(channel.getId(), !node.isExpanded());
                updateChannels();
                notifyDataSetChanged();
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
                cvh.mChannelUserCount.setText(String.format("%d", channel.getSubchannelUserCount()));
            } else {
                cvh.mChannelUserCount.setVisibility(View.GONE);
            }

            DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
            float margin = node.getDepth() * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25, metrics);
            cvh.mChannelHolder.setPadding((int) margin,
                    cvh.mChannelHolder.getPaddingTop(),
                    cvh.mChannelHolder.getPaddingRight(),
                    cvh.mChannelHolder.getPaddingBottom());

            cvh.mJoinButton.setOnClickListener(v -> {
                if (mService != null && mService.isConnected()) {
                    mService.HumlaSession().joinChannel(channel.getId());
                }
            });

            cvh.mMoreButton.setOnClickListener(v -> {
                ChannelMenu menu = new ChannelMenu(mContext, channel, mService, mDatabase, mFragmentManager);
                menu.showPopup(v);
            });

            cvh.itemView.setOnLongClickListener(v -> {
                cvh.mMoreButton.performClick();
                return true;
            });
        } else if (node.isUser()) {
            final IUser user = node.getUser();
            final UserViewHolder uvh = (UserViewHolder) viewHolder;
            uvh.itemView.setOnClickListener(v -> {
                if (mUserClickListener != null) {
                    mUserClickListener.onUserClick(user);
                }
            });

            // =============================================
            // ✅ TENTUKAN SESI — DIRI SENDIRI ATAU BUKAN
            // =============================================
            int sesiSaya = -1;
            boolean diriSendiri = false;
            String ofaId = null;
            
            try {
                if (mService != null && mService.isConnected()) {
                    sesiSaya = mService.HumlaSession().getSessionId();
                    diriSendiri = (user.getSession() == sesiSaya);
                }
            } catch (Exception ignored) {}

            // ✅ AMBIL OFA-ID — DIRI SENDIRI PAKAI YANG TERKUNCI
            if (diriSendiri) {
                ofaId = OfaIdentity.getGlobalOfaId(mContext);
            } else {
                int uid = user.getUserId();
                ofaId = "OFA-" + (Math.abs((uid * 7591 + uid * 31)) % 90000 + 10000);
            }

            // =============================================
            // ✅ NAMA
            // =============================================
            if (uvh.mUserName != null) {
                uvh.mUserName.setText(user.getName());
                uvh.mUserName.setVisibility(View.VISIBLE);
                uvh.mUserName.setTextColor(Color.parseColor("#FF9900"));
            }

            // =============================================
            // ✅ ID — SAMA DENGAN POPUP!
            // =============================================
            if (uvh.mUserIdView != null && ofaId != null) {
                String idTampil;
                if (diriSendiri) {
                    idTampil = OfaIdentity.getSingkat(mContext);
                } else {
                    idTampil = ofaId.length() > 10 ? ofaId.substring(0, 10) : ofaId;
                }
                uvh.mUserIdView.setText(idTampil);
                uvh.mUserIdView.setVisibility(View.VISIBLE);
                uvh.mUserIdView.setTextColor(0xFF00CCFF);
            }

            // =============================================
            // ✅ PERAN — TANPA CEK PEMILIK DULU
            // =============================================
            if (uvh.mUserStatusView != null && ofaId != null) {
                int peran = OfaRole.getPeranUser(mContext, ofaId);
                uvh.mUserStatusView.setText(OfaRole.getNamaPeran(peran));
                uvh.mUserStatusView.setTextColor(OfaRole.getWarnaPeran(peran));
                uvh.mUserStatusView.setVisibility(View.VISIBLE);
            }

            // =============================================
            // ✅ LOKASI
            // =============================================
            if (uvh.mUserLokasi != null) {
                String keterangan = user.getComment();
                String lokasiTampil = null;

                if (keterangan != null && !keterangan.trim().isEmpty()) {
                    String[] baris = keterangan.split("\\r?\\n");
                    for (String b : baris) {
                        String bersih = b.trim();
                        if (bersih.startsWith("📍") || (bersih.contains(". ") && !bersih.startsWith("📍"))) {
                            lokasiTampil = bersih;
                            break;
                        }
                    }
                }

                if (lokasiSaya != null && diriSendiri) {
                    lokasiTampil = lokasiSaya;
                }

                if (lokasiTampil != null) {
                    uvh.mUserLokasi.setText(lokasiTampil);
                    uvh.mUserLokasi.setTextColor(0xFFFF9900);
                    uvh.mUserLokasi.setVisibility(View.VISIBLE);
                } else {
                    uvh.mUserLokasi.setVisibility(View.GONE);
                }
            }

            // =============================================
            // ✅ TEBAL NAMA JIKA DIRI SENDIRI
            // =============================================
            if (uvh.mUserName != null) {
                uvh.mUserName.setTypeface(null, diriSendiri ? Typeface.BOLD : Typeface.NORMAL);
            }

            // =============================================
            // ✅ IKON BICARA
            // =============================================
            uvh.mUserTalkHighlight.setImageDrawable(getTalkStateDrawable(user));

            DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
            float margin = (node.getDepth() + 1) * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25, metrics);
            uvh.mUserHolder.setPadding((int) margin,
                    uvh.mUserHolder.getPaddingTop(),
                    uvh.mUserHolder.getPaddingRight(),
                    uvh.mUserHolder.getPaddingBottom());

            uvh.mMoreButton.setOnClickListener(v -> {
                UserMenu menu = new UserMenu(mContext, user, (MumlaService) mService,
                        mFragmentManager, ChannelListAdapter.this);
                menu.showPopup(v);
            });

            uvh.itemView.setOnLongClickListener(v -> {
                uvh.mMoreButton.performClick();
                return true;
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
        if (node.isChannel()) return R.layout.channel_row;
        if (node.isUser()) return R.layout.channel_user_row;
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

    public int getUserPosition(int sessionId) {
        long itemId = sessionId | USER_ID_MASK;
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
    
    public boolean isUserTalking(int sessionId) {
        if (sessionId <= 0 || mService == null || !mService.isConnected()) return false;
        try {
            IHumlaSession sesi = mService.HumlaSession();
            for (IUser user : sesi.getSessionChannel().getUsers()) {
                if (user.getSession() == sessionId) {
                    TalkState state = user.getTalkState();
                    return state == TalkState.TALKING
                        || state == TalkState.SHOUTING
                        || state == TalkState.WHISPERING;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Cek status bicara gagal", e);
        }
        return false;
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
                                    // ✅ CEK DIRI SENDIRI → AMBIL OFA-ID
                                    int sesiSayaRefresh = -1;
                                    try {
                                        if (mService != null && mService.isConnected()) {
                                            sesiSayaRefresh = mService.HumlaSession().getSessionId();
                                        }
                                    } catch (Exception ignored) {}
                                    
                                    int sesiUserRefresh = -1;
                                    int uidRefresh = -1;
                                    try {
                                        IUser u = node.getUser();
                                        sesiUserRefresh = u.getSession();
                                        uidRefresh = u.getUserId();
                                    } catch (Exception ignored) {}
                                    
                                    boolean diriSendiriRefresh = (sesiUserRefresh == sesiSayaRefresh);
                                    String ofaIdRefresh;
                                    
                                    if (diriSendiriRefresh) {
                                        ofaIdRefresh = OfaIdentity.getGlobalOfaId(mContext);
                                    } else if (uidRefresh >= 0) {
                                        ofaIdRefresh = "OFA-" + (Math.abs((uidRefresh * 7591 + uidRefresh * 31)) % 90000 + 10000);
                                    } else {
                                        ofaIdRefresh = null;
                                    }
                                    
                                    // ✅ BACA PERAN — TANPA CEK PEMILIK
                                    if (ofaIdRefresh != null) {
                                        int peran = OfaRole.getPeranUser(mContext, ofaIdRefresh);
                                        uvh.mUserStatusView.setText(OfaRole.getNamaPeran(peran));
                                        uvh.mUserStatusView.setTextColor(OfaRole.getWarnaPeran(peran));
                                    }
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
            new Thread(() -> {
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
