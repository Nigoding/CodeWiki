package com.codewiki.agent;

import com.codewiki.agent.strategy.AgentStrategy;
import com.codewiki.config.AgentProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.context.ModuleExecutionContextFactory;
import com.codewiki.domain.ModuleTask;
import com.codewiki.domain.Node;
import com.codewiki.exception.DocumentationGenerationException;
import com.codewiki.repository.ModuleTreeRepository;
import com.codewiki.service.DocumentationPersistenceService;
import com.codewiki.service.ParentModuleDocumentationService;
import com.codewiki.service.PreClusterPlan;
import com.codewiki.service.PreModuleClusteringService;
import com.codewiki.summary.ModuleSummaryContextLoader;
import com.codewiki.tree.ModuleTreeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
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
    @Mock private PreModuleClusteringService preModuleClusteringService;
    @Mock private ParentModuleDocumentationService parentModuleDocumentationService;

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

        when(moduleTreeRepository.load(anyString(), anyString())).thenReturn(moduleTreeManager);
        when(preModuleClusteringService.cluster(any())).thenReturn(PreClusterPlan.empty());
        when(contextFactory.create(any(ModuleTask.class), any(ModuleTreeManager.class)))
                .thenAnswer(inv -> buildContext(inv.getArgument(0), inv.getArgument(1)));

        // complexStrategy evaluated first (@Order(1) in production)
        orchestrator = new DocumentationOrchestrationService(
                Arrays.asList(complexStrategy, leafStrategy),
                moduleTreeRepository,
                contextFactory,
                persistenceService,
                agentProperties,
                summaryContextLoader,
                preModuleClusteringService,
                parentModuleDocumentationService);
    }

    // ── strategy selection ────────────────────────────────────────────────────

    @Test
    void selectsComplexStrategyWhenItSupports() {
        when(complexStrategy.supports(any())).thenReturn(true);
        when(complexStrategy.execute(any())).thenReturn(new AgentExecutionResult("content", false, false));

        orchestrator.processModule(buildTask("auth_module"));

        verify(complexStrategy).execute(any());
        verify(leafStrategy, never()).execute(any());
    }

    @Test
    void fallsBackToLeafWhenComplexDoesNotSupport() {
        when(complexStrategy.supports(any())).thenReturn(false);
        when(leafStrategy.supports(any())).thenReturn(true);
        when(leafStrategy.execute(any())).thenReturn(new AgentExecutionResult("content", false, false));

        orchestrator.processModule(buildTask("simple_module"));

        verify(leafStrategy).execute(any());
        verify(complexStrategy, never()).execute(any());
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void skipsModuleWhenDocFileAlreadyExists() throws IOException {
        String moduleName = "existing_module";
        Files.write(tempDir.resolve(moduleName + ".md"), "already done".getBytes());

        orchestrator.processModule(buildTask(moduleName));

        verify(complexStrategy, never()).execute(any());
        verify(leafStrategy, never()).execute(any());
    }

    @Test
    void skipsWhenOverviewFileAlreadyExists() throws IOException {
        Files.write(tempDir.resolve("overview.md"), "already done".getBytes());

        orchestrator.processModule(buildTask("any_module"));

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
                .thenReturn(new AgentExecutionResult("content", false, true));

        orchestrator.processModule(buildTask("flaky_module"));

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
                () -> orchestrator.processModule(buildTask("broken_module")));
    }

    // ── persistence ───────────────────────────────────────────────────────────

    @Test
    void savesModuleTreeAfterSuccessfulRun() {
        when(complexStrategy.supports(any())).thenReturn(true);
        when(complexStrategy.execute(any())).thenReturn(new AgentExecutionResult("content", false, false));

        orchestrator.processModule(buildTask("some_module"));

        verify(persistenceService).persist(any(ModuleTask.class), any(ModuleExecutionContext.class),
                any(AgentExecutionResult.class), any(ModuleTreeManager.class));
    }

    @Test
    void persistsClusterPlanImmediatelyAfterRegisteringSubModules() {
        when(preModuleClusteringService.cluster(any())).thenReturn(
                PreClusterPlan.of(Collections.singletonMap(
                        "child_module",
                        Collections.singletonList("src/parent_module.py::SomeClass")
                )));
        doReturn(true).when(persistenceService).moduleDocExists(anyString(), eq("child_module"));

        orchestrator.processModule(buildTask("parent_module"));

        ArgumentCaptor<ModuleTreeManager> treeCaptor = ArgumentCaptor.forClass(ModuleTreeManager.class);
        verify(moduleTreeRepository, atLeastOnce())
                .save(eq(tempDir.toString()), eq("module_tree.json"), treeCaptor.capture());
        verify(parentModuleDocumentationService)
                .generate(any(ModuleExecutionContext.class),
                        eq(Collections.singletonList("child_module")),
                        any(ModuleTreeManager.class));

        Map<String, Object> snapshot = treeCaptor.getValue().getReadOnlySnapshot();
        @SuppressWarnings("unchecked")
        Map<String, Object> parentNode = (Map<String, Object>) snapshot.get("parent_module");
        assertNotNull(parentNode);
        @SuppressWarnings("unchecked")
        Map<String, Object> children = (Map<String, Object>) parentNode.get("children");
        assertTrue(children.containsKey("child_module"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ModuleTask buildTask(String moduleName) {
        Map<String, Node> components = new HashMap<>();
        Node node = new Node(
                "src/" + moduleName + ".py::SomeClass",
                "/repo/src/" + moduleName + ".py",
                "src/" + moduleName + ".py",
                "class SomeClass: pass",
                "SomeClass");
        components.put(node.getId(), node);

        return new ModuleTask(
                moduleName,
                components,
                Collections.singletonList(node.getId()),
                Collections.<String>emptyList(),
                tempDir.toString(),
                "/repo",
                3,
                1,
                null,
                Collections.<String>emptyList());
    }

    private ModuleExecutionContext buildContext(ModuleTask task, ModuleTreeManager treeManager) {
        return ModuleExecutionContext.builder()
                .moduleName(task.getModuleName())
                .components(task.getComponents())
                .coreComponentIds(task.getCoreComponentIds())
                .modulePath(task.getModulePath())
                .absoluteDocsPath(task.getDocsPath())
                .absoluteRepoPath(task.getRepoPath())
                .maxDepth(task.getMaxDepth())
                .currentDepth(task.getCurrentDepth())
                .customInstructions(task.getCustomInstructions())
                .moduleTreeManager(treeManager)
                .build();
    }
}
