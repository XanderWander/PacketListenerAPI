package nl.xanderwander.packetlistenerapi.objects

import org.bukkit.entity.Player

data class MutablePacketContainer(
    var player: Player?,
    var packet: Any,
    var isCancelled: Boolean = false
)