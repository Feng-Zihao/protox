package org.protox.http

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.util.ReferenceCountUtil
import org.protox.Config
import org.protox.getOriginalHost
import org.protox.tryCloseChannel


class FrontendHandler(val config: Config) : SimpleChannelInboundHandler<HttpObject>(false) {

    lateinit var serverRequest: HttpRequest
    lateinit var clientRequest: HttpRequest
    lateinit var remoteHost : String

    var remotePort : Int = 0
    var consumeFirstContent: Boolean = false

    var backChn: Channel? = null

    lateinit var proxyRule: Config.ProxyRule

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        ctx.read()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        ReferenceCountUtil.retain(msg)
        if (msg is HttpRequest) {
            serverRequest = msg

            var matchRule = config.matchProxyRuleOrNull(serverRequest)
            if (matchRule == null) {
                val response = DefaultFullHttpResponse(
                        serverRequest.protocolVersion(),
                        HttpResponseStatus.BAD_GATEWAY
                )
                ctx.writeAndFlush(response).addListener {
                    tryCloseChannel(ctx.channel())
                }
                return
            }

            this.proxyRule = matchRule

            remoteHost = proxyRule.getForwardHost(getOriginalHost(serverRequest))
            remotePort = this.proxyRule.forwardRule.port

            clientRequest = DefaultHttpRequest(
                    serverRequest.protocolVersion(),
                    serverRequest.method(),
                    serverRequest.uri())

            clientRequest.headers().add(serverRequest.headers())
            clientRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
            clientRequest.headers().set(HttpHeaderNames.HOST, remoteHost)

        } else if (msg is HttpContent) {
            if (!consumeFirstContent) {
                val bootstrap = Bootstrap()
                        .group(ctx.channel().eventLoop())
                        .channel(NioSocketChannel::class.java)
                        .handler(object : ChannelInitializer<Channel> () {
                            override fun initChannel(ch: Channel) {
                                if (proxyRule.forwardRule.scheme == HttpScheme.HTTPS) {
                                    ch.pipeline().addLast(
                                            SslContextBuilder.forClient().build().newHandler(ch.alloc(),
                                                    remoteHost,
                                                    remotePort)
                                    )
                                }
                                ch.pipeline().addLast(LoggingHandler())
                                ch.pipeline().addLast(HttpRequestEncoder())
                                ch.pipeline().addLast(HttpResponseDecoder())
                                ch.pipeline().addLast(BackendHandler(ctx.channel() as SocketChannel, proxyRule))
                            }
                        }).option(ChannelOption.AUTO_READ, false)

                val channelFuture = bootstrap.connect("www.jd.com", remotePort)
                println(remoteHost)

                channelFuture.addListener {
                    if (it.isSuccess) {
                        backChn = channelFuture.channel()
                        backChn!!.writeAndFlush(clientRequest).addListener {
                            consumeFirstContent = true
                            flushAndTryReadNext(ctx, msg)
                        }
                    } else {
                        tryCloseChannel(ctx.channel())
                        tryCloseChannel(backChn)
                    }
                }
            } else {
                flushAndTryReadNext(ctx, msg)
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

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        tryCloseChannel(ctx.channel())
        tryCloseChannel(backChn)
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        super.channelInactive(ctx)
        try {
            ReferenceCountUtil.release(serverRequest);
        } catch (e : UninitializedPropertyAccessException) {
            // pass
        }
    }
}