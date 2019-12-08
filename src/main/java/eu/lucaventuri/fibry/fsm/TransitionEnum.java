package eu.lucaventuri.fibry.fsm;

class TransitionEnum<E, S extends Enum> {
    final E event;
    final S targetState;

    TransitionEnum(E event, S targetState) {
        this.event = event;
        this.targetState = targetState;
    }
}
