package com.leadpilot.ai;

class AiProviderException extends RuntimeException {
    private final boolean timeout;

    AiProviderException(boolean timeout) {
        this.timeout = timeout;
    }

    boolean isTimeout() {
        return timeout;
    }
}
