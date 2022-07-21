package com.xuqg.testNetty.demo01;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;


public class MyByteBuf {
    @Test
    public void testByteBuf(){
        ByteBuf b = PooledByteBufAllocator.DEFAULT.directBuffer(8,32);
//        ByteBuf b = PooledByteBufAllocator.DEFAULT.heapBuffer();
//        ByteBuf b = UnooledByteBufAllocator.DEFAULT.heapBuffer();
//        ByteBuf b = ByteBufAllocator.DEFAULT.buffer(4,16);
        UnpooledByteBufAllocator.DEFAULT.directBuffer();

        print(b);
        b.writeBytes(new byte[]{0,1,2,3});
        print(b);
        b.writeBytes(new byte[]{0,1,2,3});
        print(b);
        b.writeBytes(new byte[]{0,1,2,3});
        print(b);
        b.writeBytes(new byte[]{0,1,2,3});
        print(b);

    }

    public static void print(ByteBuf buf){
        System.out.println("isReadable : " + buf.isReadable());
        System.out.println("readerIndex : " + buf.readerIndex());
        System.out.println("readableBytes : "+buf.readableBytes());
        System.out.println("isWritable : " + buf.isWritable());
        System.out.println("writableBytes : " + buf.writableBytes());
        System.out.println("capacity : " + buf.capacity());
        System.out.println("maxCapacity : " + buf.maxCapacity());
        System.out.println("isDirect : " + buf.isDirect());
        System.out.println("-------------");
    }

    /*
    * 客户端
    * 连接别人
    * 1，主动发送数据
    * 2，别人什么时候给我发数据
    * 希望连接不阻塞，有事件了就去处理
    * */
    @Test
    public void loopExecutor() throws IOException {
        NioEventLoopGroup selector = new NioEventLoopGroup(2);
        selector.execute(()->{
            for(;;){

            System.out.println("hello world 1");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            }
        });

        selector.execute(()->{
            for(;;){

                System.out.println("hello world 2");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        System.in.read();
    }

    @Test
    public void clientMode() throws InterruptedException {
        NioEventLoopGroup thread = new NioEventLoopGroup(1);
// 客户端模式
        NioSocketChannel client = new NioSocketChannel();
        thread.register(client);
        ChannelPipeline pipeline = client.pipeline();
        pipeline.addLast(new MyInHandler());
//        react 异步的特征
        ChannelFuture connect = client.connect(new InetSocketAddress("192.168.18.131", 9999));
        ChannelFuture sync = connect.sync();
        ByteBuf buf = Unpooled.copiedBuffer("hello world".getBytes());
        ChannelFuture send = client.writeAndFlush(buf);
        send.sync();

        sync.channel().closeFuture().sync();
        System.out.println("client write over");

    }

    @Test
    public void nettyServerTest() throws Exception {
        NioEventLoopGroup serverGroup = new NioEventLoopGroup(1);
        NioServerSocketChannel server = new NioServerSocketChannel();
        serverGroup.register(server);
        System.out.println("server start");
// 不定时的会有连接进来。。响应式
        ChannelPipeline pipeline = server.pipeline();
        pipeline.addLast(new MyAcceptHandler(serverGroup,new ChannelInit()));// accept接收客户端，注册到selector

        ChannelFuture bind = server.bind(new InetSocketAddress("192.168.3.7",8888));
        bind.sync().channel().closeFuture().sync();
        System.out.println("server close");
    }

}

//这个类是引荐人角色，负责把客户端定义的handler注册到pipeline就完事了，如果不用它，myinhandler就得设计成单例
@ChannelHandler.Sharable
class ChannelInit extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        Channel client = ctx.channel();
        ChannelPipeline pipeline = client.pipeline();
        pipeline.addLast(new MyInHandler());
//        过河拆桥,借用ChannelInit 把MyInHandler 加进通道，用完就没有用了
        ctx.pipeline().remove(this);

    }
}

/*
* 读写数据的Handler是用户自己实现的，目前是acceptHandler的属性，不能让用户放弃属性的操作
* @ChannelHandler.Sharable 不应该强压给coder（客户端）
* */
//@ChannelHandler.Sharable
class MyInHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel register");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel active");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        System.out.println("channel read");
        ByteBuf buf = (ByteBuf) msg;
//        readCharSequence()会移动 Bytebuf 的指针，getCharSequence()不移动指针，指针还在原来的位置可以重复读取
//        CharSequence result = buf.readCharSequence(buf.readableBytes(), CharsetUtil.UTF_8);
        CharSequence result = buf.getCharSequence(0,buf.readableBytes(), CharsetUtil.UTF_8);
        System.out.println(result);
        ctx.writeAndFlush(buf);


    }
}

class MyAcceptHandler extends ChannelInboundHandlerAdapter{
    private EventLoopGroup selector;
    private ChannelHandler handler;
    public MyAcceptHandler(EventLoopGroup serverGroup, ChannelHandler myInHandler) {
        this.selector = serverGroup;
        this.handler = myInHandler;

    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("server register");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        服务端实时监听listen socket 接收accept 客户端client
        SocketChannel client = (SocketChannel) msg;
//        响应式handler
        ChannelPipeline pipeline = client.pipeline();
//        把链接的客户端加入到通道
        pipeline.addLast(handler);

//        注册，得到了客户端也需要注册等事情
        selector.register(client);

    }
}