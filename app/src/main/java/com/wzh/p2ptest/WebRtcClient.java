package com.wzh.p2ptest;

import android.opengl.EGLContext;
import android.support.annotation.NonNull;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;

public class WebRtcClient {
    private final static String TAG = WebRtcClient.class.getCanonicalName();
    private final static int MAX_PEER = 2;
    private boolean[] endPoints = new boolean[MAX_PEER];
    private PeerConnectionFactory factory;
    private HashMap<String, Peer> peers = new HashMap<>();
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
    private MediaConstraints pcConstraints = new MediaConstraints();
    private RtcListener mListener;
    private Socket client;

    private IMessageReceiver iMessageReceiver;

    /**
     * Implement this interface to be notified of events.
     */
    public interface RtcListener {
        void onCallReady(String callId);

        void onStatusChanged(String newStatus);
        void onDataChannel(DataChannel dataChannel);

    }

    public IMessageReceiver getIMessageReceiver() {
        return iMessageReceiver;
    }

    public void setIMessageReceiver(IMessageReceiver iMessageReceiver) {
        this.iMessageReceiver = iMessageReceiver;
    }

    private interface Command {
        void execute(String peerId, JSONObject payload) throws JSONException;
    }

    private class CreateOfferCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "CreateOfferCommand peerId=" + peerId + " payload=" + payload);
            Peer peer = peers.get(peerId);
            peer.pc.createOffer(peer, pcConstraints);
        }
    }

    private class CreateAnswerCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "CreateAnswerCommand peerId=" + peerId + " payload=" + payload);
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );
            peer.pc.setRemoteDescription(peer, sdp);
            peer.pc.createAnswer(peer, pcConstraints);
        }
    }

    private class SetRemoteSDPCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "SetRemoteSDPCommand peerId=" + peerId + " payload=" + payload);
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );
            peer.pc.setRemoteDescription(peer, sdp);
        }
    }

    private class AddIceCandidateCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
