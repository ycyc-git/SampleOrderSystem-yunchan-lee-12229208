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

    @Test
    void add_and_findById_returnsSample() {
        Sample s = new Sample("S-001", "실리콘 웨이퍼", 0.5, 0.92, 100);
        repo.add(s);

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

        List<Sample> all = repo.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void save_updatesStock() {
        Sample s = new Sample("S-001", "A", 0.5, 0.9, 100);
        repo.add(s);
        s.setStock(50);
        repo.save(s);

        assertEquals(50, repo.findById("S-001").get().getStock());
    }

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
        SampleRepository fresh =
                new SampleRepository(tempDir.resolve("nonexistent.json").toString());
        assertTrue(fresh.findAll().isEmpty());
    }

    @Test
    void add_createsFileAutomatically() {
        Path file = tempDir.resolve("sub/samples.json");
        SampleRepository nested = new SampleRepository(file.toString());
        nested.add(new Sample("S-001", "A", 0.5, 0.9, 10));

        assertTrue(file.toFile().exists());
    }
}
