package com.codewiki.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PreClusterPlan {

    private static final PreClusterPlan EMPTY = new PreClusterPlan(Collections.<String, List<String>>emptyMap());

    private final Map<String, List<String>> subModules;

    private PreClusterPlan(Map<String, List<String>> subModules) {
        this.subModules = Collections.unmodifiableMap(new LinkedHashMap<String, List<String>>(subModules));
    }

    public static PreClusterPlan empty() {
        return EMPTY;
    }

    public static PreClusterPlan of(Map<String, List<String>> subModules) {
        if (subModules == null || subModules.isEmpty()) {
            return EMPTY;
        }
        return new PreClusterPlan(subModules);
    }

    public boolean isEmpty() {
        return subModules.isEmpty();
    }

    public Map<String, List<String>> getSubModules() {
        return subModules;
    }
}
