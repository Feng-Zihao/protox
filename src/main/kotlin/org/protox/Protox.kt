package org.protox

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import org.protox.http.FrontendHandler

var backendEventLoopGroup = NioEventLoopGroup(1)

val bossGroup = NioEventLoopGroup(1)
val workerGroup = NioEventLoopGroup(1)


fun main(args: Array<String>) {

    var serverBootstrap = ServerBootstrap()

    try {
        serverBootstrap
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childHandler(object : ChannelInitializer<SocketChannel> () {
                    override fun initChannel(ch: SocketChannel) {
//                        ch.pipeline().addLast(LoggingHandler())
                        ch.pipeline().addLast(HttpRequestDecoder())
                        ch.pipeline().addLast(HttpResponseEncoder())
                        ch.pipeline().addLast(FrontendHandler())
                    }
                })
                .childOption(ChannelOption.AUTO_READ, false)
                .bind("0.0.0.0", 8080).sync().channel().closeFuture().sync()

    } finally {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
    }
}
