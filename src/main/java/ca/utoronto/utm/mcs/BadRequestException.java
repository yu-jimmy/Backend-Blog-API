package ca.utoronto.utm.mcs;

public class BadRequestException extends Exception {
    public BadRequestException(String errMessage) {
        super(errMessage);
    }
}
