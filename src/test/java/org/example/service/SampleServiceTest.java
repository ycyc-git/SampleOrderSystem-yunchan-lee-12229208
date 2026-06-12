package org.example.service;

import org.example.domain.Sample;
import org.example.repository.SampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SampleServiceTest {

    @TempDir
    Path tempDir;

    private SampleService service;

    @BeforeEach
    void setUp() {
        SampleRepository repo =
                new SampleRepository(tempDir.resolve("samples.json").toString());
        service = new SampleService(repo);
    }

    @Test
    void register_persistsSample() {
        Sample s = service.register("S-001", "실리콘 웨이퍼", 0.5, 0.92, 100);
        assertEquals("S-001", s.getId());
        assertEquals(1, service.getAll().size());
    }

    @Test
    void register_throwsOnBlankId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("", "이름", 0.5, 0.9, 0));
        assertEquals("시료 ID를 입력하세요.", ex.getMessage());
    }

    @Test
    void register_throwsOnNullId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.register(null, "이름", 0.5, 0.9, 0));
    }

    @Test
    void register_throwsOnDuplicateId() {
        service.register("S-001", "첫 번째", 0.5, 0.9, 0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("S-001", "두 번째", 0.5, 0.9, 0));
        assertEquals("이미 등록된 시료 ID입니다.", ex.getMessage());
    }

    @Test
    void register_throwsOnBlankName() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("S-001", "", 0.5, 0.9, 0));
        assertEquals("시료명을 입력하세요.", ex.getMessage());
    }

    @Test
    void register_throwsOnZeroAvgTime() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("S-001", "이름", 0.0, 0.9, 0));
        assertEquals("평균 생산시간은 0보다 커야 합니다.", ex.getMessage());
    }

    @Test
    void register_throwsOnNegativeAvgTime() {
        assertThrows(IllegalArgumentException.class,
                () -> service.register("S-001", "이름", -1.0, 0.9, 0));
    }

    @Test
    void register_throwsOnZeroYield() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("S-001", "이름", 0.5, 0.0, 0));
        assertEquals("수율은 0 초과 1 이하의 값을 입력하세요.", ex.getMessage());
    }

    @Test
    void register_throwsOnYieldAboveOne() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("S-001", "이름", 0.5, 1.01, 0));
        assertEquals("수율은 0 초과 1 이하의 값을 입력하세요.", ex.getMessage());
    }

    @Test
    void register_allowsYieldEqualsOne() {
        assertDoesNotThrow(() -> service.register("S-001", "이름", 0.5, 1.0, 0));
    }

    @Test
    void register_throwsOnNegativeInitialStock() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register("S-001", "이름", 0.5, 0.9, -1));
        assertEquals("초기 재고는 0 이상이어야 합니다.", ex.getMessage());
    }

    @Test
    void register_allowsZeroInitialStock() {
        Sample s = service.register("S-001", "이름", 0.5, 0.9, 0);
        assertEquals(0, s.getStock());
    }

    @Test
    void getAll_returnsAllSamples() {
        service.register("S-001", "A", 0.5, 0.9, 10);
        service.register("S-002", "B", 0.3, 0.8, 20);
        List<Sample> all = service.getAll();
        assertEquals(2, all.size());
    }

    @Test
    void findById_returnsRegisteredSample() {
        service.register("S-001", "테스트", 0.5, 0.9, 10);
        assertTrue(service.findById("S-001").isPresent());
        assertTrue(service.findById("X-999").isEmpty());
    }
}
