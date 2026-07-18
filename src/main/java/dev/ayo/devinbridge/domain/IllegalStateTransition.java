package dev.ayo.devinbridge.domain;


public final class IllegalStateTransition extends RuntimeException {

    public IllegalStateTransition(SessionState current, SessionState.Event event) {
        super("Cannot apply " + event.getClass().getSimpleName()
                + " to state " + current.getClass().getSimpleName());
    }
}
