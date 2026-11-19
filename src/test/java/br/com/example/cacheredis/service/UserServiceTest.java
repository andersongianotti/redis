package br.com.example.cacheredis.service;

import br.com.example.cacheredis.model.User;
import br.com.example.cacheredis.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, redisTemplate, objectMapper, 5L);
        user = new User("Anderson", "ander@teste.com", "11999999999");
        user.setId("abc123");
    }

    @Test
    void findById_shouldReturnCachedUser_onCacheHit() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String json = objectMapper.writeValueAsString(user);
        when(valueOperations.get("user:abc123")).thenReturn(json);

        Optional<User> result = userService.findById("abc123");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Anderson");
        assertThat(result.get().getEmail()).isEqualTo("ander@teste.com");
        verify(userRepository, never()).findById(any());
    }

    @Test
    void findById_shouldFetchFromMongoAndCache_onCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:abc123")).thenReturn(null);
        when(userRepository.findById("abc123")).thenReturn(Optional.of(user));

        Optional<User> result = userService.findById("abc123");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("abc123");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(),
                eq(Duration.ofMinutes(5)));
        assertThat(keyCaptor.getValue()).isEqualTo("user:abc123");
        assertThat(valueCaptor.getValue()).contains("ander@teste.com");
    }

    @Test
    void findById_shouldReturnEmpty_whenNotInCacheNorMongo() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:inexistente")).thenReturn(null);
        when(userRepository.findById("inexistente")).thenReturn(Optional.empty());

        Optional<User> result = userService.findById("inexistente");

        assertThat(result).isEmpty();
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void findById_shouldInvalidateCorruptedCache() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:abc123")).thenReturn("{json-quebrado");
        when(userRepository.findById("abc123")).thenReturn(Optional.of(user));

        Optional<User> result = userService.findById("abc123");

        assertThat(result).isPresent();
        verify(redisTemplate).delete("user:abc123");
    }

    @Test
    void create_shouldSaveUserInMongo() {
        User newUser = new User("Maria", "maria@teste.com", "11888888888");
        User saved = new User("Maria", "maria@teste.com", "11888888888");
        saved.setId("def456");
        when(userRepository.save(newUser)).thenReturn(saved);

        User result = userService.create(newUser);

        assertThat(result.getId()).isEqualTo("def456");
        verify(userRepository).save(newUser);
    }

    @Test
    void evict_shouldDeleteCacheKey() {
        userService.evict("abc123");

        verify(redisTemplate).delete("user:abc123");
    }
}
