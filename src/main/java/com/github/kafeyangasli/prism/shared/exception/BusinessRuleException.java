package com.github.kafeyangasli.prism.shared.exception;

public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String message) {
        this("BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
        this.code = "BUSINESS_RULE_VIOLATION";
    }

    public String getCode() {
        return code;
    }
}
