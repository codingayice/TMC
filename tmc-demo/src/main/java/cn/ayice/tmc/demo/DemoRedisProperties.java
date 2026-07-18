package cn.ayice.tmc.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo Redis 连接配置。
 *
 * <p>这里单独使用 {@code tmc.demo.redis.*}，是为了把演示业务自己的 Redis 地址
 * 和 SDK 的 rsyslog、etcd 通信配置区分开。Demo 业务 Redis 存放商品详情，
 * TMC SDK 则通过另外的配置完成访问上报和热点监听。</p>
 */
@ConfigurationProperties(prefix = "tmc.demo.redis")
public class DemoRedisProperties {

    /**
     * Redis 主机地址。部署到服务器时可以通过环境变量覆盖。
     */
    private String host = "127.0.0.1";

    /**
     * Redis 端口。
     */
    private int port = 6379;

    /**
     * Redis 连接和读写超时时间，单位毫秒。
     */
    private int timeoutMillis = 2000;

    /**
     * Redis 密码。为空时使用无密码连接。
     */
    private String password;

    /**
     * Redis database 编号，Demo 默认使用 0 号库。
     */
    private int database = 0;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        if (isBlank(host)) {
            throw new IllegalArgumentException("host must not be blank");
        }
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        this.port = port;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        this.timeoutMillis = timeoutMillis;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        if (database < 0) {
            throw new IllegalArgumentException("database must not be negative");
        }
        this.database = database;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
