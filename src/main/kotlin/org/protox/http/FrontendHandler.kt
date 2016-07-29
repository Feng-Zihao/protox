package org.protox.http

import com.sun.javafx.scene.control.skin.VirtualFlow
import com.sun.jmx.remote.internal.ArrayQueue
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.GenericFutureListener
import org.protox.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*


class FrontendHandler(val config: Config) : ChannelDuplexHandler() {

    lateinit var remoteHost: String

    var remotePort: Int = 0
    @Volatile var consumeFirstContent: Boolean = false

    var backChn: Channel? = null

    var proxyRule: Config.ProxyRule? = null

    var LOGGER : Logger = LoggerFactory.getLogger(FrontendHandler::class.java)

    val msgHolder : Queue<HttpObject> = ArrayDeque(10)

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        msgHolder.clear()
        ctx.read()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        ReferenceCountUtil.retain(msg)
        msgHolder.add(msg as HttpObject)
    }


    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        if (msgHolder.peek() is HttpRequest) {
            routeAndLaunchBackend(ctx)
        } else {
            val compositeByteBuf = CompositeByteBuf(ByteBufAllocator.DEFAULT, false, 10)
            while (msgHolder.isNotEmpty()) {
                compositeByteBuf.addComponent((msgHolder.poll() as HttpContent).content())
            }
            backChn!!.writeAndFlush(compositeByteBuf).addListener {
                clearMsgHolder()
                (it as ChannelFuture).channel().read()
            }
        }
        super.channelReadComplete(ctx)
    }


    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise?) {
        var future = ctx.write(msg, promise)

        if (msg is LastHttpContent) {
            LOGGER.debug("{}", frontendCounter.decrementAndGet())
//            LOGGER.debug("{}", msg)

            future.addListener {
                ctx.close()
            }
        }
    }

    private fun flushAndTryReadNext(ctx: ChannelHandlerContext, msg: HttpContent) {
        backChn!!.writeAndFlush(msg).addListener {
            ReferenceCountUtil.release(msg)
            if (it.isSuccess) {
                if (msg !is LastHttpContent) {
                    ctx.read()
                }
            } else {
                tryCloseChannel(ctx.channel())
                tryCloseChannel(backChn)
            }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        super.channelInactive(ctx)
        try {
            clearMsgHolder()
        } catch (e: UninitializedPropertyAccessException) {
            // pass
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            LOGGER.info("{}", evt.state())
            tryCloseChannel(ctx.channel())
            tryCloseChannel(backChn)
        }
    }

    private fun routeAndLaunchBackend(ctx: ChannelHandlerContext) {
        LOGGER.debug("{}", frontendCounter.incrementAndGet())
        val serverRequest = msgHolder.poll() as HttpRequest

        var matchRule = config.matchProxyRuleOrNull(serverRequest)
        if (matchRule == null) {
            val response = DefaultFullHttpResponse(
                    serverRequest.protocolVersion(),
                    HttpResponseStatus.BAD_GATEWAY,
                    Unpooled.copiedBuffer("Bad Gateway", Charsets.UTF_8),
                    DefaultHttpHeaders().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE),
                    EmptyHttpHeaders.INSTANCE)

            ctx.writeAndFlush(response).addListener {
                clearMsgHolder()
                (it as ChannelFuture).channel().close()
            }
            return
        }

        this.proxyRule = matchRule

        remoteHost = proxyRule!!.getForwardHost(getOriginalHost(serverRequest))
        remotePort = this.proxyRule!!.forwardRule.port

        val clientRequest = DefaultHttpRequest(
                serverRequest.protocolVersion(),
                serverRequest.method(),
                serverRequest.uri())

        clientRequest.headers().add(serverRequest.headers())
        clientRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
        clientRequest.headers().set(HttpHeaderNames.HOST, remoteHost)

        val bootstrap = Bootstrap()
                .group(backendEventLoopGroup)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<Channel>() {
                    override fun initChannel(ch: Channel) {
                        ch.pipeline().addLast(IdleStateHandler(30, 30, 30))
                        if (proxyRule!!.forwardRule.scheme == HttpScheme.HTTPS) {
                            ch.pipeline().addLast(
                                    SslContextBuilder.forClient().clientAuth(ClientAuth.NONE).build().newHandler(ch.alloc(),
                                            remoteHost,
                                            remotePort)
                            )
                        }
                        ch.pipeline().addLast(LoggingHandler())
                        ch.pipeline().addLast(HttpRequestEncoder())
                        ch.pipeline().addLast(HttpResponseDecoder())
                        ch.pipeline().addLast(BackendHandler(ctx.channel() as SocketChannel, proxyRule!!))
                    }
                }).option(ChannelOption.AUTO_READ, false)

        val channelFuture = bootstrap.connect(remoteHost, remotePort)

        channelFuture.addListener {
            if (it.isSuccess) {
                backChn = channelFuture.channel()
                backChn!!.writeAndFlush(clientRequest).addListener {
                    val compositeByteBuf = CompositeByteBuf(ByteBufAllocator.DEFAULT, true, 10)
                    while (msgHolder.isNotEmpty()) {
                        compositeByteBuf.addComponent((msgHolder.poll() as HttpContent).content())
                    }
                    backChn!!.writeAndFlush(compositeByteBuf).addListener {
                        clearMsgHolder()
                        (it as ChannelFuture).channel().read()
                    }

                }
            } else {
                (it as ChannelFuture).channel().close()
            }
        }

    }

    private fun clearMsgHolder() {
        msgHolder.forEach {
            ReferenceCountUtil.safeRelease(it)
        }
        msgHolder.clear()
    }
}