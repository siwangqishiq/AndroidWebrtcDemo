package panyi.xyz.androidwebrtcdemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import panyi.xyz.androidwebrtcdemo.adapter.PeerConnectionObserverAdapter;
import panyi.xyz.androidwebrtcdemo.adapter.SdpObserverAdapter;

public class MainActivity extends AppCompatActivity {
    public static final int REQUEST_CODE_PERMISSION = 101;

    public static final String TAG = "WebRtcTest";

    private static final String KEY_CMD = "cmd";
    private static final String KEY_DATA = "data";
    private static final String CMD_OFFER = "offer";
    private static final String CMD_ANSWER = "answer";
    private static final String CMD_ICECANDIDATE = "icecandidate";

    private View mStartBtn;
    private View mCloseBtn;

    private EglBase mEglBase;
    private SurfaceViewRenderer mLocalSurfaceView;
    private SurfaceViewRenderer mRemoteSurfaceView;

    private WebSocketClient mWebsocket;

    private PeerConnectionFactory mPeerConnectionFactory;

    private MediaStream mLocalMediaStream;
    private MediaStream mRemoteMediaStream;

    private PeerConnection mPeerConnection;

    private Handler mUIHandler = new Handler(Looper.getMainLooper());

    private List<IceCandidate> receivedIceCandidateList = new ArrayList<IceCandidate>(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        connectWebsocket();
        if(requestPermission()){
            mStartBtn.setOnClickListener((v)->{
                startInitWebrtc();
            });
            mCloseBtn.setOnClickListener((v)->{
                if(mPeerConnection != null){
                    mPeerConnection.dispose();
                    mPeerConnection = null;
                }
            });
        }
    }

    private void initView(){
        mLocalSurfaceView = findViewById(R.id.local_view);
        mRemoteSurfaceView = findViewById(R.id.remote_view);
        mStartBtn = findViewById(R.id.start_btn);
        mCloseBtn = findViewById(R.id.close_btn);

        mEglBase = EglBase.create();
    }

    private boolean requestPermission(){
        if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            Log.i(TAG , "need request permission");
            requestPermissions(new String[]{Manifest.permission.CAMERA , Manifest.permission.RECORD_AUDIO},REQUEST_CODE_PERMISSION);
            return false;
        }

