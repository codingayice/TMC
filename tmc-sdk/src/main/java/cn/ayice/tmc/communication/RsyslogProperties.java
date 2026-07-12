package cn.ayice.tmc.communication;

/**
 * rsyslog 写入配置。
 *
 * <p>SDK 只连接本机或应用服务器上的 rsyslog，不直接依赖 Kafka。
 * rsyslog 再负责把 JSON line 转发到 Kafka。</p>
 */
public class RsyslogProperties {

    /**
     * rsyslog 地址。部署在应用服务器本机时通常是 127.0.0.1。
     */
    private String host = "127.0.0.1";

    /**
     * rsyslog TCP 监听端口。
     */
    private int port = 5514;

    /**
     * 建立 TCP 连接的超时时间。
     */
    private int connectTimeoutMillis = 1000;

    /**
     * Socket 写超时。
     */
    private int writeTimeoutMillis = 1000;

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

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        if (connectTimeoutMillis <= 0) {
            throw new IllegalArgumentException("connectTimeoutMillis must be positive");
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getWriteTimeoutMillis() {
        return writeTimeoutMillis;
    }

    public void setWriteTimeoutMillis(int writeTimeoutMillis) {
        if (writeTimeoutMillis <= 0) {
            throw new IllegalArgumentException("writeTimeoutMillis must be positive");
        }
        this.writeTimeoutMillis = writeTimeoutMillis;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
