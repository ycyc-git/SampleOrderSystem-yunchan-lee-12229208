package org.example;

import org.example.controller.SampleController;
import org.example.domain.Sample;
import org.example.repository.SampleRepository;
import org.example.service.SampleService;
import org.example.util.ConsoleReader;

public class AppContext {

    private final SampleRepository sampleRepository;
    private final MainMenu mainMenu;

    public AppContext() {
        ConsoleReader consoleReader = new ConsoleReader();
        sampleRepository = new SampleRepository();
        SampleService sampleService = new SampleService(sampleRepository);
        SampleController sampleController =
                new SampleController(sampleService, consoleReader, System.out);

        mainMenu = new MainMenu(consoleReader) {
            @Override
            protected int getSampleCount() {
                return sampleRepository.findAll().size();
            }

            @Override
            protected int getTotalStock() {
                return sampleRepository.findAll().stream()
                        .mapToInt(Sample::getStock).sum();
            }
        };
        mainMenu.setMenuHandler("1", sampleController::run);
    }

    public void start() {
        mainMenu.run();
    }
}