        Log.i(TAG , "have all permission");
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode != REQUEST_CODE_PERMISSION){
            return;
        }

        for(int result : grantResults){
            if(result != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this , "必要权限未授予!" , Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void connectWebsocket(){
        try {
            final URI uri = new URI("ws://10.242.142.129:9999/signal");
            mWebsocket = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Log.i(TAG , "websocket open " + mWebsocket.getReadyState());
                }

                @Override
                public void onMessage(String message) {
                    Log.i(TAG , "websocket onMessage " + message);

                    try {
                        final JSONObject jsonData = new JSONObject(message);
                        final String cmd = jsonData.getString("cmd");
                        Log.i(TAG , "cmd : " + cmd);

                        if(CMD_OFFER.equals(cmd)){
                            mUIHandler.post(()->{
                                handleOffer(jsonData);
                            });
                        }else if(CMD_ANSWER.equals(cmd)){
                            handleAnswer(jsonData);
                        }else if(CMD_ICECANDIDATE.equals(cmd)){
                            handleIceCandidate(jsonData);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG , e.getMessage());
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.i(TAG , "websocket onClose ");
                }

                @Override
                public void onError(Exception ex) {
                    Log.i(TAG , "websocket onError " + ex.getMessage());
                }
            };

            mWebsocket.connect();
        } catch (URISyntaxException e) {
            Log.e(TAG , e.getMessage());
        }
    }

    private void startInitWebrtc(){
        startLocalMediaStream();   //1.开启本地流
        createPeerConnection();//2.创建peerconnection
        boolean addLocalStreamResult = mPeerConnection.addStream(mLocalMediaStream);//将本地流 添加到peerconnection
        Log.i(TAG , "addLocalStreamResult : " + addLocalStreamResult);
        //3生成offer
        mPeerConnection.createOffer(new SdpObserverAdapter(){
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                mPeerConnection.setLocalDescription(new SdpObserverAdapter(){
                    @Override
                    public void onSetSuccess() {
                        SessionDescription description = mPeerConnection.getLocalDescription();
                        Log.i(TAG , "create offer : " + description.description);
                        JSONObject dataJson = new JSONObject();
                        try {
                            dataJson.put("type" , "offer");
                            dataJson.put("sdp" , description.description);
                            sendSignal(CMD_OFFER , dataJson);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                } , sessionDescription);
            }
        } , offerOrAnswerConstraint());
    }

    private MediaConstraints offerOrAnswerConstraint() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        ArrayList<MediaConstraints.KeyValuePair> keyValuePairs = new ArrayList<>();
//        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
//        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        mediaConstraints.mandatory.addAll(keyValuePairs);
        return mediaConstraints;
    }

    private void createPeerConnection(){
        if(mPeerConnection != null){
            return;
        }

        PeerConnection.IceServer stun =  PeerConnection.IceServer.builder("stun:101.34.23.152:3478").createIceServer();
        PeerConnection.IceServer iceServer = PeerConnection.IceServer
                .builder("turn:101.34.23.152:3478")
                .setUsername("panyi")
                .setPassword("123456").createIceServer();
        List<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>(2);
        iceServers.add(stun);
        iceServers.add(iceServer);

        mPeerConnection = getPeerConnectionFactory().createPeerConnection(iceServers, new PeerConnectionObserverAdapter() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                Log.i(TAG , "onIceCandidate : " + iceCandidate.sdp);
                try {
                    JSONObject json = new JSONObject();
                    json.put("sdpMid" , iceCandidate.sdpMid);
                    json.put("sdpMLineIndex" , iceCandidate.sdpMLineIndex);
                    json.put("candidate", iceCandidate.sdp);
                    sendSignal(CMD_ICECANDIDATE , json);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.i(TAG , "onAdd Remote Stream");
                onAddRemoteStream(mediaStream);
            }

            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.i(TAG , "onSignalingChange  " + signalingState);
                if(signalingState == PeerConnection.SignalingState.STABLE){
                    if(receivedIceCandidateList.size() > 0){
                        Log.i(TAG , "create peerconnection add icecandidate size : " + receivedIceCandidateList.size());
                        for(IceCandidate candidate : receivedIceCandidateList){
                            boolean result = mPeerConnection.addIceCandidate(candidate);
                            Log.i(TAG , "mPeerConnection addIceCandidate result : " + result);
                        }
                        receivedIceCandidateList.clear();
                    }
                }
            }

        });
    }

    public void onAddRemoteStream(MediaStream remoteMediaStream){
        mRemoteMediaStream = remoteMediaStream;
        attachMediaStreamToView(mRemoteSurfaceView , remoteMediaStream);
    }

    private void startLocalMediaStream() {
        mLocalMediaStream = getPeerConnectionFactory().createLocalMediaStream("ARDAMS");
        //audio
        AudioSource audioSource =getPeerConnectionFactory().createAudioSource(createAudioConstraints());
        AudioTrack audioTrack = getPeerConnectionFactory().createAudioTrack("ARDAMSa0" , audioSource);
        mLocalMediaStream.addTrack(audioTrack);
        //video
        VideoCapturer videoCapture = createVideoCapture();
        VideoSource videoSource = getPeerConnectionFactory().createVideoSource(videoCapture.isScreencast());
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mEglBase.getEglBaseContext());
        videoCapture.initialize(surfaceTextureHelper, this,videoSource.getCapturerObserver());
        videoCapture.startCapture(mLocalSurfaceView.getWidth(), mLocalSurfaceView.getHeight(), 8);
        VideoTrack videoTrack = getPeerConnectionFactory().createVideoTrack("ARDAMSv0" , videoSource);
        mLocalMediaStream.addTrack(videoTrack);

        attachMediaStreamToView(mLocalSurfaceView , mLocalMediaStream);
    }

    private void attachMediaStreamToView(final SurfaceViewRenderer surfaceView ,  final MediaStream mediaStream){
        mUIHandler.post(()->{
            surfaceView.init(mEglBase.getEglBaseContext(), null);
            surfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
            surfaceView.setMirror(true);
            if(mediaStream.videoTracks.size() > 0){
                List<VideoTrack> vTracks = mediaStream.videoTracks;
                for(VideoTrack track : vTracks){
                    track.addSink(surfaceView);
                }//end for each
            }
        });
    }

    private VideoCapturer createVideoCapture(){
        VideoCapturer videoCapturer = null;
        if (Camera2Enumerator.isSupported(this)) {
            Camera2Enumerator enumerator = new Camera2Enumerator(this);
            videoCapturer = createCameraCapture(enumerator);
        }else {
            Camera1Enumerator enumerator = new Camera1Enumerator(true);
            videoCapturer = createCameraCapture(enumerator);
        }
        return videoCapturer;
    }

    private MediaConstraints createAudioConstraints() {
        MediaConstraints audioConstraints = new MediaConstraints();
//        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
//        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
//        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "false"));
//        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));
        return audioConstraints;
    }

    private PeerConnectionFactory getPeerConnectionFactory(){
        if(mPeerConnectionFactory != null){
            return mPeerConnectionFactory;
        }

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this)
                .createInitializationOptions());
        VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(mEglBase.getEglBaseContext(),
                true, true);
        VideoDecoderFactory decoderFactory=new DefaultVideoDecoderFactory(mEglBase.getEglBaseContext());
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        mPeerConnectionFactory = PeerConnectionFactory.builder()
                    .setOptions(options)
                    .setAudioDeviceModule(JavaAudioDeviceModule.builder(this).createAudioDeviceModule())
                    .setVideoDecoderFactory(decoderFactory)
                    .setVideoEncoderFactory(encoderFactory)
                    .createPeerConnectionFactory();
        return mPeerConnectionFactory;
    }

    private VideoCapturer createCameraCapture(CameraEnumerator enumerator) {
        String[] deviceNames = enumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer= enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }

        }
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer= enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        onDispose();
        super.onDestroy();
    }

    private void onDispose(){
        if(mWebsocket != null && mWebsocket.getReadyState() != ReadyState.CLOSED
                && mWebsocket.getReadyState() != ReadyState.CLOSING){
            mWebsocket.close();
        }

        if(mLocalSurfaceView != null){
            mLocalSurfaceView.release();
        }

        if(mRemoteSurfaceView != null){
            mRemoteSurfaceView.release();
        }

        if(mEglBase != null){
            mEglBase.release();
        }
    }

    public void sendSignal(String cmd , JSONObject data){
        try {
            JSONObject json = new JSONObject();
            json.put(KEY_CMD , cmd);
            json.put(KEY_DATA , data);

            if(mWebsocket != null && mWebsocket.getReadyState() != ReadyState.CLOSED
                    && mWebsocket.getReadyState() != ReadyState.CLOSING){
                mWebsocket.send(json.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }



    private void handleOffer(JSONObject json){
        try {
            String dataString = json.getString(KEY_DATA);
            JSONObject jsonData = new JSONObject(dataString);
            Log.i(TAG , "offer data : " + jsonData);

            startLocalMediaStream();   //1.开启本地流
            createPeerConnection();//2.创建peerconnection
            boolean addLocalStreamResult = mPeerConnection.addStream(mLocalMediaStream);//将本地流 添加到peerconnection
            Log.i(TAG , "addLocalStreamResult : " + addLocalStreamResult);

            SessionDescription description = new SessionDescription(SessionDescription.Type.OFFER , jsonData.getString("sdp"));
            mPeerConnection.setRemoteDescription(new SdpObserverAdapter(){
                @Override
                public void onSetSuccess() {
                    mPeerConnection.createAnswer(new SdpObserverAdapter(){
                        @Override
                        public void onCreateSuccess(SessionDescription answerDescription) {
                            mPeerConnection.setLocalDescription(new SdpObserverAdapter(){
                                @Override
                                public void onSetSuccess() {
                                    JSONObject dataJson = new JSONObject();
                                    try {
                                        dataJson.put("type" , "answer");
                                        dataJson.put("sdp" , answerDescription.description);
                                        sendSignal(CMD_ANSWER , dataJson);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            } , answerDescription);
                        }
                    } , offerOrAnswerConstraint());
                }
            }, description);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleAnswer(JSONObject json){
        try {
            String dataString = json.getString(KEY_DATA);
            JSONObject jsonData = new JSONObject(dataString);
            String sdp = jsonData.getString("sdp");
            SessionDescription answerDescription = new SessionDescription(SessionDescription.Type.ANSWER , sdp);
            mPeerConnection.setRemoteDescription(new SdpObserverAdapter(){
                @Override
                public void onSetSuccess() {
                    Log.i(TAG , "peerconection set remote success " + mPeerConnection.signalingState());
                }
            }, answerDescription);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleIceCandidate(JSONObject json){
        try {
            String dataString = json.getString(KEY_DATA);
            if(TextUtils.isEmpty(dataString)){
                return;
            }
            Log.i(TAG , "dataString : " + dataString);

            JSONObject jsonData = new JSONObject(dataString);
            String sdpMid = jsonData.getString("sdpMid");
            int sdpMLineIndex = jsonData.getInt("sdpMLineIndex");

            String sdp = null;
            if(!jsonData.isNull("sdp")){
                sdp = jsonData.getString("sdp");
            }else if(!jsonData.isNull("candidate")){
                sdp = jsonData.getString("candidate");
            }
            IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex , sdp);
            // createPeerConnection();
            if(mPeerConnection == null){
                Log.i(TAG , "peerconnection is null return");
                receivedIceCandidateList.add(iceCandidate);
                return;
            }

            if(receivedIceCandidateList.size() > 0){
                for(IceCandidate candidate : receivedIceCandidateList){
                    mPeerConnection.addIceCandidate(candidate);
                }
                receivedIceCandidateList.clear();
            }
            boolean iceCandidateResult = mPeerConnection.addIceCandidate(iceCandidate);
            Log.i(TAG , "iceCandidateResult : " + iceCandidateResult);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}