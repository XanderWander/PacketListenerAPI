package nl.xanderwander.packetlistenerapi.reflection

import com.google.common.collect.MapMaker
import io.netty.channel.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level


/**
 * Modified by fren_gor to support 1.17+ servers
 * Modified by XanderWander to use Kotlin
 *
 * @author Kristian
 */
abstract class TinyProtocol(protected val plugin: Plugin) {
    private val channelLookup: MutableMap<String, Channel> = MapMaker().weakValues().makeMap()
    private var listener: Listener? = null
    private val uninjectedChannels = Collections.newSetFromMap(MapMaker().weakKeys().makeMap<Channel, Boolean>())
    private var networkManagers: MutableList<*>? = null
    private val serverChannels: MutableList<Channel> = ArrayList()
    private var serverChannelHandler: ChannelInboundHandlerAdapter? = null
    private var beginInitProtocol: ChannelInitializer<Channel>? = null
    private var endInitProtocol: ChannelInitializer<Channel>? = null
    private val handlerName: String

    @Volatile
    protected var closed = false

    @Volatile
    protected var injected = false

    init {
        handlerName = getHandlerName()
        registerBukkitEvents()
        try {
            registerChannelHandler()
            registerPlayers(plugin)
            injected = true
        } catch (ex: IllegalArgumentException) {
            plugin.logger.info("[protocol.TinyProtocol] Delaying server channel injection due to late bind.")
            object : BukkitRunnable() {
                override fun run() {
                    registerChannelHandler()
                    registerPlayers(plugin)
                    injected = true
                    plugin.logger.info("[protocol.TinyProtocol] Late bind injection successful.")
                }
            }.runTask(plugin)
        }
    }