//            Log.d(TAG,"AddIceCandidateCommand peerId="+peerId+" payload="+payload);
            PeerConnection pc = peers.get(peerId).pc;
            if (pc.getRemoteDescription() != null) {
                IceCandidate candidate = new IceCandidate(
                        payload.getString("id"),
                        payload.getInt("label"),
                        payload.getString("candidate")
                );
                pc.addIceCandidate(candidate);
            }
        }
    }

    /**
     * Send a message through the signaling server
     *
     * @param to      id of recipient
     * @param type    type of message
     * @param payload payload of message
     * @throws JSONException
     */
    public void sendMessage(String to, String type, JSONObject payload) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("to", to);
        message.put("type", type);
        message.put("payload", payload);
        client.emit("message", message);
        Log.i(TAG, "sendMessage=" + message);
    }

    private class MessageHandler {
        private HashMap<String, Command> commandMap;

        private MessageHandler() {
            this.commandMap = new HashMap<>();
            commandMap.put("init", new CreateOfferCommand());
            commandMap.put("offer", new CreateAnswerCommand());
            commandMap.put("answer", new SetRemoteSDPCommand());
            commandMap.put("candidate", new AddIceCandidateCommand());
        }

        private Emitter.Listener onMessage = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                try {
                    String from = data.getString("from");
                    String type = data.getString("type");
                    JSONObject payload = null;
                    if (!type.equals("init")) {
                        payload = data.getJSONObject("payload");
                    }
                    Log.d(TAG, "onMessage data=" + data);
                    // if peer is unknown, try to add him
                    if (!peers.containsKey(from)) {
                        // if MAX_PEER is reach, ignore the call
                        int endPoint = findEndPoint();
                        if (endPoint != MAX_PEER) {
                            Peer peer = addPeer(from, endPoint);
                            commandMap.get(type).execute(from, payload);
                        }
                    } else {
                        commandMap.get(type).execute(from, payload);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };

        private Emitter.Listener onId = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String id = (String) args[0];
                Log.d(TAG, "onId data=" + id);

                mListener.onCallReady(id);
            }
        };
    }

    private class Peer implements SdpObserver, PeerConnection.Observer {
        private DataChannel channel;
        private PeerConnection pc;
        private String id;
        private int endPoint;

        @Override
        public void onCreateSuccess(final SessionDescription sdp) {
            // TODO: modify sdp to use pcParams prefered codecs
            try {
                JSONObject payload = new JSONObject();
                payload.put("type", sdp.type.canonicalForm());
                payload.put("sdp", sdp.description);
                Log.i(TAG, "onCreateSuccess" + sdp);
                sendMessage(id, sdp.type.canonicalForm(), payload);
                pc.setLocalDescription(Peer.this, sdp);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSetSuccess() {
            Log.i(TAG, "Peer onSetSuccess");

        }

        @Override
        public void onCreateFailure(String s) {
            Log.i(TAG, "Peer onCreateFailure " + s);

        }

        @Override
        public void onSetFailure(String s) {
            Log.i(TAG, "Peer onSetFailure " + s);

        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.i(TAG, "Peer onSignalingChange " + signalingState);
            mListener.onStatusChanged("onSignalingChange " + signalingState.name());

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            mListener.onStatusChanged("onIceConnectionChange " + iceConnectionState.name());

            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                removePeer(id);
            } else if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {//连接成功
                mListener.onStatusChanged("连接成功");
            }
            Log.i(TAG, "Peer onIceConnectionChange " + iceConnectionState);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.i(TAG, "Peer onIceGatheringChange " + iceGatheringState);
            mListener.onStatusChanged("onIceGatheringChange " + iceGatheringState.name());


        }

        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("label", candidate.sdpMLineIndex);
                payload.put("id", candidate.sdpMid);
                payload.put("candidate", candidate.sdp);
                sendMessage(id, "candidate", payload);

//                Log.i(TAG,"onIceCandidate "+candidate);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(TAG, "onAddStream " + mediaStream.label());
            // remote streams are displayed from 1 to MAX_PEER (0 is localStream)
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "onRemoveStream " + mediaStream.label());
            removePeer(id);
        }

        @Override
        public void onDataChannel(final DataChannel dataChannel) {
            Log.i(TAG, "Peer onDataChannel=" + dataChannel);
            channel=dataChannel;
            mListener.onDataChannel(dataChannel);
            mListener.onStatusChanged("DataChannel " + channel.state());

        }

        @Override
        public void onRenegotiationNeeded() {
            Log.i(TAG, "Peer onRenegotiationNeeded=");

        }

        public Peer(String id, int endPoint) {
            Log.d(TAG, "new Peer: " + id + " " + endPoint);
            this.pc = factory.createPeerConnection(iceServers, pcConstraints, this);
//            pc.createDataChannel()
            this.id = id;
            this.endPoint = endPoint;

//            pc.addStream(localMS); //, new MediaConstraints()

            mListener.onStatusChanged("CONNECTING");

            channel = pc.createDataChannel("sendDataChannel", new DataChannel.Init());
            Log.i(TAG, "createDataChannel " + channel);
            channel.registerObserver(new DataChannel.Observer() {

                byte[] mReceiveSrcData;

                @Override
                public void onStateChange() {
                    Log.d(TAG, "registerObserver onStateChange" + channel.state());
                    mListener.onStatusChanged("DataChannel " + channel.state());
                }

                @Override
                public void onMessage(DataChannel.Buffer buffer) {
                    Log.d(TAG, "registerObserver onMessage" + buffer + " data=" + buffer.data + " isBinary=" + buffer.binary);

                    int capacity = buffer.data.capacity();
                    byte[] bytes = new byte[capacity];
                    buffer.data.get(bytes);

                    int index = (int) getValue(bytes, 0, 2);
                    long packSize = getValue(bytes, 2, 4);//总包数
                    long packType = getValue(bytes, 4, 5);//包所在类型
                    int totalDataLength = (int) getValue(bytes, 5, 10);//总数据长度
                    int dataLength = (int) getValue(bytes, 10, 13);//当前包data长度
                    int type = (int) getValue(bytes, 13, 14);//type

                    if (index == 0)
                        mReceiveSrcData = new byte[totalDataLength];

                    System.arraycopy(bytes, PACK_DATA_START_INDEX, mReceiveSrcData, index * MAX_PACK_DATA_SIZE, dataLength);

                    if (iMessageReceiver != null) {
                        if (packType == 0 || index==0) {
                            iMessageReceiver.onReceiverStart();
                        }
                        if (packType == 1) {
                            iMessageReceiver.onReceiverProcess((float) ((double) index*100 / packSize));
                        }
                        if (packType == 2) {
                            iMessageReceiver.onReceiverSuccess(mReceiveSrcData,type);
                            mReceiveSrcData =null;
                        }
                    }

                }
            });


        }
    }

    private Peer addPeer(String id, int endPoint) {
        Peer peer = new Peer(id, endPoint);
        peers.put(id, peer);

        endPoints[endPoint] = true;
        return peer;
    }

    private void removePeer(String id) {
        Peer peer = peers.get(id);
        peer.pc.close();
        peers.remove(peer.id);
        endPoints[peer.endPoint] = false;
    }

    public WebRtcClient(RtcListener listener, String host, EGLContext mEGLcontext) {
        mListener = listener;
        PeerConnectionFactory.initializeAndroidGlobals(listener, true, true,
                true, mEGLcontext);
        factory = new PeerConnectionFactory();
        MessageHandler messageHandler = new MessageHandler();

        try {
            client = IO.socket(host);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        client.on("id", messageHandler.onId);
        client.on("message", messageHandler.onMessage);
        client.connect();
        iceServers.add(new PeerConnection.IceServer("stun:39.104.185.143:3478"));

        // stun 打洞服务器 turn 打洞不行后中继服务服务器
        iceServers.add(new PeerConnection.IceServer("turn:39.104.185.143:3478?transport=udp", "webrtc", "webrtc"));
        iceServers.add(new PeerConnection.IceServer("turn:39.104.185.143:3478?transport=tcp", "webrtc", "webrtc"));

   /*
     iceServers.add(new PeerConnection.IceServer("stun:23.21.150.121"));
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        iceServers.add(new PeerConnection.IceServer("stun:stun.services.mozilla.com"));

        //免费的turn服务
        iceServers.add(new PeerConnection.IceServer("turn:numb.viagenie.ca", "", ""));//这个网站可以自己去申请账号密码
        iceServers.add(new PeerConnection.IceServer("turn:192.158.29.39:3478?transport=udp", "28224511:1379330808", "JZEOEt2V3Qb0y27GRntt2u2PAYA="));
        iceServers.add(new PeerConnection.IceServer("turn:192.158.29.39:3478?transport=tcp", "28224511:1379330808", "JZEOEt2V3Qb0y27GRntt2u2PAYA="));

        iceServers.add(new PeerConnection.IceServer("turn:turn.bistri.com:80", "homeo", "homeo"));
         iceServers.add(new PeerConnection.IceServer("turn:turn.anyfirewall.com:443?transport=tcp", "webrtc", "webrtc"));

         */
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));

        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    }

    /**
     * Call this method in Activity.onPause()
     */
    public void onPause() {
    }

    /**
     * Call this method in Activity.onResume()
     */
    public void onResume() {
    }

    /**
     * Call this method in Activity.onDestroy()
     */
    public void onDestroy() {
        for (Peer peer : peers.values()) {
            peer.pc.dispose();
        }
        factory.dispose();
        client.disconnect();
        client.close();
    }

    private int findEndPoint() {
        for (int i = 0; i < MAX_PEER; i++) if (!endPoints[i]) return i;
        return MAX_PEER;
    }

    /**
     * Start the client.
     * <p>
     * Set up the local stream and notify the signaling server.
     * Call this method after onCallReady.
     *
     * @param name client name
     */
    public void start(String name) {
        try {
            JSONObject message = new JSONObject();
            message.put("name", name);
            client.emit("readyToStream", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static final int MAX_PACK_SIZE = 66528;//每个包最大size
    public static final int MAX_PACK_DATA_SIZE = 65536;//每个包中数据体最大size
    public static final int PACK_DATA_START_INDEX = MAX_PACK_SIZE - MAX_PACK_DATA_SIZE;//包中data体前剩余空间，用于存放索引、总长度等

    //2^14-1=16383
    public static final int MAX_INDEX = (int) (Math.pow(2, 14) - 1);

    /**
     * 发送数据
     * @param channel 和peer 形成的数据通道
     * @param data 数据
     * @param type 数据类型，自定义
     * @return
     */
    public boolean sendData(DataChannel channel, @NonNull byte[] data,int type) {
        DataChannel.State state = null;
        if (channel == null || (state = channel.state()) != DataChannel.State.OPEN) {
            Log.d(TAG, "channel = " + channel + " and state is " + state);
            return false;
        }
        int len = data.length;
        if (len > 1073676288) {
            throw new RuntimeException("data.length range 1 ~ 16383 *$MAX_PACK_DATA_SIZE");
        }
        int quotient = len / MAX_PACK_DATA_SIZE;//商
        int remainder = len % MAX_PACK_DATA_SIZE;//余数

        int packSize = (remainder == 0 ? quotient : quotient + 1);//总包数

        Log.d(TAG, "arrays.len=" + len + " quotient=" + quotient + " remainder=" + remainder);
        for (int i = 0; i < quotient; i++) {
            byte[] dest = new byte[MAX_PACK_SIZE];
            int start = i * MAX_PACK_DATA_SIZE;
            System.arraycopy(data, start, dest, PACK_DATA_START_INDEX, MAX_PACK_DATA_SIZE);
            setValue(dest, 0, 2, i);//index
            setValue(dest, 2, 4, packSize);//总包数
            setValue(dest, 4, 5, buildType(quotient, remainder, i));//包所在类型
            setValue(dest, 5, 10, len);//总数据长度
            setValue(dest, 10, 13, MAX_PACK_DATA_SIZE);//当前包data长度
            setValue(dest, 13, 14, type);//自定义type

//            Log.d(TAG, "i" + i + " start " + start + " length=" + dest.length);
            channel.send(new DataChannel.Buffer(ByteBuffer.wrap(dest), true));
        }

        if (remainder != 0) {//不够packSize 的end

            byte[] dest = new byte[remainder + PACK_DATA_START_INDEX];
            System.arraycopy(data, len - remainder, dest, PACK_DATA_START_INDEX, remainder);

            setValue(dest, 0, 2, packSize-1);//index
            setValue(dest, 2, 4, packSize);//总包数
            setValue(dest, 4, 5, 2);//包所在类型
            setValue(dest, 5, 10, len);//总数据长度
            setValue(dest, 10, 13, remainder);//当前包data长度
            setValue(dest, 13, 14, type);//自定义type

//            Log.d(TAG, "remainder=" + remainder);
            channel.send(new DataChannel.Buffer(ByteBuffer.wrap(dest), true));
        }
        return true;
    }

    /**
     * 把long 型 value 值 转换成byte数组 表示
     * 如 setValue(data,0,2,333) 表示在数组中0，1两个byte 中分隔存放 333
     *
     * @param data
     * @param firstPosition 数据存放开始索引
     * @param endPosition   数据存放结束索引位置，在数组不包含此位置，如 firstPosition=0，endPosition=2,表示 0,1索引
     * @param value         要表示的int值
     */
    private static void setValue(byte[] data, int firstPosition, int endPosition, long value) {
        if (endPosition - firstPosition < 1)
            throw new IndexOutOfBoundsException("end - first must > 0 " + endPosition + " - " + firstPosition);

        if (value <= Byte.MAX_VALUE) {//byte -128~127
            for (int i = firstPosition; i < endPosition - 1; i++) {
                data[i] = 0;
            }
            data[endPosition - 1] = (byte) value;
        } else {
            String binary = Long.toBinaryString(value);//二进制
            int len = binary.length();
            int maxBit = (endPosition - firstPosition) * 7;
            if (len > maxBit) {//超出最大可表示数
                throw new IndexOutOfBoundsException(value + "--> " + binary + " out of " + maxBit + " bit byte value");
            }

            for (int i = endPosition - 1; i >= firstPosition; i--) {
                long newValue = value >> 7;//去除后七位
                data[i] = (byte) (value - (newValue << 7));//加上七位0，相减就是后七位值
                value = newValue;
            }
        }
    }

    /**
     * 把fir~end 位的数转换为对应value
     *
     * @param data
     * @param firstPosition
     * @param endPosition   如fir=0，end=2,表示0,1两位，不包含索引2
     * @return
     */
    private static long getValue(byte[] data, int firstPosition, int endPosition) {
        if (endPosition - firstPosition < 1)
            throw new IndexOutOfBoundsException("end - first must > 0 " + endPosition + " - " + firstPosition);

        long value = 0;
        int bit = endPosition - firstPosition - 1;
        for (int i = firstPosition; i < endPosition; i++, bit--) {
            long bitValue = (long) data[i] << (7 * bit);
//            System.out.println("bit: " + bit + " bitV=" + bitValue);
            value += bitValue;
        }
        return value;
    }

    private int buildType(int size, int footer, int i) {
        int type;
        if (i == 0)
            type = 0;//头
        else if (footer == 0) {
            if (i != size - 1)
                type = 1;//中间部分
            else
                type = 2;//尾
        } else {
            type = 1;
        }
        return type;
    }

    public interface IMessageReceiver {
        void onReceiverStart();

        void onReceiverProcess(float process);

        void onReceiverSuccess(byte[] data,int type);
    }

}
