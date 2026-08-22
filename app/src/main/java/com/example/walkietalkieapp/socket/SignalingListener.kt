package com.example.walkietalkieapp.socket

interface SignalingListener {
    fun onOfferReceived(fromPeerId: String, sdp: String)
    fun onAnswerReceived(fromPeerId: String, sdp: String)
    fun onIceCandidateReceived(fromPeerId: String, candidate: String)
    fun onPeersReceived(peers: List<String>)
    fun onPeerLeft(peerId: String)
    fun onCallStarted()
    fun onCallEnded()
}
