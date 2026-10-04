package com.insight.router;

import com.insight.router.proto.CastPerson;
import com.insight.router.proto.FlightState;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Repeatable fiction for the presentation; never modifies the historical catalog. */
@Component
public class FlightSimulation {
    private final Scenario scenario;
    private final Catalog catalog;
    private final int intervalMs;

    public FlightSimulation(
            Catalog catalog,
            JsonMapper mapper,
            @Value("${demo.flight.interval-ms:2000}") int intervalMs) {
        this.catalog = catalog;
        if (intervalMs < 20 || intervalMs > 5000)
            throw new IllegalArgumentException("Flight interval must be between 20 and 5000 ms");
        this.intervalMs = intervalMs;
        try (var input = new ClassPathResource("falcon-flight.json").getInputStream()) {
            scenario = mapper.readValue(input, Scenario.class);
            if (scenario.frames().size() != 21)
                throw new IllegalArgumentException("Expected 21 flight frames");
            catalog.starships("ship:" + scenario.shipId()).getFirst();
            for (var frame : scenario.frames()) {
                catalog.castMember(frame.pilotId());
                catalog.castMember(frame.coPilotId());
                if (frame.speedKph() < 0
                        || frame.shieldsPercent() < 0
                        || frame.shieldsPercent() > 100)
                    throw new IllegalArgumentException("Invalid flight telemetry");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Invalid flight simulation seed", e);
        }
    }

    public boolean supports(String target) {
        return target.equals("ship:" + scenario.shipId());
    }

    public int count() {
        return scenario.frames().size();
    }

    public int intervalMs() {
        return intervalMs;
    }

    public FlightState frame(int index) {
        var f = scenario.frames().get(index);
        return FlightState.newBuilder()
                .setSimulated(true)
                .setScenarioId(scenario.id())
                .setTitle(scenario.title())
                .setShipName(catalog.starships("ship:" + scenario.shipId()).getFirst().name())
                .setOrigin(scenario.origin())
                .setDestination(scenario.destination())
                .setLocation(f.location())
                .setPhase(f.phase())
                .setPilot(person(f.pilotId()))
                .setCoPilot(person(f.coPilotId()))
                .setProgressPercent(index * 100 / (count() - 1))
                .setSpeedKph(f.speedKph())
                .setShieldsPercent(f.shieldsPercent())
                .setEvent(f.event())
                .setElapsedMs(index * intervalMs)
                .setDurationMs((count() - 1) * intervalMs)
                .build();
    }

    private CastPerson person(String id) {
        var member = catalog.castMember(id);
        return CastPerson.newBuilder().setId(member.id()).setName(member.name()).build();
    }

    record Frame(
            String location,
            String phase,
            String pilotId,
            String coPilotId,
            int speedKph,
            int shieldsPercent,
            String event) {}

    record Scenario(
            String id,
            String title,
            String shipId,
            String origin,
            String destination,
            List<Frame> frames) {
        Scenario {
            frames = List.copyOf(frames);
        }
    }
}
