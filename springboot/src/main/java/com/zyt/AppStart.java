/*
配置启动类，基于这个类中的main方法就可以运行springboot服务器默认情况下占用8080端口，协议http，ip地址localhost
我们可以通过网址：http://localhost:8080/资源地址来访问服务器中的资源
资源地址：需要我们自己在服务器开发中进行配置，这样的地址也可以叫做一个接口
*/

package com.zyt;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// 这个类用于启动springboot服务器---springboot项目是一个服务器而非普通的java文件
// @SpringBootApplication注解的作用：标识当前类是一个springboot项目的启动类
// 今后基本上所有的java代码都要放在这个类的同级目录或者子级目录中 --- 才可以被扫描识别
@SpringBootApplication
@EnableScheduling
// 扫描mapper接口所在的包，自动生成代理对象【实现类对象】
@MapperScan("com.zyt.mapper")
public class AppStart {
    // main方法启动服务器
    public static void main(String[] args) {
        // run方法启动服务器，参数为当前启动类、args参数
        SpringApplication.run(AppStart.class, args);
    }
}
