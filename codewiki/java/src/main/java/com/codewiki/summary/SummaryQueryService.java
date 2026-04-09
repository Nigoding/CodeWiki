package com.codewiki.summary;

import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.MethodSummaryRecord;

import java.util.List;

public interface SummaryQueryService {

    List<ClassSummaryRecord> findClassSummariesByComponentIds(List<String> componentIds);

    List<MethodSummaryRecord> findMethodSummariesByComponentIds(List<String> componentIds);
}
