package com.hmdp.support;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class LocalCodeStore {

    private static final class CodeEntry {
        private final String code;
        private final long expireAt;

        private CodeEntry(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }

    private final ConcurrentHashMap<String, CodeEntry> store = new ConcurrentHashMap<>();

    public void store(String phone, String code, long ttlMinutes) {
        long expireAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ttlMinutes);
        store.put(phone, new CodeEntry(code, expireAt));
    }

    public String get(String phone) {
        CodeEntry entry = store.get(phone);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt <= System.currentTimeMillis()) {
            store.remove(phone);
            return null;
        }
        return entry.code;
    }
}
