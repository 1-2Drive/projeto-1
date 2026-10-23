package com.one2drive.projeto1.drools.facts;

/**
 * What the vehicle has decided to do about the traffic light ahead. It is decided once, by
 * the rules, when the light is first seen yellow or red, and kept until the light turns
 * green, another light takes its place or the light is no longer seen: a vehicle that
 * decided to stop does not change its mind and drive on, and one that decided to drive on
 * does not brake halfway across.
 */
public enum TrafficLightCommitment {
    NONE,
    COMMITTED_TO_STOP,
    COMMITTED_TO_PROCEED
}
