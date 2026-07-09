package cn.ayice.tmc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JsonUtilsTest {

    @Test
    void toJsonShouldOmitNullFields() {
        SampleEvent event = new SampleEvent("tmc-demo", "product:10001", null);

        String json = JsonUtils.toJson(event);

        assertTrue(json.contains("\"appName\":\"tmc-demo\""));
        assertTrue(json.contains("\"key\":\"product:10001\""));
        assertFalse(json.contains("optionalValue"));
    }

    @Test
    void fromJsonShouldIgnoreUnknownFields() {
        String json = "{\"appName\":\"tmc-demo\",\"key\":\"product:10001\",\"unknown\":\"ignored\"}";

        SampleEvent event = JsonUtils.fromJson(json, SampleEvent.class);

        assertEquals("tmc-demo", event.getAppName());
        assertEquals("product:10001", event.getKey());
    }

    @Test
    void toJsonLineShouldAppendSingleLineBreak() {
        SampleEvent event = new SampleEvent("tmc-demo", "product:10001", null);

        String jsonLine = JsonUtils.toJsonLine(event);

        assertTrue(jsonLine.endsWith("\n"));
        assertFalse(jsonLine.endsWith("\n\n"));
        JsonUtils.fromJson(jsonLine.trim(), SampleEvent.class);
    }

    public static class SampleEvent {
        private String appName;
        private String key;
        private String optionalValue;

        public SampleEvent() {
        }

        public SampleEvent(String appName, String key, String optionalValue) {
            this.appName = appName;
            this.key = key;
            this.optionalValue = optionalValue;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getOptionalValue() {
            return optionalValue;
        }

        public void setOptionalValue(String optionalValue) {
            this.optionalValue = optionalValue;
        }
    }
}
