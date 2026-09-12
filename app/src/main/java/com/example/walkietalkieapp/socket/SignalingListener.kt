package com.example.walkietalkieapp.socket

interface SignalingListener {
    fun onPeerLeft(peerId: String)
    fun onCallStarted()
    fun onCallEnded()
}
