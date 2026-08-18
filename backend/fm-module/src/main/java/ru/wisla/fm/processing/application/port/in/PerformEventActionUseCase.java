package ru.wisla.fm.processing.application.port.in;

/**
 * Console duty actions ({@code take}, {@code close}, {@code comment}, {@code ack}, {@code assign},
 * {@code silence}) as an inbound port. Invoked by {@code EventController} in {@code processing.api}.
 */
public interface PerformEventActionUseCase {

    EventActionOutcome perform(EventActionCommand command);
}
