package cn.ayice.tmc.jedis.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

class JedisRemoteCacheClientTest {

    @Test
    void shouldReadValueFromJedis() {
        Jedis jedis = mock(Jedis.class);
        when(jedis.get("product:1")).thenReturn("value-1");
        JedisRemoteCacheClient remoteCacheClient = new JedisRemoteCacheClient(jedis);

        String value = remoteCacheClient.get("product:1");

        assertEquals("value-1", value);
        verify(jedis).get("product:1");
    }

    @Test
    void shouldReturnNullWhenJedisReturnsNull() {
        Jedis jedis = mock(Jedis.class);
        when(jedis.get("missing")).thenReturn(null);
        JedisRemoteCacheClient remoteCacheClient = new JedisRemoteCacheClient(jedis);

        assertNull(remoteCacheClient.get("missing"));
    }

    @Test
    void shouldPropagateJedisException() {
        Jedis jedis = mock(Jedis.class);
        RuntimeException exception = new RuntimeException("redis down");
        when(jedis.get("product:1")).thenThrow(exception);
        JedisRemoteCacheClient remoteCacheClient = new JedisRemoteCacheClient(jedis);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> remoteCacheClient.get("product:1"));

        assertSame(exception, thrown);
    }
}
