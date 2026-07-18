package cn.ayice.tmc.demo;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Demo API 异常处理器。
 *
 * <p>演示页面需要拿到稳定的 JSON 错误响应，才能把售罄、商品不存在、Redis 异常等情况
 * 展示在页面上。这里不吞掉异常原因，只把它转换成适合前端消费的响应格式。</p>
 */
@RestControllerAdvice
public class DemoExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }
}
