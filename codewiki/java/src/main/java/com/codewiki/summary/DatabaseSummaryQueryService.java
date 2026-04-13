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
import java.util.Map;
import java.util.stream.Collectors;

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

        List<String> hashes = componentIds.stream()
                .map(SummaryElementNames::md5)
                .distinct()
                .collect(Collectors.toList());
        List<SummaryEntity> entities = summaryMapper.selectList(
                new LambdaQueryWrapper<SummaryEntity>()
                        .eq(SummaryEntity::getProjectName, projectName)
                        .in(SummaryEntity::getElementNameHash, hashes)
        );
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, SummaryEntity> entityMap = entities.stream()
                .collect(Collectors.toMap(SummaryEntity::getElementNameHash, e -> e, (a, b) -> a));
        List<ClassSummaryRecord> results = new ArrayList<ClassSummaryRecord>();
        for (String componentId : componentIds) {
            SummaryEntity entity = entityMap.get(SummaryElementNames.md5(componentId));
            if (entity != null) {
                ClassSummary summary = parse(entity.getElementSummary(), ClassSummary.class);
                results.add(new ClassSummaryRecord(
                        componentId,
                        projectName,
                        null,
                        SummaryElementNames.simpleClassName(componentId),
                        summary,
                        entity.getElementSummary()
                ));
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

        List<String> hashes = methodFqns.stream()
                .map(SummaryElementNames::md5)
                .distinct()
                .collect(Collectors.toList());
        List<SummaryEntity> entities = summaryMapper.selectList(
                new LambdaQueryWrapper<SummaryEntity>()
                        .eq(SummaryEntity::getProjectName, projectName)
                        .in(SummaryEntity::getElementNameHash, hashes)
        );
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, SummaryEntity> entityMap = entities.stream()
                .collect(Collectors.toMap(SummaryEntity::getElementNameHash, e -> e, (a, b) -> a));
        List<MethodSummaryRecord> results = new ArrayList<MethodSummaryRecord>();
        for (String methodFqn : methodFqns) {
            SummaryEntity entity = entityMap.get(SummaryElementNames.md5(methodFqn));
            if (entity != null) {
                MethodSummary summary = parse(entity.getElementSummary(), MethodSummary.class);
                String ownerClass = extractOwnerClass(methodFqn);
                results.add(new MethodSummaryRecord(
                        entity.getElementName(),
                        ownerClass,
                        null,
                        SummaryElementNames.simpleClassName(ownerClass),
                        SummaryElementNames.extractMethodName(entity.getElementName()),
                        summary,
                        entity.getElementSummary()
                ));
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

        LambdaQueryWrapper<SummaryEntity> wrapper = new LambdaQueryWrapper<SummaryEntity>()
                .eq(SummaryEntity::getProjectName, projectName);
        for (int i = 0; i < componentIds.size(); i++) {
            String prefix = SummaryElementNames.toMethodPrefix(componentIds.get(i));
            if (i == 0) {
                wrapper.likeRight(SummaryEntity::getElementName, prefix);
            } else {
                wrapper.or().likeRight(SummaryEntity::getElementName, prefix);
            }
        }

        List<SummaryEntity> entities = summaryMapper.selectList(wrapper);
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        List<MethodSummaryRecord> results = new ArrayList<MethodSummaryRecord>();
        for (SummaryEntity entity : entities) {
            MethodSummary summary = parse(entity.getElementSummary(), MethodSummary.class);
            String ownerClass = extractOwnerClass(entity.getElementName());
            results.add(new MethodSummaryRecord(
                    entity.getElementName(),
                    ownerClass,
                    null,
                    SummaryElementNames.simpleClassName(ownerClass),
                    SummaryElementNames.extractMethodName(entity.getElementName()),
                    summary,
                    entity.getElementSummary()
            ));
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

        List<String> hashes = packageFqns.stream()
                .map(SummaryElementNames::md5)
                .distinct()
                .collect(Collectors.toList());
        List<SummaryEntity> entities = summaryMapper.selectList(
                new LambdaQueryWrapper<SummaryEntity>()
                        .eq(SummaryEntity::getProjectName, projectName)
                        .in(SummaryEntity::getElementNameHash, hashes)
        );
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, SummaryEntity> entityMap = entities.stream()
                .collect(Collectors.toMap(SummaryEntity::getElementNameHash, e -> e, (a, b) -> a));
        List<PackageSummaryRecord> results = new ArrayList<PackageSummaryRecord>();
        for (String packageFqn : packageFqns) {
            SummaryEntity entity = entityMap.get(SummaryElementNames.md5(packageFqn));
            if (entity != null) {
                PackageSummary summary = parse(entity.getElementSummary(), PackageSummary.class);
                results.add(new PackageSummaryRecord(projectName, packageFqn, summary));
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
