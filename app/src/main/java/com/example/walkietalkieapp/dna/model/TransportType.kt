package com.example.walkietalkieapp.dna.model

/**
 * Represents the fundamental classification of communication transports and paths.
 * Sealed hierarchy ensures compile-time safety and seamless extensibility for future
 * mesh, relay, or custom transport protocols.
 */
sealed class TransportType(val id: String, val displayName: String) {
    data object Internet : TransportType("INTERNET", "Internet Cloud")
    data object WifiDirect : TransportType("WIFI_DIRECT", "Wi-Fi Direct P2P")
    data object Bluetooth : TransportType("BLUETOOTH", "Bluetooth Tactical Mesh")
    
    // Extensible slots for future multi-hop & alternative transports
    data class LocalLan(val subnet: String = "local") : TransportType("LOCAL_LAN", "Local LAN / Wi-Fi")
    data class RelayHop(val hopCount: Int, val relayId: String) : TransportType("RELAY_HOP", "Multi-Hop Relay ($hopCount hops)")
    data class Custom(val customId: String, val name: String) : TransportType(customId, name)

    companion object {
        fun fromId(id: String): TransportType {
            return when (id.uppercase()) {
                "INTERNET" -> Internet
                "WIFI_DIRECT" -> WifiDirect
                "BLUETOOTH" -> Bluetooth
                else -> Custom(id, id)
            }
        }
    }
}
