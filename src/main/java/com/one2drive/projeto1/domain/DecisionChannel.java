package com.one2drive.projeto1.domain;

/**
 * The two independent dimensions of a driving decision, one per vehicle control: the
 * longitudinal channel sets the speed (brake, hold, accelerate) and the lateral channel
 * the lane (keep, change left, change right). Each {@link DrivingAction} belongs to
 * exactly one channel, and the rules' proposals are arbitrated per channel.
 */
public enum DecisionChannel {
    LONGITUDINAL,
    LATERAL
}
