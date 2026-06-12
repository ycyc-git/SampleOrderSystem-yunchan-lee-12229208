package org.example.service;

import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.ProductionJob;
import org.example.domain.Sample;
import org.example.repository.OrderRepository;
import org.example.repository.SampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductionLineServiceTest {

    @TempDir
    Path tempDir;

    private SampleRepository sampleRepo;
    private OrderRepository orderRepo;
    private OrderService orderService;
    private ProductionLineService service;

    @BeforeEach
    void setUp() {
        sampleRepo = new SampleRepository(tempDir.resolve("samples.json").toString());
        orderRepo = new OrderRepository(tempDir.resolve("orders.json").toString(), sampleRepo);
        service = new ProductionLineService(tempDir.resolve("jobs.json").toString(), orderRepo);
        orderService = new OrderService(orderRepo, sampleRepo, service);

        sampleRepo.add(new Sample("S-001", "실리콘 웨이퍼", 0.5, 0.92, 100));
        sampleRepo.add(new Sample("S-002", "GaN 에피택셀", 0.3, 0.78, 0));
        sampleRepo.add(new Sample("S-003", "SiC 기판", 0.8, 0.85, 0));
    }

    private Order reserveAndApprove(String sampleId, String customer, int qty) {
        Order o = orderService.reserve(sampleId, customer, qty);
        orderService.approve(o.getOrderId());
        return orderRepo.findById(o.getOrderId()).get();
    }

    // ── enqueue ───────────────────────────────────────────────────

    @Test
    void enqueue_addsJobToQueue() {
        reserveAndApprove("S-002", "고객", 50);
        assertEquals(1, service.getTotalQueueSize());
    }

    @Test
    void enqueue_firstJob_hasStartedAt() {
        reserveAndApprove("S-002", "고객", 50);
        assertTrue(service.getCurrentJob().isPresent());
        assertNotNull(service.getCurrentJob().get().getStartedAt());
    }

    @Test
    void enqueue_secondJob_hasNullStartedAt() {
        reserveAndApprove("S-002", "고객1", 50);
        reserveAndApprove("S-002", "고객2", 30);
        List<ProductionJob> waiting = service.getWaitingQueue();
        assertEquals(1, waiting.size());
        assertNull(waiting.get(0).getStartedAt());
    }

    @Test
    void enqueue_calculatesActualQtyCorrectly() {
        // S-002: stock=0, qty=50, shortage=50, yield=0.78
        // actualQty = ceil(50 / (0.78 * 0.9)) = ceil(71.22) = 72
        reserveAndApprove("S-002", "고객", 50);
        ProductionJob job = service.getCurrentJob().get();
        assertEquals(50, job.getShortage());
        int expected = (int) Math.ceil(50.0 / (0.78 * 0.9));
        assertEquals(expected, job.getActualProductionQty());
    }

    @Test
    void enqueue_calculatesTotalTimeCorrectly() {
        reserveAndApprove("S-002", "고객", 50);
        ProductionJob job = service.getCurrentJob().get();
        int actualQty = job.getActualProductionQty();
        assertEquals(0.3 * actualQty, job.getTotalProductionTime(), 0.001);
    }

    @Test
    void enqueue_jobIdStartsWithPJ() {
        reserveAndApprove("S-002", "고객", 50);
        assertTrue(service.getCurrentJob().get().getJobId().startsWith("PJ-"));
    }

    @Test
    void enqueue_jobIdsAreUnique() {
        reserveAndApprove("S-002", "고객1", 50);
        reserveAndApprove("S-002", "고객2", 30);
        ProductionJob current = service.getCurrentJob().get();
        ProductionJob waiting = service.getWaitingQueue().get(0);
        assertNotEquals(current.getJobId(), waiting.getJobId());
    }

    // ── getCurrentJob / getWaitingQueue ───────────────────────────

    @Test
    void getCurrentJob_returnsEmpty_whenQueueEmpty() {
        assertTrue(service.getCurrentJob().isEmpty());
    }

    @Test
    void getWaitingQueue_returnsEmpty_whenOnlyOneJob() {
        reserveAndApprove("S-002", "고객", 50);
        assertTrue(service.getWaitingQueue().isEmpty());
    }

    @Test
    void getWaitingQueue_returnsFifoOrder() {
        reserveAndApprove("S-002", "고객1", 50);
        reserveAndApprove("S-002", "고객2", 30);
        reserveAndApprove("S-003", "고객3", 20);

        assertEquals(2, service.getWaitingQueue().size());
        assertEquals("고객2",
                service.getWaitingQueue().get(0).getOrder().getCustomerName());
        assertEquals("고객3",
                service.getWaitingQueue().get(1).getOrder().getCustomerName());
    }

    @Test
    void getTotalQueueSize_includesCurrentAndWaiting() {
        reserveAndApprove("S-002", "고객1", 50);
        reserveAndApprove("S-002", "고객2", 30);
        assertEquals(2, service.getTotalQueueSize());
    }

    @Test
    void getProgressPercent_returnsZero_inPhase07() {
        reserveAndApprove("S-002", "고객", 50);
        assertEquals(0, service.getProgressPercent());
    }

    // ── 영속성 ────────────────────────────────────────────────────

    @Test
    void persistence_survivesRestart() {
        reserveAndApprove("S-002", "고객1", 50);
        reserveAndApprove("S-002", "고객2", 30);

        ProductionLineService reloaded = new ProductionLineService(
                tempDir.resolve("jobs.json").toString(), orderRepo);
        assertEquals(2, reloaded.getTotalQueueSize());
    }

    @Test
    void persistence_restoresCurrentJobFields() {
        reserveAndApprove("S-002", "고객", 50);
        ProductionJob original = service.getCurrentJob().get();

        ProductionLineService reloaded = new ProductionLineService(
                tempDir.resolve("jobs.json").toString(), orderRepo);
        ProductionJob restored = reloaded.getCurrentJob().get();

        assertEquals(original.getJobId(), restored.getJobId());
        assertEquals(original.getShortage(), restored.getShortage());
        assertEquals(original.getActualProductionQty(), restored.getActualProductionQty());
        assertEquals(original.getTotalProductionTime(), restored.getTotalProductionTime(), 0.001);
        assertNotNull(restored.getStartedAt());
    }

    @Test
    void persistence_restoresFifoOrder() {
        reserveAndApprove("S-002", "고객1", 50);
        reserveAndApprove("S-002", "고객2", 30);

        ProductionLineService reloaded = new ProductionLineService(
                tempDir.resolve("jobs.json").toString(), orderRepo);
        assertEquals("고객1",
                reloaded.getCurrentJob().get().getOrder().getCustomerName());
        assertEquals("고객2",
                reloaded.getWaitingQueue().get(0).getOrder().getCustomerName());
    }

    @Test
    void constructorWithMissingFile_startsEmpty() {
        ProductionLineService fresh = new ProductionLineService(
                tempDir.resolve("nonexistent.json").toString(), orderRepo);
        assertEquals(0, fresh.getTotalQueueSize());
    }

    // ── OrderService 연동 ─────────────────────────────────────────

    @Test
    void orderService_approve_insufficientStock_enqueuesToProductionLine() {
        Order o = orderService.reserve("S-002", "고객", 50);
        orderService.approve(o.getOrderId());
        assertEquals(1, service.getTotalQueueSize());
    }

    @Test
    void orderService_approve_sufficientStock_doesNotEnqueue() {
        Order o = orderService.reserve("S-001", "고객", 30); // stock=100
        orderService.approve(o.getOrderId());
        assertEquals(0, service.getTotalQueueSize());
    }
}
