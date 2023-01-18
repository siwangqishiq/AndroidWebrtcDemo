package panyi.xyz.androidwebrtcdemo.adapter;

import android.util.Log;

import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;

import panyi.xyz.androidwebrtcdemo.MainActivity;

public class PeerConnectionObserverAdapter implements PeerConnection.Observer{

    private static final String  TAG = MainActivity.TAG;

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        Log.i(TAG , "onSignalingChange  " + signalingState);
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        Log.i(TAG , "onIceConnectionChange  " + iceConnectionState.toString());
    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) {
        Log.i(TAG , "onIceConnectionReceivingChange " + b);
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {

    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

    }

    @Override
    public void onAddStream(MediaStream mediaStream) {

    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {

    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {

    }

    @Override
    public void onRenegotiationNeeded() {
        Log.i(TAG , "onRenegotiationNeeded");
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        Log.i(TAG , "onAddTrack");
    }
}
