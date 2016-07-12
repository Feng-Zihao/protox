package org.protox.http

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil
import org.protox.backendEventLoopGroup
import org.protox.tryCloseChannel

class FrontendHandler : SimpleChannelInboundHandler<HttpObject>(false) {

    var serverRequest: HttpRequest? = null
    var clientRequest: HttpRequest? = null
    var consumeFirstContent: Boolean = false

    var backChn: Channel? = null

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        ctx.read()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        ReferenceCountUtil.retain(msg)
        if (msg is HttpRequest) {
            serverRequest = msg
            clientRequest = DefaultHttpRequest(
                    serverRequest!!.protocolVersion(),
                    serverRequest!!.method(),
                    serverRequest!!.uri())

            clientRequest!!.headers().add(serverRequest!!.headers())
            clientRequest!!.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)

        } else if (msg is HttpContent) {
            if (!consumeFirstContent) {
                val bootstrap = Bootstrap()
                        .group(backendEventLoopGroup)
                        .channel(NioSocketChannel::class.java)
                        .handler(object : ChannelInitializer<Channel> () {
                            override fun initChannel(ch: Channel) {
//                                ch.pipeline().addLast(LoggingHandler())
                                ch.pipeline().addLast(HttpRequestEncoder())
                                ch.pipeline().addLast(HttpResponseDecoder())
                                ch.pipeline().addLast(BackendHandler(ctx.channel() as SocketChannel))
                            }
                        }).option(ChannelOption.AUTO_READ, false);

                val channelFuture = bootstrap.connect("114.67.23.99", 8123)

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
//        super.exceptionCaught(ctx, cause)
        tryCloseChannel(ctx.channel())
        tryCloseChannel(backChn)
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        super.channelInactive(ctx)
        if (serverRequest != null) {
            ReferenceCountUtil.release(serverRequest);
        }
    }
}