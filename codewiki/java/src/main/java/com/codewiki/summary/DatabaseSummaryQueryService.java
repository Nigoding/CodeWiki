package com.codewiki.summary;

import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.MethodSummaryRecord;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class DatabaseSummaryQueryService implements SummaryQueryService {

    @Override
    public List<ClassSummaryRecord> findClassSummariesByComponentIds(List<String> componentIds) {
        return Collections.emptyList();
    }

    @Override
    public List<MethodSummaryRecord> findMethodSummariesByComponentIds(List<String> componentIds) {
        return Collections.emptyList();
    }
}
