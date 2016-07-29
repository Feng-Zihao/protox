package org.protox.http

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.ChannelFutureListener.CLOSE
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import io.netty.util.ReferenceCountUtil
import org.protox.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*


class FrontendHandler(val config: Config) : ChannelDuplexHandler() {

    var LOGGER: Logger = LoggerFactory.getLogger(FrontendHandler::class.java)

    var backChn: Channel? = null

    var proxyRule: Config.ProxyRule? = null

    val msgHolder: Queue<HttpObject> = ArrayDeque(10)

    override fun channelActive(ctx: ChannelHandlerContext) {
        msgHolder.clear()
        ctx.read()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        ReferenceCountUtil.retain(msg)
        msgHolder.add(msg as HttpObject)
    }


    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        var msg = msgHolder.peek()

        if (msg is HttpRequest) {
            var serverRequest = msgHolder.poll() as HttpRequest
            var matchRule = config.matchProxyRuleOrNull(serverRequest)
            if (matchRule == null) {
                ctx.writeAndFlush(BAD_GATEWAY_RESPONSE).addListener {
                    clearMsgHolder()
                    (it as ChannelFuture).channel().close()
                }
                return
            }

            this.proxyRule = matchRule
            launchBackendRequest(ctx, serverRequest)
        } else {
            flushMsgHolderThenTryRead(ctx)
        }

    }

    private fun flushMsgHolderThenTryRead(ctx: ChannelHandlerContext) {
        if (msgHolder.isNotEmpty()) {
            val msg = msgHolder.poll()
            backChn!!.writeAndFlush(msg).addListener {
                if (msg is LastHttpContent) {
                    return@addListener
                }
                ReferenceCountUtil.safeRelease(msg)
                flushMsgHolderThenTryRead(ctx)
            }
        } else {
            ctx.channel().read()
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        clearMsgHolder()
        LOGGER.debug("{}", frontendCounter.decrementAndGet())
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        super.exceptionCaught(ctx, cause)
        tryCloseChannel(ctx.channel())
        tryCloseChannel(backChn)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            LOGGER.debug("{}", evt.state())
            ctx.channel().writeAndFlush(REQUEST_TIMEOUT_RESPONSE).addListener {
                ctx.channel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(CLOSE)
            }
            tryCloseChannel(backChn)
        }
    }

    private fun launchBackendRequest(ctx: ChannelHandlerContext, serverRequest: HttpRequest) {
        LOGGER.debug("{}", frontendCounter.incrementAndGet())

        val remoteHost = proxyRule!!.getForwardHost(getOriginalHost(serverRequest))
        val remotePort = this.proxyRule!!.forwardRule.port

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
            backChn = channelFuture.channel()
            backChn!!.writeAndFlush(clientRequest).addListener {
                flushMsgHolderThenTryRead(ctx)
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