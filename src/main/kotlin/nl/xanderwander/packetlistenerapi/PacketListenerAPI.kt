package nl.xanderwander.packetlistenerapi

import org.bukkit.plugin.java.JavaPlugin

class PacketListenerAPI: JavaPlugin() {

    companion object {
        lateinit var listener: PacketListener
    }

    override fun onEnable() {
        listener = PacketListener(this)
    }

    override fun onDisable() {}

}