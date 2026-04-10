package com.codewiki.summary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.ClassSummary;
import com.codewiki.summary.dto.MethodSummary;
import com.codewiki.summary.dto.MethodSummaryRecord;
import com.codewiki.summary.dto.PackageSummary;
import com.codewiki.summary.dto.PackageSummaryRecord;
import com.codewiki.summary.persistence.SummaryEntity;
import com.codewiki.summary.persistence.SummaryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DatabaseSummaryQueryService implements SummaryQueryService {

    private final SummaryMapper summaryMapper;
    private final ObjectMapper objectMapper;

    public DatabaseSummaryQueryService(SummaryMapper summaryMapper, ObjectMapper objectMapper) {
        this.summaryMapper = summaryMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ClassSummaryRecord findClassSummary(String projectName, String componentId) {
        SummaryEntity entity = summaryMapper.selectOne(
                new LambdaQueryWrapper<SummaryEntity>()
                        .eq(SummaryEntity::getProjectName, projectName)
                        .eq(SummaryEntity::getElementNameHash, SummaryElementNames.md5(componentId))
        );

        if (entity == null) {
            return null;
        }

        ClassSummary summary = parse(entity.getElementSummary(), ClassSummary.class);
        return new ClassSummaryRecord(
                componentId,
                projectName,
                null,
                SummaryElementNames.simpleClassName(componentId),
                summary,
                entity.getElementSummary()
        );
    }

    @Override
    public List<ClassSummaryRecord> findClassSummaries(String projectName, List<String> componentIds) {
        if (componentIds == null || componentIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ClassSummaryRecord> results = new ArrayList<ClassSummaryRecord>();
        for (String componentId : componentIds) {
            ClassSummaryRecord record = findClassSummary(projectName, componentId);
            if (record != null) {
                results.add(record);
            }
        }
        return results;
    }

    @Override
    public MethodSummaryRecord findMethodSummaryByFqn(String projectName, String methodFqn) {
        SummaryEntity entity = summaryMapper.selectOne(
                new LambdaQueryWrapper<SummaryEntity>()
                        .eq(SummaryEntity::getProjectName, projectName)
                        .eq(SummaryEntity::getElementNameHash, SummaryElementNames.md5(methodFqn))
        );

        if (entity == null) {
            return null;
        }

        MethodSummary summary = parse(entity.getElementSummary(), MethodSummary.class);
        String ownerClass = extractOwnerClass(methodFqn);
        return new MethodSummaryRecord(
                entity.getElementName(),
                ownerClass,
                null,
                SummaryElementNames.simpleClassName(ownerClass),
                SummaryElementNames.extractMethodName(entity.getElementName()),
                summary,
                entity.getElementSummary()
        );
    }

    @Override
    public List<MethodSummaryRecord> findMethodSummariesByFqns(String projectName, List<String> methodFqns) {
        if (methodFqns == null || methodFqns.isEmpty()) {
            return Collections.emptyList();
        }

        List<MethodSummaryRecord> results = new ArrayList<MethodSummaryRecord>();
        for (String methodFqn : methodFqns) {
            MethodSummaryRecord record = findMethodSummaryByFqn(projectName, methodFqn);
            if (record != null) {
                results.add(record);
            }
        }
        return results;
    }

    @Override
    public List<MethodSummaryRecord> findMethodSummariesByClass(String projectName, String componentId) {
        List<SummaryEntity> entities = summaryMapper.selectList(
                new LambdaQueryWrapper<SummaryEntity>()
                        .eq(SummaryEntity::getProjectName, projectName)
                        .likeRight(SummaryEntity::getElementName, SummaryElementNames.toMethodPrefix(componentId))
        );

        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        List<MethodSummaryRecord> results = new ArrayList<MethodSummaryRecord>();
        for (SummaryEntity entity : entities) {
            MethodSummary summary = parse(entity.getElementSummary(), MethodSummary.class);
            results.add(new MethodSummaryRecord(
                    entity.getElementName(),
                    componentId,
                    null,
                    SummaryElementNames.simpleClassName(componentId),
                    SummaryElementNames.extractMethodName(entity.getElementName()),
                    summary,
                    entity.getElementSummary()
            ));
        }
        return results;
    }

    @Override
    public List<MethodSummaryRecord> findMethodSummaries(String projectName, List<String> componentIds) {
        if (componentIds == null || componentIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<MethodSummaryRecord> results = new ArrayList<MethodSummaryRecord>();
        for (String componentId : componentIds) {
            results.addAll(findMethodSummariesByClass(projectName, componentId));
        }
        return results;
    }

    @Override
    public MethodSummaryRecord findMethodSummary(String projectName,
                                                 String componentId,
                                                 String methodName,
                                                 String methodSignature) {
        String elementName = SummaryElementNames.toMethodElementName(componentId, methodName, methodSignature);
        SummaryEntity entity = summaryMapper.selectOne(
                new LambdaQueryWrapper<SummaryEntity>()
                        .eq(SummaryEntity::getProjectName, projectName)
                        .eq(SummaryEntity::getElementNameHash, SummaryElementNames.md5(elementName))
        );

        if (entity == null) {
            return null;
        }

        MethodSummary summary = parse(entity.getElementSummary(), MethodSummary.class);
        return new MethodSummaryRecord(
                entity.getElementName(),
                componentId,
                null,
                SummaryElementNames.simpleClassName(componentId),
                methodName,
                summary,
                entity.getElementSummary()
        );
    }

    @Override
    public PackageSummaryRecord findPackageSummary(String projectName, String packageFqn) {
        if (packageFqn == null || packageFqn.isEmpty()) {
            return null;
        }

        SummaryEntity entity = summaryMapper.selectOne(
                new LambdaQueryWrapper<SummaryEntity>()
                        .eq(SummaryEntity::getProjectName, projectName)
                        .eq(SummaryEntity::getElementNameHash, SummaryElementNames.md5(packageFqn))
        );

        if (entity == null) {
            return null;
        }

        PackageSummary summary = parse(entity.getElementSummary(), PackageSummary.class);
        return new PackageSummaryRecord(projectName, packageFqn, summary);
    }

    @Override
    public List<PackageSummaryRecord> findPackageSummaries(String projectName, List<String> packageFqns) {
        if (packageFqns == null || packageFqns.isEmpty()) {
            return Collections.emptyList();
        }

        List<PackageSummaryRecord> results = new ArrayList<PackageSummaryRecord>();
        for (String packageFqn : packageFqns) {
            PackageSummaryRecord record = findPackageSummary(projectName, packageFqn);
            if (record != null) {
                results.add(record);
            }
        }
        return results;
    }

    @Override
    public PackageSummaryRecord findPackageSummaryByClass(String projectName, String componentId) {
        return findPackageSummary(projectName, SummaryElementNames.extractPackageName(componentId));
    }

    private <T> T parse(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse " + clazz.getSimpleName() + " JSON", e);
        }
    }

    private String extractOwnerClass(String methodFqn) {
        int idx = methodFqn.indexOf('#');
        return idx < 0 ? methodFqn : methodFqn.substring(0, idx);
    }
}
