package xin.v5ai.nb.model;

/**
 * Result of a model connectivity test, carrying the failure reason so clients can
 * diagnose invalid credentials, wrong endpoints, timeouts, etc.
 */
public record ConnectionTestResult(boolean ok, String message) {
    public static ConnectionTestResult success() {
        return new ConnectionTestResult(true, "connection ok");
    }

    public static ConnectionTestResult failure(String message) {
        return new ConnectionTestResult(false, message);
    }
}
