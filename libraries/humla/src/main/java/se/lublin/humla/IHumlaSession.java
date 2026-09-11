package se.lublin.humla;

import java.util.List;

import se.lublin.humla.model.IChannel;
import se.lublin.humla.model.IUser;
import se.lublin.humla.model.Message;
import se.lublin.humla.model.ServerSettings;
import se.lublin.humla.model.WhisperTarget;
import se.lublin.humla.net.HumlaUDPMessageType;
import se.lublin.humla.util.VoiceTargetMode;

/**
 * An interface representing a live connection to the server.
 * Created by andrew on 28/02/17.
 */

public interface IHumlaSession {
    /**
     * @return the latency in milliseconds for the TCP connection.
     */
    long getTCPLatency();

    /**
     * @return the latency in milliseconds for the UDP connection.
     */
    long getUDPLatency();

    /**
     * @return the maximum bandwidth in bps for audio allowed by the server, or -1 if not set.
     */
    int getMaxBandwidth();

    /**
     * @return the current bandwidth in bps for audio sent to the server, or a negative integer
     *         if unknown (prior to connection or after disconnection).
     */
    int getCurrentBandwidth();

    
    int getServerVersion();

    String getServerRelease();

 
    String getServerOSName();

    
    String getServerOSVersion();


    int getSessionId();

  
    IUser getSessionUser();

    IChannel getSessionChannel();

    IUser getUser(int session);


    IChannel getChannel(int id);

  
    IChannel getRootChannel();

    int getPermissions();

    int getTransmitMode();

    HumlaUDPMessageType getCodec();

    boolean usingBluetoothSco();

    void enableBluetoothSco();

    void disableBluetoothSco();

    boolean isTalking();

    void setTalkingState(boolean talking);

    void joinChannel(int channel);

    void moveUserToChannel(int session, int channel);

    void createChannel(int parent, String name, String description, int position, boolean temporary);

    void sendAccessTokens(List<String> tokens);

    void requestBanList();

    void requestUserList();

    void requestPermissions(int channel);

    void requestComment(int session);
    
void setUserTexture(int session, byte[] data);

    void requestAvatar(int session);

    void requestChannelDescription(int channel);

    void registerUser(int session);

    void kickBanUser(int session, String reason, boolean ban);

    Message sendUserTextMessage(int session, String message);

    Message sendChannelTextMessage(int channel, String message, boolean tree);

    void setUserComment(int session, String comment);

    void setPrioritySpeaker(int session, boolean priority);

    void removeChannel(int channel);

    void setMuteDeafState(int session, boolean mute, boolean deaf);

    void setSelfMuteDeafState(boolean mute, boolean deaf);

    /**
     * Links the provided two channels together.
     */
    void linkChannels(IChannel channelA, IChannel channelB);

    /**
     * Unlinks the two provided channels.
     */
    void unlinkChannels(IChannel channelA, IChannel channelB);

    /**
     * Unlinks all channels from the provided channel.
     * @param channel The channel to be unlinked.
     */
    void unlinkAllChannels(IChannel channel);

  
    byte registerWhisperTarget(final WhisperTarget target);

  
    void unregisterWhisperTarget(byte targetId);

    void setVoiceTargetId(byte targetId);

    byte getVoiceTargetId();

    VoiceTargetMode getVoiceTargetMode();

    /**
     * Returns the current whisper target.
     * @return the set whisper target, or null if the user is not whispering.
     */
    WhisperTarget getWhisperTarget();

    /**
     * Gets the current server settings.
     * @return Settings of the current server.
     */
    ServerSettings getServerSettings();
}
