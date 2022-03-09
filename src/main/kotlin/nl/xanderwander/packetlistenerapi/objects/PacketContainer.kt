package nl.xanderwander.packetlistenerapi.objects

import org.bukkit.entity.Player

data class PacketContainer(
    val player: Player?,
    val packet: Any,
    var isCancelled: Boolean = false
)