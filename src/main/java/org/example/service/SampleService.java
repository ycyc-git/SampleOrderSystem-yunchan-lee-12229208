package org.example.service;

import org.example.domain.Sample;
import org.example.repository.SampleRepository;

import java.util.List;
import java.util.Optional;

public class SampleService {

    private final SampleRepository repository;

    public SampleService(SampleRepository repository) {
        this.repository = repository;
    }

    public Sample register(String id, String name, double avgProductionTime,
                           double yield, int initialStock) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("시료 ID를 입력하세요.");
        if (repository.findById(id).isPresent())
            throw new IllegalArgumentException("이미 등록된 시료 ID입니다.");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("시료명을 입력하세요.");
        if (avgProductionTime <= 0)
            throw new IllegalArgumentException("평균 생산시간은 0보다 커야 합니다.");
        if (yield <= 0 || yield > 1.0)
            throw new IllegalArgumentException("수율은 0 초과 1 이하의 값을 입력하세요.");
        if (initialStock < 0)
            throw new IllegalArgumentException("초기 재고는 0 이상이어야 합니다.");

        Sample sample = new Sample(id, name, avgProductionTime, yield, initialStock);
        repository.add(sample);
        return sample;
    }

    public Optional<Sample> findById(String id) {
        return repository.findById(id);
    }

    public List<Sample> search(String keyword) {
        return repository.findByName(keyword);
    }

    public List<Sample> getAll() {
        return repository.findAll();
    }
}
