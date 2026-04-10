package com.codewiki.summary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SummaryElementNames {

    private SummaryElementNames() {
    }

    public static String toMethodPrefix(String classFqn) {
        return classFqn + "#";
    }

    public static String toMethodElementName(String classFqn, String methodName, String methodSignature) {
        return classFqn + "#" + methodName + "(" + methodSignature + ")";
    }

    public static String extractPackageName(String classFqn) {
        int idx = classFqn.lastIndexOf('.');
        return idx < 0 ? "" : classFqn.substring(0, idx);
    }

    public static String simpleClassName(String classFqn) {
        int idx = classFqn.lastIndexOf('.');
        return idx < 0 ? classFqn : classFqn.substring(idx + 1);
    }

    public static String extractMethodName(String methodElementName) {
        int hashIdx = methodElementName.indexOf('#');
        int parenIdx = methodElementName.indexOf('(');
        if (hashIdx < 0 || parenIdx < 0 || parenIdx <= hashIdx) {
            return methodElementName;
        }
        return methodElementName.substring(hashIdx + 1, parenIdx);
    }

    public static String md5(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash summary element name", e);
        }
    }
}
