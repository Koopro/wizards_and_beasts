package at.koopro.wizardsandbeasts.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaraudersMapTrackerScalingTest {

    @Test
    void computeSyncInterval_scalesWithViewerCountAndCaps() {
        assertEquals(5, MaraudersMapTracker.computeSyncInterval(1));
        assertEquals(5, MaraudersMapTracker.computeSyncInterval(2));
        assertEquals(6, MaraudersMapTracker.computeSyncInterval(3));
        assertEquals(12, MaraudersMapTracker.computeSyncInterval(20));
    }

    @Test
    void computePerViewerEntityCap_scalesDownAndRespectsFloor() {
        assertEquals(200, MaraudersMapTracker.computePerViewerEntityCap(1));
        assertEquals(185, MaraudersMapTracker.computePerViewerEntityCap(2));
        assertEquals(170, MaraudersMapTracker.computePerViewerEntityCap(3));
        assertEquals(80, MaraudersMapTracker.computePerViewerEntityCap(20));
    }

    @Test
    void computeViewersToProcess_capsWorkPerTick() {
        assertEquals(0, MaraudersMapTracker.computeViewersToProcess(0));
        assertEquals(1, MaraudersMapTracker.computeViewersToProcess(1));
        assertEquals(10, MaraudersMapTracker.computeViewersToProcess(10));
        assertEquals(10, MaraudersMapTracker.computeViewersToProcess(50));
    }
}
