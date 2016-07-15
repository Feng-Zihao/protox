package org.protox.http

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.ssl.SslContextBuilder
import io.netty.util.ReferenceCountUtil
import org.protox.Config
import org.protox.tryCloseChannel


class FrontendHandler(val config: Config) : SimpleChannelInboundHandler<HttpObject>(false) {

    lateinit var serverRequest: HttpRequest
    lateinit var clientRequest: HttpRequest
    var consumeFirstContent: Boolean = false

    var backChn: Channel? = null

    lateinit var matchRule: Config.Rule

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        ctx.read()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        ReferenceCountUtil.retain(msg)
        if (msg is HttpRequest) {
            serverRequest = msg

            var rule = config.matchRuleOrNull(serverRequest)
            if (rule == null) {
                val response = DefaultFullHttpResponse(
                        serverRequest!!.protocolVersion(),
                        HttpResponseStatus.BAD_GATEWAY
                )
                ctx.writeAndFlush(response).addListener {
                    tryCloseChannel(ctx.channel())
                }
                return
            }

            matchRule = rule

            val host = matchRule.forwardUri.host;
            val port = matchRule.forwardPort;

            clientRequest = DefaultHttpRequest(
                    serverRequest.protocolVersion(),
                    serverRequest.method(),
                    serverRequest.uri())

            clientRequest.headers().add(serverRequest.headers())
            clientRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
            clientRequest.headers().set(HttpHeaderNames.HOST, host)

        } else if (msg is HttpContent) {
            if (!consumeFirstContent) {
                val bootstrap = Bootstrap()
                        .group(ctx.channel().eventLoop())
                        .channel(NioSocketChannel::class.java)
                        .handler(object : ChannelInitializer<Channel> () {
                            override fun initChannel(ch: Channel) {
                                if (matchRule.forwardHttps) {
                                    ch.pipeline().addLast(
                                            SslContextBuilder.forClient().build().newHandler(ch.alloc(),
                                                    matchRule.forwardHost,
                                                    matchRule.forwardPort)
                                    );
                                }
                                ch.pipeline().addLast(HttpRequestEncoder())
                                ch.pipeline().addLast(HttpResponseDecoder())
                                ch.pipeline().addLast(BackendHandler(ctx.channel() as SocketChannel))
                            }
                        }).option(ChannelOption.AUTO_READ, false);

                val channelFuture = bootstrap.connect(matchRule.forwardHost, matchRule.forwardPort)

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