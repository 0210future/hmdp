package com.hmdp.common.context;

public final class UserContext {
    private static final ThreadLocal<RequestUser> TL = new ThreadLocal<>();

    private UserContext() {}

    public static void set(RequestUser user) {
        TL.set(user);
    }

    public static RequestUser get() {
        return TL.get();
    }

    public static void clear() {
        TL.remove();
    }
}