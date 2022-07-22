package com.xuqg.testNetty.demo01;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1 假设需求
 * 2 客户端、服务器段通信，链接数量，拆包？
 * 3 动态代理，序列化，协议封装
 * 4 连接池
 * 5 远程调用，就像调用本地方法一样去调用远程的方法
 */
public class MyRPCTest {
    public void get(){
        Car car = proxyGet(Car.class) ;
//            动态代理实现
            car.run("car is running ... ");

        Plane plane = proxyGet(Plane.class);
//            动态代理实现
            plane.fly("plane is flying ... ");

    }

    public static <T>T proxyGet(Class<T> interfaceInfo) {
// 可以实现各个版本的动态代理
        ClassLoader loader = interfaceInfo.getClassLoader();
        Class<?>[] methodInfo = {interfaceInfo};
        return (T) Proxy.newProxyInstance(loader, methodInfo, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
// 调用服务、方法、参数 --> 封装成的message，[content]
                String name = interfaceInfo.getName();
                String methodName = method.getName();;
                Class<?>[] parameterTypes = method.getParameterTypes();
                MyContent content = new MyContent();
                content.setName(name);
                content.setArgs(args);
                content.setMethodName(methodName);
                content.setParameterTypes(parameterTypes);
//                封装消息
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(content);
                byte[] msgBody = baos.toByteArray();
// 协议：【head：id、body长度、】【msgbody】
                MyHeader myHeader = createHeader(msgBody);
//                清空数据
                baos.reset();
                oos = new ObjectOutputStream(baos);
                oos.writeObject(myHeader);
                byte[] msgHeader = baos.toByteArray();




// 远端调用可能是并发的，为了区分收到的消息是哪个客户端发来的，
// 给消息加一个ID前缀（requestID+message），  本地要缓存ID，
// 连接池：多线程使用连接池 管理连接，
                ClientFactory factory = new ClientFactory();
                factory.getClient(new InetSocketAddress("localhost",9999));



//                发送走IO， out走netty（事件驱动）
//                从 IO 回来 从未来（future）回来，怎么将代理执行到这里
//                睡眠/回调，如何让线程停下来，然后还能继续


                return null;
            }
        });
    }

// 创建一个协议头
    private static MyHeader createHeader(byte[] msg) {
        MyHeader header = new MyHeader();
        int size = msg.length;
        int flag = 0x10101010;
        long requestID = UUID.randomUUID().getLeastSignificantBits();
        header.setDatalen(size);
        header.setFlag(flag);
        header.setRequestID(requestID);

        return header;
    }
}

// 协议内容
class MyContent implements Serializable {
    String name;
    String methodName;
    Class<?>[] parameterTypes;
    Object[] args;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterType) {
        this.parameterTypes = parameterType;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }
}

interface Car{
    void run(String info);
}

interface Plane{
    void fly(String info);
}
// 协议头类
class MyHeader implements Serializable{
// 通信协议
    /*
    * 三个部分
    * 1  二进制位值
    * 2 UUID：requestID
    * 3 DATA_LEN
    * */
    int flag;// 32位可以做很多事情
    long requestID;
    long datalen;

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public long getRequestID() {
        return requestID;
    }

    public void setRequestID(long requestID) {
        this.requestID = requestID;
    }

    public long getDatalen() {
        return datalen;
    }

    public void setDatalen(long datalen) {
        this.datalen = datalen;
    }
}
// 连接池类
class ClientPool{
//    客户端数组接收客户端连接
    NioSocketChannel[] clients;
    Object[] lock;// 锁
//    构造器中初始化
    public ClientPool(int size){
        clients = new NioSocketChannel[size];
        lock = new Object[size];//锁可以并且必须初始化
        for (Object o : lock) {
            o = new Object();
        }
    }
}

// 单例模式
class ClientFactory{
    int poolSize = 1;
    Random rand;
    NioEventLoopGroup clientWorker;

    private static final ClientFactory factory;
    static{
        factory = new ClientFactory();
    }
    public static ClientFactory getFactory(){
        return factory;
    }
// 一个consumer可以连接多个provider，每一个provider都有自己的pool，K\V形式，K是provider的ip，v是clientPool
    ConcurrentHashMap<InetSocketAddress,ClientPool> outboxs = new ConcurrentHashMap<>();
    public synchronized NioSocketChannel getClient(InetSocketAddress address){
        ClientPool clientPool = outboxs.get(address);
        if(clientPool == null){
            outboxs.putIfAbsent(address,new ClientPool(poolSize));
            clientPool = outboxs.get(address);
        }
        int i = rand.nextInt(poolSize);
//        如果连接池不为空并且存活就返回
        if(clientPool.clients[i] != null && clientPool.clients[i].isActive()){
            return clientPool.clients[i];
        }
        synchronized (clientPool.lock[i]){
            return clientPool.clients[i] = create(address);
        }

    }

    private NioSocketChannel create(InetSocketAddress address) {
//        基于netty的客户端创建方式
        clientWorker = new NioEventLoopGroup(1);
        Bootstrap bs = new Bootstrap();
        ChannelFuture connect = bs.group(clientWorker)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        ChannelPipeline pipeline = nioSocketChannel.pipeline();
                        pipeline.addLast(new ClientResponse());
                    }
                }).connect(address);
        try {
            NioSocketChannel client = (NioSocketChannel) connect.sync().channel();
            return client;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        return null;

    }

}
// consumer
class ClientResponse extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        if(buf.readableBytes() >= 160){
            byte[] bytes = new byte[160];
            buf.readBytes(bytes);
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            ObjectInputStream oin = new ObjectInputStream(in);
            MyHeader header = (MyHeader) oin.readObject();
            System.out.println(header.getDatalen());
            if(buf.readableBytes() >= header.getDatalen()){
                byte[] data = new byte[(int) header.getDatalen()];
                buf.readBytes(data);
                ByteArrayInputStream din = new ByteArrayInputStream(data);
                ObjectInputStream doin = new ObjectInputStream(din);
                MyContent content = (MyContent) doin.readObject();
                System.out.println(content.getName());
                System.out.println(header.requestID);
//                TODO:有了requestID 才能找到对应的线程，根据它继续处理业务

            }
        }

    }
}