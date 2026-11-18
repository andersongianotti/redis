package br.com.example.cacheredis.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CacheController {

    private final StringRedisTemplate redisTemplate;

    public CacheController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @DeleteMapping("/cache/{key}")
    public ResponseEntity<Void> evict(@PathVariable String key) {
        redisTemplate.delete(key);
        return ResponseEntity.noContent().build();
    }
}
