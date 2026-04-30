package com.team28.booking.invoice.auth.handlers;

public abstract class AuthHandler {

    protected AuthHandler next;

    public AuthHandler setNext(AuthHandler next) {
        this.next = next;
        return next;
    }

    public abstract void handle(AuthContext context);

    protected void handleNext(AuthContext context) {
        if (context.hasError()) {
            return;
        }
        if (next != null) {
            next.handle(context);
        }
    }
}
