package cn.ayice.tmc.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TMC 服务端启动入口。
 *
 * <p>服务端负责消费 SDK 上报到 Kafka 的访问事件，并通过定时任务把访问热度映射到
 * 时间轮滑动窗口中，最终产出每个应用的热点 key 快照。</p>
 */
@EnableKafka
@EnableScheduling
@SpringBootApplication(scanBasePackages = "cn.ayice.tmc")
public class TmcServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TmcServerApplication.class, args);
    }
}
