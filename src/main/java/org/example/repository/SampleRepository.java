package org.example.repository;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.example.domain.Sample;
import org.example.util.GsonConfig;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class SampleRepository {

    private static final String DEFAULT_PATH = "data/samples.json";

    private final String filePath;
    private final Gson gson;
    private final Map<String, Sample> store = new LinkedHashMap<>();

    public SampleRepository() {
        this(DEFAULT_PATH);
    }

    public SampleRepository(String filePath) {
        this.filePath = filePath;
        this.gson = GsonConfig.create();
        loadFromFile();
    }

    public void add(Sample sample) {
        store.put(sample.getId(), sample);
        saveToFile();
    }

    public Optional<Sample> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Sample> findAll() {
        return new ArrayList<>(store.values());
    }

    public List<Sample> findByName(String keyword) {
        String lower = (keyword == null ? "" : keyword).toLowerCase();
        return store.values().stream()
                .filter(s -> s.getName().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    public void save(Sample sample) {
        store.put(sample.getId(), sample);
        saveToFile();
    }

    private void loadFromFile() {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<Sample>>() {}.getType();
            List<Sample> samples = gson.fromJson(reader, listType);
            if (samples != null) {
                for (Sample s : samples) {
                    store.put(s.getId(), s);
                }
            }
        } catch (IOException e) {
            // start empty on read error
        }
    }

    private void saveToFile() {
        Path path = Paths.get(filePath);
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                gson.toJson(new ArrayList<>(store.values()), writer);
            }
        } catch (IOException e) {
            // ignore write errors
        }
    }
}