    private fun createServerChannelHandler() {
        endInitProtocol = object : ChannelInitializer<Channel>() {
            @Throws(Exception::class)
            override fun initChannel(channel: Channel) {
                try {
                    synchronized(networkManagers!!) {
                        if (!closed) {
                            channel.eventLoop().submit(Callable { injectChannelInternal(channel) })
                        }
                    }
                } catch (e: Exception) {
                    plugin.logger.log(Level.SEVERE, "Cannot inject incoming channel $channel", e)
                }
            }
        }
        beginInitProtocol = object : ChannelInitializer<Channel>() {
            override fun initChannel(channel: Channel) {
                channel.pipeline().addLast(endInitProtocol)
            }
        }
        serverChannelHandler = object : ChannelInboundHandlerAdapter() {
            override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                val channel = msg as Channel
                channel.pipeline().addFirst(beginInitProtocol)
                ctx.fireChannelRead(msg)
            }
        }
    }

    private fun registerBukkitEvents() {
        listener = object : Listener {
            @EventHandler(priority = EventPriority.LOWEST)
            fun onPlayerLogin(e: PlayerLoginEvent) {
                if (closed) return
                val channel = getChannel(e.player)
                if (!uninjectedChannels.contains(channel)) {
                    injectPlayer(e.player)
                }
            }

            @EventHandler
            fun onPluginDisable(e: PluginDisableEvent) {
                if (e.plugin == plugin) {
                    close()
                }
            }
        }.apply {
            plugin.server.pluginManager.registerEvents(this, plugin)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerChannelHandler() {
        val mcServer = getMinecraftServer[Bukkit.getServer()]!!
        val serverConnection = getServerConnection[mcServer]!!
        networkManagers = getNetworkMarkers[serverConnection]
        val futures: MutableList<ChannelFuture> = getChannelFutures[serverConnection] as MutableList<ChannelFuture>
        createServerChannelHandler()
        synchronized(futures) {
            for (item in futures) {
                val serverChannel = item.channel()
                serverChannels.add(serverChannel)
                serverChannel.pipeline().addFirst(serverChannelHandler)
            }
        }
    }

    private fun unregisterChannelHandler() {
        if (serverChannelHandler == null) return
        for (serverChannel in serverChannels) {
            val pipeline = serverChannel.pipeline()
            serverChannel.eventLoop().execute {
                try {
                    pipeline.remove(serverChannelHandler)
                } catch (ignored: NoSuchElementException) {
                }
            }
        }
    }

    private fun registerPlayers(plugin: Plugin) {
        for (player in plugin.server.onlinePlayers) {
            injectPlayer(player)
        }
    }

    open fun onPacketOutAsync(receiver: Player?, channel: Channel, packet: Any): Any? {
        return packet
    }

    open fun onPacketInAsync(sender: Player?, channel: Channel, packet: Any): Any? {
        return packet
    }

    fun sendPacket(player: Player, packet: Any) {
        sendPacket(getChannel(player), packet)
    }

    fun sendPacket(channel: Channel, packet: Any) {
        channel.pipeline().writeAndFlush(packet)
    }

    fun receivePacket(player: Player, packet: Any) {
        receivePacket(getChannel(player), packet)
    }

    fun receivePacket(channel: Channel, packet: Any) {
        channel.pipeline().context("encoder").fireChannelRead(packet)
    }

    private fun getHandlerName(): String {
        return "tiny-" + plugin.name + "-" + ID.incrementAndGet()
    }

    fun injectPlayer(player: Player) {
        injectChannelInternal(getChannel(player)).player = player
    }

    private fun injectChannelInternal(channel: Channel): PacketInterceptor {
        return try {
            var interceptor = channel.pipeline()[handlerName] as PacketInterceptor?
            if (interceptor == null) {
                interceptor = PacketInterceptor()
                channel.pipeline().addBefore("packet_handler", handlerName, interceptor)
                uninjectedChannels.remove(channel)
            }
            interceptor
        } catch (e: IllegalArgumentException) {
            channel.pipeline()[handlerName] as PacketInterceptor
        }
    }

    fun getChannel(player: Player): Channel {
        var channel = channelLookup[player.name]
        if (channel == null) {
            val connection: Any = getConnection[getPlayerHandle.invoke(player)]!!
            val manager: Any = getManager[connection]!!
            getChannel[manager].let {
                channelLookup[player.name] = it
                channel = it
            }
        }
        return channel!!
    }

    fun uninjectPlayer(player: Player) {
        uninjectChannel(getChannel(player))
    }

    fun uninjectChannel(channel: Channel) {
        if (!closed) {
            uninjectedChannels.add(channel)
        }
        channel.eventLoop().execute { channel.pipeline().remove(handlerName) }
    }

    fun close() {
        if (!closed) {
            closed = true
            for (player in plugin.server.onlinePlayers) {
                uninjectPlayer(player)
            }
            HandlerList.unregisterAll(listener!!)
            unregisterChannelHandler()
        }
    }
    private inner class PacketInterceptor : ChannelDuplexHandler() {
        @Volatile
        var player: Player? = null

        @Throws(Exception::class)
        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            var message: Any? = msg
            val channel = ctx.channel()
            handleLoginStart(channel, message!!)
            try {
                message = onPacketInAsync(player, channel, message)
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "Error in onPacketInAsync().", e)
            }
            if (message != null) {
                super.channelRead(ctx, message)
            }
        }

        @Throws(Exception::class)
        override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
            var message: Any? = msg
            try {
                message = onPacketOutAsync(player, ctx.channel(), message!!)
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "Error in onPacketOutAsync().", e)
            }
            if (message != null) {
                super.write(ctx, message, promise)
            }
        }

        private fun handleLoginStart(channel: Channel, packet: Any) {
            if (PACKET_LOGIN_IN_START.isInstance(packet)) {
                val profile = getGameProfile[packet]
                val name: String = Reflection.getMethod(profile::class.java, "getName").invoke(profile) as String
                channelLookup[name] = channel
            }
        }
    }

    companion object {
        private val ID = AtomicInteger(0)
        private val getPlayerHandle: Reflection.MethodInvoker =
            Reflection.getMethod("{obc}.entity.CraftPlayer", "getHandle")
        private val playerConnectionClass = Reflection.getUntypedClass("{nms.server.network}.PlayerConnection")
        private val networkManagerClass = Reflection.getUntypedClass("{nms.network}.NetworkManager")
        private val getConnection: Reflection.FieldAccessor<*> =
            Reflection.getField("{nms.server.level}.EntityPlayer", null, playerConnectionClass)
        private val getManager: Reflection.FieldAccessor<*> =
            Reflection.getField(playerConnectionClass, null, networkManagerClass)
        private val getChannel: Reflection.FieldAccessor<Channel> = Reflection.getField(
            networkManagerClass,
            Channel::class.java, 0
        )
        private val minecraftServerClass = Reflection.getUntypedClass("{nms.server}.MinecraftServer")
        private val serverConnectionClass = Reflection.getUntypedClass("{nms.server.network}.ServerConnection")
        private val getMinecraftServer: Reflection.FieldAccessor<*> =
            Reflection.getField("{obc}.CraftServer", minecraftServerClass, 0)
        private val getServerConnection: Reflection.FieldAccessor<*> =
            Reflection.getField(minecraftServerClass, serverConnectionClass, 0)
        private val getChannelFutures: Reflection.FieldAccessor<MutableList<*>> = Reflection.getField(
            serverConnectionClass,
            MutableList::class.java, 0
        )
        private val getNetworkMarkers: Reflection.FieldAccessor<MutableList<*>> = Reflection.getField(
            serverConnectionClass,
            MutableList::class.java, 1
        )
        private val PACKET_LOGIN_IN_START = Reflection.getMinecraftClass("PacketLoginInStart", "network.protocol.login")
        private val gameProfileClass = Reflection.getClass("com.mojang.authlib.GameProfile")
        private val getGameProfile: Reflection.FieldAccessor<out Any> = Reflection.getField(
            PACKET_LOGIN_IN_START,
            gameProfileClass, 0
        )
    }
}