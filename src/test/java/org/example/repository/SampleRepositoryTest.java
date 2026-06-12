package org.example.repository;

import org.example.domain.Sample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SampleRepositoryTest {

    @TempDir
    Path tempDir;

    private SampleRepository repo;

    @BeforeEach
    void setUp() {
        repo = new SampleRepository(tempDir.resolve("samples.json").toString());
    }

    // ── 기본 CRUD ─────────────────────────────────────────────────

    @Test
    void add_and_findById_returnsSample() {
        repo.add(new Sample("S-001", "실리콘 웨이퍼", 0.5, 0.92, 100));
        assertTrue(repo.findById("S-001").isPresent());
        assertEquals("실리콘 웨이퍼", repo.findById("S-001").get().getName());
    }

    @Test
    void findById_returnsEmpty_forUnknownId() {
        assertTrue(repo.findById("X-999").isEmpty());
    }

    @Test
    void findAll_returnsAllAddedSamples() {
        repo.add(new Sample("S-001", "A", 0.5, 0.9, 10));
        repo.add(new Sample("S-002", "B", 0.3, 0.8, 20));
        assertEquals(2, repo.findAll().size());
    }

    @Test
    void save_updatesStock() {
        Sample s = new Sample("S-001", "A", 0.5, 0.9, 100);
        repo.add(s);
        s.setStock(50);
        repo.save(s);
        assertEquals(50, repo.findById("S-001").get().getStock());
    }

    // ── 영속성 ────────────────────────────────────────────────────

    @Test
    void persistence_survivesRestart() {
        repo.add(new Sample("S-001", "실리콘 웨이퍼", 0.5, 0.92, 100));
        repo.add(new Sample("S-002", "GaN", 0.3, 0.78, 50));

        SampleRepository reloaded =
                new SampleRepository(tempDir.resolve("samples.json").toString());
        assertEquals(2, reloaded.findAll().size());
        assertEquals("실리콘 웨이퍼", reloaded.findById("S-001").get().getName());
        assertEquals(100, reloaded.findById("S-001").get().getStock());
    }

    @Test
    void persistence_restoresAllFields() {
        repo.add(new Sample("S-001", "테스트", 1.5, 0.75, 30));

        SampleRepository reloaded =
                new SampleRepository(tempDir.resolve("samples.json").toString());
        Sample loaded = reloaded.findById("S-001").get();

        assertEquals("S-001", loaded.getId());
        assertEquals("테스트", loaded.getName());
        assertEquals(1.5, loaded.getAvgProductionTime(), 1e-9);
        assertEquals(0.75, loaded.getYield(), 1e-9);
        assertEquals(30, loaded.getStock());
    }

    @Test
    void constructorWithMissingFile_startsEmpty() {
        assertTrue(new SampleRepository(
                tempDir.resolve("nonexistent.json").toString()).findAll().isEmpty());
    }

    @Test
    void add_createsFileAutomatically() {
        Path file = tempDir.resolve("sub/samples.json");
        new SampleRepository(file.toString())
                .add(new Sample("S-001", "A", 0.5, 0.9, 10));
        assertTrue(file.toFile().exists());
    }

    // ── findByIdContaining ────────────────────────────────────────

    @Test
    void findByIdContaining_matchesPartialId() {
        repo.add(new Sample("S-001", "A", 0.5, 0.9, 10));
        repo.add(new Sample("S-002", "B", 0.3, 0.8, 20));
        repo.add(new Sample("X-001", "C", 0.4, 0.7, 30));

        List<Sample> result = repo.findByIdContaining("S-00");
        assertEquals(2, result.size());
    }

    @Test
    void findByIdContaining_isCaseInsensitive() {
        repo.add(new Sample("S-001", "A", 0.5, 0.9, 10));
        List<Sample> result = repo.findByIdContaining("s-001");
        assertEquals(1, result.size());
    }

    @Test
    void findByIdContaining_emptyKeyword_returnsAll() {
        repo.add(new Sample("S-001", "A", 0.5, 0.9, 10));
        repo.add(new Sample("S-002", "B", 0.3, 0.8, 20));
        assertEquals(2, repo.findByIdContaining("").size());
    }

    @Test
    void findByIdContaining_returnsEmpty_forNoMatch() {
        repo.add(new Sample("S-001", "A", 0.5, 0.9, 10));
        assertTrue(repo.findByIdContaining("X-999").isEmpty());
    }

    // ── findByName ────────────────────────────────────────────────

    @Test
    void findByName_matchesPartialName() {
        repo.add(new Sample("S-001", "실리콘 웨이퍼-8인치", 0.5, 0.92, 100));
        repo.add(new Sample("S-002", "GaN 에피택셀-4인치", 0.3, 0.78, 50));
        repo.add(new Sample("S-003", "SiC 파워기판-6인치", 0.8, 0.92, 0));

        List<Sample> result = repo.findByName("웨이퍼");
        assertEquals(1, result.size());
        assertEquals("S-001", result.get(0).getId());
    }

    @Test
    void findByName_isCaseInsensitive() {
        repo.add(new Sample("S-001", "Silicon Wafer", 0.5, 0.9, 10));
        repo.add(new Sample("S-002", "GaN Epi", 0.3, 0.8, 20));

        List<Sample> result = repo.findByName("GAN");
        assertEquals(1, result.size());
        assertEquals("S-002", result.get(0).getId());
    }

    @Test
    void findByName_returnsEmpty_forNoMatch() {
        repo.add(new Sample("S-001", "실리콘 웨이퍼", 0.5, 0.9, 10));
        assertTrue(repo.findByName("없는값").isEmpty());
    }

    @Test
    void findByName_emptyKeyword_returnsAll() {
        repo.add(new Sample("S-001", "A", 0.5, 0.9, 10));
        repo.add(new Sample("S-002", "B", 0.3, 0.8, 20));
        assertEquals(2, repo.findByName("").size());
    }

    @Test
    void findByName_matchesMultiple() {
        repo.add(new Sample("S-001", "실리콘 웨이퍼-8인치", 0.5, 0.92, 100));
        repo.add(new Sample("S-002", "GaN 에피택셀-4인치", 0.3, 0.78, 50));
        repo.add(new Sample("S-003", "SiC 파워기판-6인치", 0.8, 0.92, 0));

        assertEquals(3, repo.findByName("인치").size());
    }

    // ── findByYieldAtLeast ────────────────────────────────────────

    @Test
    void findByYieldAtLeast_returnsMatchingResults() {
        repo.add(new Sample("S-001", "A", 0.5, 0.92, 100));
        repo.add(new Sample("S-002", "B", 0.3, 0.78, 50));
        repo.add(new Sample("S-003", "C", 0.8, 0.92, 0));

        List<Sample> result = repo.findByYieldAtLeast(0.90);
        assertEquals(2, result.size());
    }

    @Test
    void findByYieldAtLeast_exactBoundaryIsIncluded() {
        repo.add(new Sample("S-001", "A", 0.5, 0.90, 10));
        assertEquals(1, repo.findByYieldAtLeast(0.90).size());
    }

    @Test
    void findByYieldAtLeast_returnsEmpty_whenNoneMatch() {
        repo.add(new Sample("S-001", "A", 0.5, 0.78, 10));
        assertTrue(repo.findByYieldAtLeast(0.90).isEmpty());
    }

    @Test
    void findByYieldAtLeast_zeroMin_returnsAll() {
        repo.add(new Sample("S-001", "A", 0.5, 0.92, 10));
        repo.add(new Sample("S-002", "B", 0.3, 0.78, 20));
        assertEquals(2, repo.findByYieldAtLeast(0.0).size());
    }

    // ── findByStockAtLeast ────────────────────────────────────────

    @Test
    void findByStockAtLeast_returnsMatchingResults() {
        repo.add(new Sample("S-001", "A", 0.5, 0.9, 100));
        repo.add(new Sample("S-002", "B", 0.3, 0.8, 50));
        repo.add(new Sample("S-003", "C", 0.8, 0.9, 0));

        List<Sample> result = repo.findByStockAtLeast(50);
        assertEquals(2, result.size());
    }

    @Test
    void findByStockAtLeast_exactBoundaryIsIncluded() {
        repo.add(new Sample("S-001", "A", 0.5, 0.9, 50));
        assertEquals(1, repo.findByStockAtLeast(50).size());
    }

    @Test
    void findByStockAtLeast_returnsEmpty_whenNoneMatch() {
        repo.add(new Sample("S-001", "A", 0.5, 0.9, 10));
        assertTrue(repo.findByStockAtLeast(50).isEmpty());
    }

    @Test
    void findByStockAtLeast_zeroMin_returnsAll() {
        repo.add(new Sample("S-001", "A", 0.5, 0.9, 100));
        repo.add(new Sample("S-002", "B", 0.3, 0.8, 0));
        assertEquals(2, repo.findByStockAtLeast(0).size());
    }
}
