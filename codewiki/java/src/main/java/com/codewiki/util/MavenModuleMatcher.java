package com.codewiki.util;

import java.util.List;

/**
 * Resolves which Maven sub-module a relative source path belongs to, using
 * the list of module names parsed from pom.xml files.
 *
 * Matches on path segments bounded by '/' to avoid substring false positives
 * (e.g. "api" should not match inside "foo-api-internal"). When multiple
 * module names match (e.g. "parent" and "parent-child"), picks the longest.
 */
public final class MavenModuleMatcher {

    private MavenModuleMatcher() {}

    public static String match(String relativePath, List<String> mavenModules) {
        if (relativePath == null || mavenModules == null || mavenModules.isEmpty()) {
            return null;
        }
        String path = relativePath.replace('\\', '/');
        String best = null;
        int bestLen = -1;
        for (String name : mavenModules) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (containsSegment(path, name) && name.length() > bestLen) {
                best = name;
                bestLen = name.length();
            }
        }
        return best;
    }

    private static boolean containsSegment(String path, String segment) {
        int idx = 0;
        while ((idx = path.indexOf(segment, idx)) >= 0) {
            boolean leftOk = idx == 0 || path.charAt(idx - 1) == '/';
            int end = idx + segment.length();
            boolean rightOk = end == path.length() || path.charAt(end) == '/';
            if (leftOk && rightOk) {
                return true;
            }
            idx = end;
        }
        return false;
    }
}
