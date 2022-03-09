package nl.xanderwander.packetlistenerapi

import io.netty.channel.Channel
import nl.xanderwander.packetlistenerapi.objects.MutablePacketContainer
import nl.xanderwander.packetlistenerapi.objects.PacketContainer
import nl.xanderwander.packetlistenerapi.reflection.TinyProtocol
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

class PacketListener(plugin: Plugin): TinyProtocol(plugin) {

    private val outListeners = arrayListOf<(packet: PacketContainer) -> Unit>()
    private val inListeners = arrayListOf<(packet: PacketContainer) -> Unit>()

    private val mutableOutListeners = arrayListOf<(packet: MutablePacketContainer) -> Unit>()
    private val mutableInListeners = arrayListOf<(packet: MutablePacketContainer) -> Unit>()

    override fun onPacketOutAsync(receiver: Player?, channel: Channel, packet: Any): Any? {
        return onPacketAsync(receiver, packet, outListeners, mutableOutListeners)
    }
    override fun onPacketInAsync(sender: Player?, channel: Channel, packet: Any): Any? {
        return onPacketAsync(sender, packet, inListeners, mutableInListeners)
    }

    private fun onPacketAsync(
        sender: Player?,
        packet: Any,
        staticListeners: ArrayList<(any: PacketContainer) -> Unit>,
        mutableListeners: ArrayList<(any: MutablePacketContainer) -> Unit>
    ): Any? {
        val container = PacketContainer(sender, packet)
        staticListeners.forEach { it.invoke(container) }
        val mutableContainer = MutablePacketContainer(sender, packet, container.isCancelled)
        mutableListeners.forEach { it.invoke(mutableContainer) }
        return if (mutableContainer.isCancelled) null else mutableContainer.packet
    }

    /**
     * Register an outgoing packet listener.
     * @param f - the lambda to be called.
     */
    fun onOutPacket(f: (packet: PacketContainer) -> Unit) {
        outListeners.add(f)
    }

    /**
     * Register an incoming packet listener.
     * @param f - the lambda to be called.
     */
    fun onInPacket(f: (packet: PacketContainer) -> Unit) {
        inListeners.add(f)
    }

    /**
     * Register an outgoing packet listener.
     * @param f - the lambda to be called.
     */
    fun onMutableOutPacket(f: (packet: MutablePacketContainer) -> Unit) {
        mutableOutListeners.add(f)
    }

    /**
     * Register an incoming packet listener.
     * @param f - the lambda to be called.
     */
    fun onMutableInPacket(f: (packet: MutablePacketContainer) -> Unit) {
        mutableInListeners.add(f)
    }

    /**
     * Register an outgoing packet listener.
     * @param f - the lambda to be called.
     */
    fun onOutPacketRemove(f: (packet: PacketContainer) -> Unit) {
        outListeners.remove(f)
    }

    /**
     * Register an incoming packet listener.
     * @param f - the lambda to be called.
     */
    fun onInPacketRemove(f: (packet: PacketContainer) -> Unit) {
        inListeners.remove(f)
    }

    /**
     * Register an outgoing packet listener.
     * @param f - the lambda to be called.
     */
    fun onMutableOutPacketRemove(f: (packet: MutablePacketContainer) -> Unit) {
        mutableOutListeners.remove(f)
    }

    /**
     * Register an incoming packet listener.
     * @param f - the lambda to be called.
     */
    fun onMutableInPacketRemove(f: (packet: MutablePacketContainer) -> Unit) {
        mutableInListeners.remove(f)
    }

}