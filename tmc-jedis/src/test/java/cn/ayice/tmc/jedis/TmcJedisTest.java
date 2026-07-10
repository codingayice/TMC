package cn.ayice.tmc.jedis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ayice.tmc.core.TmcClient;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

class TmcJedisTest {

    @Test
    void shouldReadThroughTmcClient() {
        Jedis jedis = mock(Jedis.class);
        TmcClient tmcClient = mock(TmcClient.class);
        when(tmcClient.get("product:1")).thenReturn("cached-value");
        TmcJedis tmcJedis = new TmcJedis(jedis, tmcClient);

        String value = tmcJedis.get("product:1");

        assertEquals("cached-value", value);
        verify(tmcClient).get("product:1");
        verify(jedis, never()).get("product:1");
    }

    @Test
    void shouldInvalidateWhenSetReturnsOk() {
        Jedis jedis = mock(Jedis.class);
        TmcClient tmcClient = mock(TmcClient.class);
        when(jedis.set("product:1", "value-1")).thenReturn("OK");
        TmcJedis tmcJedis = new TmcJedis(jedis, tmcClient);

        String result = tmcJedis.set("product:1", "value-1");

        assertEquals("OK", result);
        verify(tmcClient).invalidate("product:1");
    }

    @Test
    void shouldNotInvalidateWhenSetDoesNotReturnOk() {
        Jedis jedis = mock(Jedis.class);
        TmcClient tmcClient = mock(TmcClient.class);
        when(jedis.set("product:1", "value-1")).thenReturn("FAIL");
        TmcJedis tmcJedis = new TmcJedis(jedis, tmcClient);

        assertEquals("FAIL", tmcJedis.set("product:1", "value-1"));

        verify(tmcClient, never()).invalidate("product:1");
    }

    @Test
    void shouldInvalidateWhenDelDeletesKey() {
        Jedis jedis = mock(Jedis.class);
        TmcClient tmcClient = mock(TmcClient.class);
        when(jedis.del("product:1")).thenReturn(1L);
        TmcJedis tmcJedis = new TmcJedis(jedis, tmcClient);

        Long result = tmcJedis.del("product:1");

        assertEquals(1L, result);
        verify(tmcClient).invalidate("product:1");
    }

    @Test
    void shouldNotInvalidateWhenDelDeletesNothing() {
        Jedis jedis = mock(Jedis.class);
        TmcClient tmcClient = mock(TmcClient.class);
        when(jedis.del("product:1")).thenReturn(0L);
        TmcJedis tmcJedis = new TmcJedis(jedis, tmcClient);

        assertEquals(0L, tmcJedis.del("product:1"));

        verify(tmcClient, never()).invalidate("product:1");
    }

    @Test
    void shouldInvalidateWhenExpireSucceeds() {
        Jedis jedis = mock(Jedis.class);
        TmcClient tmcClient = mock(TmcClient.class);
        when(jedis.expire("product:1", 60)).thenReturn(1L);
        TmcJedis tmcJedis = new TmcJedis(jedis, tmcClient);

        Long result = tmcJedis.expire("product:1", 60);

        assertEquals(1L, result);
        verify(tmcClient).invalidate("product:1");
    }

    @Test
    void shouldNotInvalidateWhenExpireFails() {
        Jedis jedis = mock(Jedis.class);
        TmcClient tmcClient = mock(TmcClient.class);
        when(jedis.expire("product:1", 60)).thenReturn(0L);
        TmcJedis tmcJedis = new TmcJedis(jedis, tmcClient);

        assertEquals(0L, tmcJedis.expire("product:1", 60));

        verify(tmcClient, never()).invalidate("product:1");
    }

    @Test
    void shouldNotInvalidateWhenWriteThrows() {
        Jedis jedis = mock(Jedis.class);
        TmcClient tmcClient = mock(TmcClient.class);
        RuntimeException exception = new RuntimeException("redis down");
        when(jedis.set("product:1", "value-1")).thenThrow(exception);
        TmcJedis tmcJedis = new TmcJedis(jedis, tmcClient);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> tmcJedis.set("product:1", "value-1"));

        assertSame(exception, thrown);
        verify(tmcClient, never()).invalidate("product:1");
    }
}
