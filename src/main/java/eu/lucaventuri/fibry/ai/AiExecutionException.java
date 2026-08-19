package eu.lucaventuri.fibry.ai;

public class AiExecutionException extends RuntimeException {
    private final String state;

    public AiExecutionException(Throwable e, String state) {
        super("Exception on state " + state + ": " + e.getMessage(), e);
        this.state = state;
    }

    public String state() {
        return state;
    }

    public static AiExecutionException from(Exception e, String state) {
        Throwable refEx = e;

        while (refEx.getCause() != null) {
            refEx = refEx.getCause();
        }

        return new AiExecutionException(refEx, state);
    }
}
