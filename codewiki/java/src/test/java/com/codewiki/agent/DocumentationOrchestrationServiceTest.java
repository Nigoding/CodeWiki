package com.codewiki.agent;

import com.codewiki.agent.strategy.AgentStrategy;
import com.codewiki.config.AgentProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.context.ModuleExecutionContextFactory;
import com.codewiki.domain.Node;
import com.codewiki.exception.DocumentationGenerationException;
import com.codewiki.repository.ModuleTreeRepository;
import com.codewiki.service.DocumentationPersistenceService;
import com.codewiki.summary.ModuleSummaryContextLoader;
import com.codewiki.tree.ModuleTreeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DocumentationOrchestrationService.
 *
 * All LLM calls are mocked; these tests verify orchestration logic:
 * - strategy selection order
 * - idempotency (skip if output already exists)
 * - primary → fallback escalation
 * - persistence after successful run
 */
@ExtendWith(MockitoExtension.class)
class DocumentationOrchestrationServiceTest {

    @TempDir
    Path tempDir;

    @Mock private AgentStrategy complexStrategy;
    @Mock private AgentStrategy leafStrategy;
    @Mock private ModuleTreeRepository moduleTreeRepository;
    @Mock private ModuleExecutionContextFactory contextFactory;
    @Mock private DocumentationPersistenceService persistenceService;
    @Mock private ModuleSummaryContextLoader summaryContextLoader;

    private ModuleTreeManager moduleTreeManager;
    private AgentProperties agentProperties;
    private DocumentationOrchestrationService orchestrator;

    @BeforeEach
    void setUp() {
        moduleTreeManager = new ModuleTreeManager();
        agentProperties = new AgentProperties();
        // Use TempDir so idempotency checks work against real filesystem
        agentProperties.setModuleTreeFilename("module_tree.json");
        agentProperties.setOverviewFilename("overview.md");

        // complexStrategy evaluated first (@Order(1) in production)
        orchestrator = new DocumentationOrchestrationService(
                Arrays.asList(complexStrategy, leafStrategy),
                moduleTreeRepository,
                contextFactory,
                persistenceService,
                agentProperties,
                summaryContextLoader);
    }

    // ── strategy selection ────────────────────────────────────────────────────

    @Test
    void selectsComplexStrategyWhenItSupports() {
        when(complexStrategy.supports(any())).thenReturn(true);
        when(complexStrategy.execute(any())).thenReturn(Collections.emptyMap());

        orchestrator.processModule(buildCtx("auth_module"));

        verify(complexStrategy).execute(any());
        verify(leafStrategy, never()).execute(any());
    }

    @Test
    void fallsBackToLeafWhenComplexDoesNotSupport() {
        when(complexStrategy.supports(any())).thenReturn(false);
        when(leafStrategy.supports(any())).thenReturn(true);
        when(leafStrategy.execute(any())).thenReturn(Collections.emptyMap());

        orchestrator.processModule(buildCtx("simple_module"));

        verify(leafStrategy).execute(any());
        verify(complexStrategy, never()).execute(any());
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void skipsModuleWhenDocFileAlreadyExists() throws IOException {
        String moduleName = "existing_module";
        Files.write(tempDir.resolve(moduleName + ".md"), "already done".getBytes());

        orchestrator.processModule(buildCtx(moduleName));

        verify(complexStrategy, never()).execute(any());
        verify(leafStrategy, never()).execute(any());
    }

    @Test
    void skipsWhenOverviewFileAlreadyExists() throws IOException {
        Files.write(tempDir.resolve("overview.md"), "already done".getBytes());

        orchestrator.processModule(buildCtx("any_module"));

        verify(complexStrategy, never()).execute(any());
        verify(leafStrategy, never()).execute(any());
    }

    // ── fallback escalation ───────────────────────────────────────────────────

    @Test
    void switchesToFallbackModelOnPrimaryFailure() {
        when(complexStrategy.supports(any())).thenReturn(true);
        when(complexStrategy.execute(any()))
                .thenThrow(new RuntimeException("primary model timeout"));
        when(complexStrategy.executeWithFallback(any()))
                .thenReturn(Collections.emptyMap());

        orchestrator.processModule(buildCtx("flaky_module"));

        verify(complexStrategy).execute(any());
        verify(complexStrategy).executeWithFallback(any());
    }

    @Test
    void throwsDocumentationGenerationExceptionWhenBothModelsFail() {
        when(complexStrategy.supports(any())).thenReturn(true);
        when(complexStrategy.execute(any()))
                .thenThrow(new RuntimeException("primary failed"));
        when(complexStrategy.executeWithFallback(any()))
                .thenThrow(new RuntimeException("fallback failed"));

        assertThrows(DocumentationGenerationException.class,
                () -> orchestrator.processModule(buildCtx("broken_module")));
    }

    // ── persistence ───────────────────────────────────────────────────────────

    @Test
    void savesModuleTreeAfterSuccessfulRun() {
        when(complexStrategy.supports(any())).thenReturn(true);
        when(complexStrategy.execute(any())).thenReturn(Collections.emptyMap());

        orchestrator.processModule(buildCtx("some_module"));

        verify(moduleTreeRepository).save(
                eq(tempDir.toString()),
                eq("module_tree.json"),
                any(ModuleTreeManager.class));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ModuleExecutionContext buildCtx(String moduleName) {
        Map<String, Node> components = new HashMap<>();
        Node node = new Node(
                "src/" + moduleName + ".py::SomeClass",
                "/repo/src/" + moduleName + ".py",
                "src/" + moduleName + ".py",
                "class SomeClass: pass",
                "SomeClass");
        components.put(node.getId(), node);

        return ModuleExecutionContext.builder()
                .moduleName(moduleName)
                .components(components)
                .coreComponentIds(Collections.singletonList(node.getId()))
                .absoluteDocsPath(tempDir.toString())
                .absoluteRepoPath("/repo")
                .maxDepth(3)
                .currentDepth(1)
                .moduleTreeManager(moduleTreeManager)
                .build();
    }
}
