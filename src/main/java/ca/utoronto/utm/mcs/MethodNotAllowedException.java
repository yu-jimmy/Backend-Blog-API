package ca.utoronto.utm.mcs;

public class MethodNotAllowedException extends Exception {
    public MethodNotAllowedException(String errMessage) {
        super(errMessage);
    }
}
