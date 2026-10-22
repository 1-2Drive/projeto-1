package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiCreateRoadElementRequest;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiRequestMapperTest {
    private final ApiRequestMapper mapper = new ApiRequestMapper();

    private static ApiCreateRoadElementRequest request(String type, String lane) {
        ApiCreateRoadElementRequest request = new ApiCreateRoadElementRequest();
        request.setType(type);
        request.setLane(lane);
        request.setDistanceAheadMeters(150.0);
        return request;
    }

    @Test
    void aStopSignNeedsNoLaneBecauseItGovernsTheWholeRoad() {
        ScenarioRoadElementDefinition withoutLane = mapper.toRoadElement(request("STOP_SIGN", null));
        assertEquals(Lane.CENTER, withoutLane.getLane(), "stored in the centre cell; the lane is only where the map keeps it");
        assertEquals(150.0, withoutLane.getPositionMeters());

        assertEquals(Lane.CENTER, mapper.toRoadElement(request("STOP_SIGN", "ALL")).getLane());
        assertEquals(Lane.CENTER, mapper.toRoadElement(request("STOP_SIGN", " ")).getLane());
    }

    @Test
    void otherRoadWideElementsNeedNoLaneEither() {
        for (String type : new String[] {"SPEED_LIMIT", "TRAFFIC_LIGHT", "CROSSWALK"}) {
            assertEquals(Lane.CENTER, mapper.toRoadElement(request(type, null)).getLane(), type);
        }
    }

    @Test
    void aGivenLaneIsStillHonoured() {
        assertEquals(Lane.LEFT, mapper.toRoadElement(request("POTHOLE", "left")).getLane());
        assertEquals(Lane.RIGHT, mapper.toRoadElement(request("STOP_SIGN", "RIGHT")).getLane());
    }

    @Test
    void anElementOfOneLaneStillNeedsOne() {
        assertThrows(IllegalArgumentException.class, () -> mapper.toRoadElement(request("POTHOLE", null)));
        assertThrows(IllegalArgumentException.class, () -> mapper.toRoadElement(request("BARRIER", "ALL")));
    }
}
