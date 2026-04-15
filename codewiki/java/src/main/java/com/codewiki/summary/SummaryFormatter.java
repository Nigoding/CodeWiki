package com.codewiki.summary;

import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.MethodSummaryRecord;
import com.codewiki.summary.dto.PackageSummaryRecord;
import com.codewiki.util.Texts;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Uniform formatting for summary records used across prompts and tool responses.
 */
@Component
public class SummaryFormatter {

    public String formatClassSummary(ClassSummaryRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append(record.getClassName());
        if (Texts.trimToEmpty(record.getRelativePath()).length() > 0) {
            sb.append(" [").append(Texts.trimToEmpty(record.getRelativePath())).append("]");
        }
        if (Texts.trimToEmpty(record.getRole()).length() > 0) {
            sb.append(": role=").append(Texts.trimToEmpty(record.getRole()));
        }
        if (Texts.trimToEmpty(record.getKeyFunctionality()).length() > 0) {
            sb.append("; functionality=").append(Texts.trimToEmpty(record.getKeyFunctionality()));
        }
        if (Texts.trimToEmpty(record.getPurpose()).length() > 0) {
            sb.append("; purpose=").append(Texts.trimToEmpty(record.getPurpose()));
        }
        return sb.toString();
    }

    public String formatMethodSummary(MethodSummaryRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append(record.getClassName())
                .append("#").append(record.getMethodName())
                .append(": ").append(Texts.trimToEmpty(record.getSummary()));
        if (!record.getInputs().isEmpty()) {
            sb.append(" Inputs=").append(String.join(", ", record.getInputs()));
        }
        if (Texts.trimToEmpty(record.getOutputs()).length() > 0) {
            sb.append(" Outputs=").append(Texts.trimToEmpty(record.getOutputs()));
        }
        if (!record.getSideEffects().isEmpty()) {
            sb.append(" SideEffects=").append(String.join(", ", record.getSideEffects()));
        }
        if (Texts.trimToEmpty(record.getDataFlow()).length() > 0) {
            sb.append(" DataFlow=").append(Texts.trimToEmpty(record.getDataFlow()));
        }
        return sb.toString();
    }

    public String formatClassSummaryBrief(ClassSummaryRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append(record.getClassName());
        if (Texts.trimToEmpty(record.getRole()).length() > 0) {
            sb.append(": ").append(Texts.trimToEmpty(record.getRole()));
        }
        if (Texts.trimToEmpty(record.getPurpose()).length() > 0) {
            sb.append("; purpose=").append(Texts.trimToEmpty(record.getPurpose()));
        }
        return sb.toString();
    }

    public String formatMethodSummaryBrief(MethodSummaryRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append(record.getClassName())
                .append("#")
                .append(record.getMethodName())
                .append(": ")
                .append(Texts.trimToEmpty(record.getSummary()));
        if (!record.getSideEffects().isEmpty()) {
            int limit = Math.min(2, record.getSideEffects().size());
            sb.append("; side effects=")
                    .append(String.join(", ", record.getSideEffects().subList(0, limit)));
        }
        return sb.toString();
    }

    public String formatMethodSummaryRecall(MethodSummaryRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append(record.getMethodName())
                .append(": ")
                .append(Texts.trimToEmpty(record.getSummary()));
        if (!record.getSideEffects().isEmpty()) {
            int limit = Math.min(2, record.getSideEffects().size());
            sb.append("\n  Side effects: ")
                    .append(String.join(", ", record.getSideEffects().subList(0, limit)));
        }
        return sb.toString();
    }

    public String formatCoreComponentSummary(ClassSummaryRecord classRecord,
                                             List<MethodSummaryRecord> representativeMethods) {
        StringBuilder sb = new StringBuilder();
        sb.append("- Component: ").append(classRecord.getClassName()).append("\n");
        if (Texts.trimToEmpty(classRecord.getRole()).length() > 0) {
            sb.append("  Role: ").append(Texts.trimToEmpty(classRecord.getRole())).append("\n");
        }
        if (Texts.trimToEmpty(classRecord.getPurpose()).length() > 0) {
            sb.append("  Purpose: ").append(Texts.trimToEmpty(classRecord.getPurpose())).append("\n");
        }
        if (Texts.trimToEmpty(classRecord.getKeyFunctionality()).length() > 0) {
            sb.append("  Key behavior: ").append(Texts.trimToEmpty(classRecord.getKeyFunctionality())).append("\n");
        }
        if (representativeMethods != null && !representativeMethods.isEmpty()) {
            sb.append("  Representative methods:\n");
            int limit = Math.min(2, representativeMethods.size());
            for (int i = 0; i < limit; i++) {
                sb.append("    - ").append(formatMethodSummaryBrief(representativeMethods.get(i))).append("\n");
            }
        }
        return sb.toString().trim();
    }

    public String formatPackageSummary(PackageSummaryRecord record) {
        if (record == null || record.getPackageSummary() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Package ").append(record.getPackageName()).append(": ");
        if (Texts.trimToEmpty(record.getPackageSummary().getCoreBusinessFunction()).length() > 0) {
            sb.append(Texts.trimToEmpty(record.getPackageSummary().getCoreBusinessFunction()));
        }
        if (record.getPackageSummary().getKeyBusinessEntities() != null
                && !record.getPackageSummary().getKeyBusinessEntities().isEmpty()) {
            sb.append(" Entities=")
                    .append(String.join(", ", record.getPackageSummary().getKeyBusinessEntities()));
        }
        return sb.toString().trim();
    }

    public String formatClassSummaries(List<ClassSummaryRecord> records, int limit) {
        if (records == null || records.isEmpty()) {
            return "";
        }
        int actualLimit = Math.min(limit, records.size());
        StringBuilder sb = new StringBuilder();
        sb.append("Class summaries:\n");
        for (int i = 0; i < actualLimit; i++) {
            sb.append("- ").append(formatClassSummary(records.get(i))).append("\n");
        }
        return sb.toString().trim();
    }

    public String formatMethodSummaries(List<MethodSummaryRecord> records, int limit) {
        if (records == null || records.isEmpty()) {
            return "";
        }
        int actualLimit = Math.min(limit, records.size());
        StringBuilder sb = new StringBuilder();
        sb.append("Method summaries:\n");
        for (int i = 0; i < actualLimit; i++) {
            sb.append("- ").append(formatMethodSummary(records.get(i))).append("\n");
        }
        return sb.toString().trim();
    }

    public String formatPackageSummaries(List<PackageSummaryRecord> records, int limit) {
        if (records == null || records.isEmpty()) {
            return "";
        }
        int actualLimit = Math.min(limit, records.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < actualLimit; i++) {
            sb.append("- ").append(formatPackageSummary(records.get(i))).append("\n");
        }
        return sb.toString().trim();
    }
}
