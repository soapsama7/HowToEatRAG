package com.anfioo.howtocook.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class HowToCookApplicationTests {

    @Test
    void testOnCompletionOverride() {
        SseEmitter emitter = new SseEmitter();
        List<String> calls = new ArrayList<>();

        emitter.onCompletion(() -> calls.add("first"));
        emitter.onCompletion(() -> calls.add("second"));

        emitter.complete();

        // 只会执行最后注册的那个
        assertEquals(List.of("second"), calls);
    }

}
