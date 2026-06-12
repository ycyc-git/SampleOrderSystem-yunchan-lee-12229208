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

    // ── register ──────────────────────────────────────────────────

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
        assertEquals(0, service.register("S-001", "이름", 0.5, 0.9, 0).getStock());
    }

    @Test
    void getAll_returnsAllSamples() {
        service.register("S-001", "A", 0.5, 0.9, 10);
        service.register("S-002", "B", 0.3, 0.8, 20);
        assertEquals(2, service.getAll().size());
    }

    @Test
    void findById_returnsRegisteredSample() {
        service.register("S-001", "테스트", 0.5, 0.9, 10);
        assertTrue(service.findById("S-001").isPresent());
        assertTrue(service.findById("X-999").isEmpty());
    }

    // ── searchById ────────────────────────────────────────────────

    @Test
    void searchById_returnsPartialIdMatch() {
        service.register("S-001", "A", 0.5, 0.9, 10);
        service.register("S-002", "B", 0.3, 0.8, 20);
        service.register("X-001", "C", 0.4, 0.7, 30);

        List<Sample> result = service.searchById("S-0");
        assertEquals(2, result.size());
    }

    @Test
    void searchById_isCaseInsensitive() {
        service.register("S-001", "A", 0.5, 0.9, 10);
        assertEquals(1, service.searchById("s-001").size());
    }

    @Test
    void searchById_emptyKeyword_returnsAll() {
        service.register("S-001", "A", 0.5, 0.9, 10);
        service.register("S-002", "B", 0.3, 0.8, 20);
        assertEquals(2, service.searchById("").size());
    }

    @Test
    void searchById_returnsEmpty_forNoMatch() {
        service.register("S-001", "A", 0.5, 0.9, 10);
        assertTrue(service.searchById("X-999").isEmpty());
    }

    // ── searchByName ──────────────────────────────────────────────

    @Test
    void searchByName_returnsPartialNameMatch() {
        service.register("S-001", "실리콘 웨이퍼", 0.5, 0.9, 10);
        service.register("S-002", "GaN 에피택셀", 0.3, 0.8, 20);

        List<Sample> result = service.searchByName("웨이퍼");
        assertEquals(1, result.size());
        assertEquals("S-001", result.get(0).getId());
    }

    @Test
    void searchByName_isCaseInsensitive() {
        service.register("S-001", "Silicon Wafer", 0.5, 0.9, 10);
        assertEquals(1, service.searchByName("SILICON").size());
    }

    @Test
    void searchByName_returnsEmpty_forNoMatch() {
        service.register("S-001", "실리콘 웨이퍼", 0.5, 0.9, 10);
        assertTrue(service.searchByName("없는값").isEmpty());
    }

    @Test
    void searchByName_emptyKeyword_returnsAll() {
        service.register("S-001", "A", 0.5, 0.9, 10);
        service.register("S-002", "B", 0.3, 0.8, 20);
        assertEquals(2, service.searchByName("").size());
    }

    // ── searchByYield ─────────────────────────────────────────────

    @Test
    void searchByYield_returnsHighYieldSamples() {
        service.register("S-001", "A", 0.5, 0.92, 10);
        service.register("S-002", "B", 0.3, 0.78, 20);
        service.register("S-003", "C", 0.8, 0.92, 0);

        List<Sample> result = service.searchByYield(0.90);
        assertEquals(2, result.size());
    }

    @Test
    void searchByYield_exactBoundaryIsIncluded() {
        service.register("S-001", "A", 0.5, 0.90, 10);
        assertEquals(1, service.searchByYield(0.90).size());
    }

    @Test
    void searchByYield_returnsEmpty_whenNoneMatch() {
        service.register("S-001", "A", 0.5, 0.78, 10);
        assertTrue(service.searchByYield(0.90).isEmpty());
    }

    // ── searchByStock ─────────────────────────────────────────────

    @Test
    void searchByStock_returnsHighStockSamples() {
        service.register("S-001", "A", 0.5, 0.9, 100);
        service.register("S-002", "B", 0.3, 0.8, 50);
        service.register("S-003", "C", 0.8, 0.9, 0);

        List<Sample> result = service.searchByStock(50);
        assertEquals(2, result.size());
    }

    @Test
    void searchByStock_exactBoundaryIsIncluded() {
        service.register("S-001", "A", 0.5, 0.9, 50);
        assertEquals(1, service.searchByStock(50).size());
    }

    @Test
    void searchByStock_zeroMin_returnsAll() {
        service.register("S-001", "A", 0.5, 0.9, 100);
        service.register("S-002", "B", 0.3, 0.8, 0);
        assertEquals(2, service.searchByStock(0).size());
    }

    @Test
    void searchByStock_returnsEmpty_whenNoneMatch() {
        service.register("S-001", "A", 0.5, 0.9, 10);
        assertTrue(service.searchByStock(50).isEmpty());
    }
}
