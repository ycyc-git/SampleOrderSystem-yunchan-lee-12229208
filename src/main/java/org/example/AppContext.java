package org.example;

import org.example.controller.MonitoringController;
import org.example.controller.OrderController;
import org.example.controller.ProductionLineController;
import org.example.controller.SampleController;
import org.example.domain.Sample;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;
import org.example.service.MonitoringService;
import org.example.service.OrderService;
import org.example.service.ProductionLineService;
import org.example.service.SampleService;
import org.example.util.ConsoleReader;

public class AppContext {

    private final SampleRepository sampleRepository;
    private final OrderService orderService;
    private final ProductionLineService productionLineService;
    private final MainMenu mainMenu;

    public AppContext() {
        ConsoleReader consoleReader = new ConsoleReader();
        sampleRepository = new SampleRepository();
        OrderRepository orderRepository = new OrderRepository(sampleRepository);
        productionLineService = new ProductionLineService(orderRepository);
        SampleService sampleService = new SampleService(sampleRepository);
        orderService = new OrderService(orderRepository, sampleRepository, productionLineService);

        MonitoringService monitoringService =
                new MonitoringService(orderRepository, sampleRepository);

        SampleController sampleController =
                new SampleController(sampleService, consoleReader, System.out);
        OrderController orderController =
                new OrderController(orderService, consoleReader, System.out);
        MonitoringController monitoringController =
                new MonitoringController(monitoringService, consoleReader, System.out);
        ProductionLineController productionLineController =
                new ProductionLineController(productionLineService, consoleReader, System.out);

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
    }

    public void start() {
        mainMenu.run();
    }
}
