package br.com.example.cacheredis.service;

import br.com.example.cacheredis.model.User;
import br.com.example.cacheredis.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String KEY_PREFIX = "user:";

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlMinutes;

    public UserService(UserRepository userRepository,
                       StringRedisTemplate redisTemplate,
                       ObjectMapper objectMapper,
                       @Value("${cache.ttl-minutes}") long ttlMinutes) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlMinutes = ttlMinutes;
    }

    public Optional<User> findById(String id) {
        String key = KEY_PREFIX + id;

        // 1) Tenta o Redis primeiro
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            log.debug("Cache HIT para {}", key);
            try {
                return Optional.of(objectMapper.readValue(cached, User.class));
            } catch (JsonProcessingException e) {
                log.warn("Falha ao desserializar cache de {}, invalidando", key);
                redisTemplate.delete(key);
            }
        }

        // 2) Cache MISS -> busca no MongoDB
        log.debug("Cache MISS para {}, consultando MongoDB", key);
        Optional<User> user = userRepository.findById(id);

        // 3) Se achou, grava no Redis com TTL
        user.ifPresent(u -> cache(key, u));

        return user;
    }

    public User create(User user) {
        User saved = userRepository.save(user);
        log.info("Usuário criado no MongoDB: {}", saved.getId());
        return saved;
    }

    private void cache(String key, User user) {
        try {
            String json = objectMapper.writeValueAsString(user);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(ttlMinutes));
        } catch (JsonProcessingException e) {
            log.error("Falha ao serializar usuário para cache", e);
        }
    }

    public void evict(String id) {
        String key = KEY_PREFIX + id;
        redisTemplate.delete(key);
        log.info("Cache invalidado para {}", key);
    }

    public String keyFor(String id) {
        return KEY_PREFIX + id;
    }
}
