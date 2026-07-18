package cn.ayice.tmc;

import cn.ayice.tmc.demo.DemoRedisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * TMC 限时抢购 Demo 启动类。
 *
 * <p>该模块用于演示商品抢购场景下的透明多级缓存链路：页面高频读取商品详情，
 * 业务代码通过 TmcJedis 访问 Redis，SDK 上报访问事件，tmc-server 识别热点后
 * 通过 etcd 下发热点快照，客户端再把热点商品详情放入本地缓存。库存不进入
 * TMC 本地缓存，避免强一致数据被本地缓存放大一致性风险。</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(DemoRedisProperties.class)
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
