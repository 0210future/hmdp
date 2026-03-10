package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public final class JwtUtils {
    private static final String HMAC_ALGO = "HmacSHA256";

    private JwtUtils() {
    }

    public static String createToken(Map<String, Object> claims, String secret, long ttlSeconds) {
        Map<String, Object> payload = new HashMap<>();
        if (claims != null) {
            payload.putAll(claims);
        }
        if (ttlSeconds > 0) {
            payload.put("exp", Instant.now().getEpochSecond() + ttlSeconds);
        }

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = JSONUtil.toJsonStr(payload);
        String header = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        String body = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        String data = header + "." + body;
        String signature = hmacSha256(data, secret);
        return data + "." + signature;
    }

    public static Map<String, Object> parseToken(String token, String secret) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String data = parts[0] + "." + parts[1];
        String expectedSignature = hmacSha256(data, secret);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            return null;
        }

        try {
            String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
            JSONObject obj = JSONUtil.parseObj(payloadJson);
            Long exp = obj.getLong("exp");
            if (exp != null && exp <= Instant.now().getEpochSecond()) {
                return null;
            }
            return new HashMap<>(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static String hmacSha256(String data, String secret) {
        try {
            String key = secret == null ? "" : secret;
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(signature);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }
}
