package cn.ayice.tmc.server.hotkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.ayice.tmc.model.HotKey;
import cn.ayice.tmc.model.HotKeySnapshot;
import cn.ayice.tmc.server.config.EtcdProperties;
import cn.ayice.tmc.server.metrics.TmcServerMetrics;
import cn.ayice.tmc.util.EtcdKeys;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * HotKeyPublisher 热点快照发布测试。
 *
 * <p>该测试保护服务端到 etcd 的发布协议：路径必须与 SDK 监听路径一致，
 * 写入内容必须是 HotKeySnapshot JSON，发布异常只能影响指标，不能向调度器外抛。</p>
 */
class HotKeyPublisherTest {

    @Test
    void shouldPublishSnapshotToHotKeysPath() {
        List<String> puts = new ArrayList<>();
        TmcServerMetrics metrics = new TmcServerMetrics();
        HotKeyPublisher publisher = HotKeyPublisher.forTest(
                new EtcdProperties(),
                metrics,
                (path, value) -> puts.add(path + "|" + value),
                path -> {
                }
        );
        HotKeySnapshot snapshot = new HotKeySnapshot(
                "v1",
                "product-service",
                List.of(new HotKey("product-service", "product:1", 100L, 1000L, 300000L))
        );

        publisher.publish(snapshot);

        assertEquals(1, puts.size());
        assertTrue(puts.get(0).startsWith(EtcdKeys.hotKeysPath("product-service") + "|"));
        assertTrue(puts.get(0).contains("\"version\":\"v1\""));
        assertEquals(1L, metrics.snapshot().getHotKeyPublishSucceeded());
    }

    @Test
    void shouldDeleteSnapshotPath() {
        List<String> deletes = new ArrayList<>();
        TmcServerMetrics metrics = new TmcServerMetrics();
        HotKeyPublisher publisher = HotKeyPublisher.forTest(
                new EtcdProperties(),
                metrics,
                (path, value) -> {
                },
                deletes::add
        );

        publisher.delete("product-service");

        assertEquals(List.of(EtcdKeys.hotKeysPath("product-service")), deletes);
        assertEquals(1L, metrics.snapshot().getHotKeyDeleteSucceeded());
    }

    @Test
    void shouldRecordFailureWhenPublishThrows() {
        TmcServerMetrics metrics = new TmcServerMetrics();
        HotKeyPublisher publisher = HotKeyPublisher.forTest(
                new EtcdProperties(),
                metrics,
                (path, value) -> {
                    throw new IllegalStateException("etcd unavailable");
                },
                path -> {
                }
        );

        publisher.publish(new HotKeySnapshot("v1", "product-service", List.of()));

        assertEquals(1L, metrics.snapshot().getHotKeyPublishFailed());
    }
}
