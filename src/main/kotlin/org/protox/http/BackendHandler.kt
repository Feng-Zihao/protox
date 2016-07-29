package org.protox.http

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.ReferenceCountUtil
import org.protox.Config
import org.protox.tryCloseChannel
import org.slf4j.LoggerFactory

class BackendHandler(val frontChn: SocketChannel, val proxyRule: Config.ProxyRule) : ChannelInboundHandlerAdapter() {

    val LOGGER = LoggerFactory.getLogger(BackendHandler::class.java)

    lateinit var serverResponse: HttpResponse
    var clientResponse: HttpResponse? = null
    @Volatile var consumeFirstContent: Boolean = false

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        ctx.read()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        ReferenceCountUtil.retain(msg)
        if (msg is HttpResponse) {
            clientResponse = msg
            serverResponse = DefaultHttpResponse(
                    msg.protocolVersion(), msg.status(), msg.headers()
            )

            serverResponse.headers()[HttpHeaderNames.CONNECTION] = HttpHeaderValues.CLOSE
            if (serverResponse.headers()[HttpHeaderNames.LOCATION] != null) {
                serverResponse.headers()[HttpHeaderNames.LOCATION] = proxyRule.getReturnedLocation(
                        serverResponse.headers()[HttpHeaderNames.LOCATION]
                )
            }
        } else if (msg is HttpContent) {
            if (!consumeFirstContent) {
                frontChn.writeAndFlush(serverResponse).addListener {
                    if (it.isSuccess) {
                        consumeFirstContent = true
                        flushAndTryReadNextOrClose(ctx, msg)
                    } else {
//                        tryCloseChannel(frontChn)
                        tryCloseChannel(ctx.channel())
                    }
                }
            } else {
                flushAndTryReadNextOrClose(ctx, msg)
            }
        }
    }

//    override fun channelReadComplete(ctx: ChannelHandlerContext?) {
//        LOGGER.info("ReadComplete")
//    }

    private fun flushAndTryReadNextOrClose(ctx: ChannelHandlerContext, msg: HttpContent) {
        frontChn.writeAndFlush(msg).addListener {
            ReferenceCountUtil.release(msg)
            if (it.isSuccess) {
                if (msg !is LastHttpContent) {
                    ctx.read()
                } else {
                    tryCloseChannel(ctx.channel())
//                    tryCloseChannel(frontChn)
                }
            } else {
                tryCloseChannel(ctx.channel())
//                tryCloseChannel(frontChn)
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        LOGGER.error("{}", cause)
        tryCloseChannel(ctx.channel())
//        tryCloseChannel(frontChn)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        if (clientResponse != null) {
            ReferenceCountUtil.release(clientResponse)
        }
        super.channelInactive(ctx)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            LOGGER.info("{}", evt)
            tryCloseChannel(ctx.channel())
//            tryCloseChannel(frontChn)
        }
    }
}