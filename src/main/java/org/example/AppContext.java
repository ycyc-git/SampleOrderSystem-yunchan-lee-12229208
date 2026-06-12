package org.example;

import org.example.controller.MonitoringController;
import org.example.controller.OrderController;
import org.example.controller.ProductionLineController;
import org.example.controller.ReleaseController;
import org.example.controller.SampleController;
import org.example.domain.Sample;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;
import org.example.service.MonitoringService;
import org.example.service.OrderService;
import org.example.service.ProductionLineService;
import org.example.service.ReleaseService;
import org.example.service.SampleService;
import org.example.util.ConsoleReader;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AppContext {

    private final SampleRepository sampleRepository;
    private final OrderService orderService;
    private final ProductionLineService productionLineService;
    private final ScheduledExecutorService scheduler;
    private final MainMenu mainMenu;

    public AppContext() {
        ConsoleReader consoleReader = new ConsoleReader();
        sampleRepository = new SampleRepository();
        OrderRepository orderRepository = new OrderRepository(sampleRepository);
        productionLineService = new ProductionLineService(orderRepository, sampleRepository);
        SampleService sampleService = new SampleService(sampleRepository);
        orderService = new OrderService(orderRepository, sampleRepository, productionLineService);

        MonitoringService monitoringService =
                new MonitoringService(orderRepository, sampleRepository);
        ReleaseService releaseService =
                new ReleaseService(orderRepository, sampleRepository);

        SampleController sampleController =
                new SampleController(sampleService, consoleReader, System.out);
        OrderController orderController =
                new OrderController(orderService, consoleReader, System.out);
        MonitoringController monitoringController =
                new MonitoringController(monitoringService, consoleReader, System.out);
        ProductionLineController productionLineController =
                new ProductionLineController(productionLineService, consoleReader, System.out);
        ReleaseController releaseController =
                new ReleaseController(releaseService, consoleReader, System.out);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "production-tick");
            t.setDaemon(true);
            return t;
        });

        mainMenu = new MainMenu(consoleReader, System.out, this::stop) {
            @Override
            protected int getSampleCount() {
                return sampleRepository.findAll().size();
            }

            @Override
            protected int getTotalStock() {
                return sampleRepository.findAll().stream()
                        .mapToInt(Sample::getStock).sum();
            }

            @Override
            protected int getTotalOrders() {
                return orderService.getTotalOrders();
            }

            @Override
            protected int getProductionQueueSize() {
                return productionLineService.getTotalQueueSize();
            }
        };
        mainMenu.setMenuHandler("1", sampleController::run);
        mainMenu.setMenuHandler("2", orderController::run);
        mainMenu.setMenuHandler("3", orderController::approveOrReject);
        mainMenu.setMenuHandler("4", monitoringController::run);
        mainMenu.setMenuHandler("5", productionLineController::run);
        mainMenu.setMenuHandler("6", releaseController::run);

        scheduler.scheduleAtFixedRate(productionLineService::tick, 0, 1, TimeUnit.SECONDS);
    }

    public void start() {
        mainMenu.run();
    }

    private void stop() {
        scheduler.shutdownNow();
        System.exit(0);
    }
}
