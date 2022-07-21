package com.xuqg.testNetty.demo01;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

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
                byte[] messageBody = baos.toByteArray();


// 远端调用可能是并发的，为了区分收到的消息是哪个客户端发来的，
// 给消息加一个ID前缀（requestID+message），  本地要缓存ID，
// 多线程使用连接池 管理连接，
//                发送走IO， out走netty（事件驱动）
//                从 IO 回来 从未来（future）回来，怎么将代理执行到这里
//                睡眠/回调，如何让线程停下来，然后还能继续


                return null;
            }
        });
    }
}

class MyContent {
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