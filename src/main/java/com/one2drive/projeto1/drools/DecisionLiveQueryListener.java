package com.one2drive.projeto1.drools;

import com.one2drive.projeto1.decision.DrivingDecision;
import org.kie.api.runtime.rule.Row;
import org.kie.api.runtime.rule.ViewChangedEventListener;

import java.util.function.Consumer;

public class DecisionLiveQueryListener implements ViewChangedEventListener {
    private final Consumer<DrivingDecision> consumer;

    public DecisionLiveQueryListener(Consumer<DrivingDecision> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void rowInserted(Row row) {
        publish(row);
    }

    @Override
    public void rowDeleted(Row row) {
        // no-op
    }

    @Override
    public void rowUpdated(Row row) {
        publish(row);
    }

    private void publish(Row row) {
        Object decision = row.get("$decision");
        if (decision instanceof DrivingDecision drivingDecision) {
            consumer.accept(drivingDecision);
        }
    }
}
