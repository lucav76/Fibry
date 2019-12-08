package eu.lucaventuri.fibry.receipts;

public class ImmutableProgress {
    /** Progress, from 0.0f to 1.0f */
    private final float progressPercent;
    /** Description of the phase */
    private final String progressPhase;
    /** More complex object, expressing the progress in an application-dependent way */
    private final String progressJson;

    public ImmutableProgress() {
        this(0, null, null);
    }

    public ImmutableProgress(float progressPercent, String progressPhase, String progressJson) {
        this.progressPercent = Math.max(0.0f, Math.min(progressPercent, 1.0f));
        this.progressPhase = progressPhase;
        this.progressJson = progressJson;
    }

    public ImmutableProgress(float progressPercent) {
        this(progressPercent, null, null);
    }

    public ImmutableProgress withProgressPercent(float newProgressPercent) {
        return new ImmutableProgress(newProgressPercent, progressPhase, progressJson);
    }

    public ImmutableProgress withProgressPhase(String newProgressPhase) {
        return new ImmutableProgress(progressPercent, newProgressPhase, progressJson);
    }

    public ImmutableProgress withProgressJson(String newProgressJson) {
        return new ImmutableProgress(progressPercent, progressPhase, newProgressJson);
    }

    public float getProgressPercent() {
        return progressPercent;
    }

    public String getProgressPhase() {
        return progressPhase;
    }

    public String getProgressJson() {
        return progressJson;
    }
}
