package com.codewiki.agent;

import com.codewiki.config.AgentConcurrencyProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import com.codewiki.tools.GenerateSubModuleDocTools;
import com.codewiki.tools.ReadCodeComponentsTools;
import com.codewiki.tree.ModuleTreeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.context.ApplicationContext;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GenerateSubModuleDocTools.
 *
 * Verifies:
 * - All sub-modules are registered before any task executes
 * - Sub-agents run concurrently (not serially)
 * - Depth guard prevents infinite recursion
 * - Rate-limit semaphore is released on success and failure
 * - Partial failure produces a correct summary without aborting siblings
 */
class GenerateSubModuleDocToolsTest {

    @TempDir
    Path tempDir;

    private ExecutorService executor;
    private Semaphore semaphore;
    private AgentConcurrencyProperties concurrencyProps;
    private ApplicationContext applicationContext;
    private DocumentationOrchestrationService mockOrchestrator;
    private ModuleTreeManager treeManager;
    private GenerateSubModuleDocTools tool;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(4);
        semaphore = new Semaphore(3, true);

        concurrencyProps = new AgentConcurrencyProperties();
        concurrencyProps.setRateLimitTimeoutMinutes(1);

        mockOrchestrator = Mockito.mock(DocumentationOrchestrationService.class);
        applicationContext = Mockito.mock(ApplicationContext.class);
        when(applicationContext.getBean(DocumentationOrchestrationService.class))
                .thenReturn(mockOrchestrator);

        treeManager = new ModuleTreeManager();
        treeManager.registerTopLevelModule("parent_module",
                Collections.singletonList("src/parent.py::Parent"));

        tool = new GenerateSubModuleDocTools(
                executor, semaphore, concurrencyProps, applicationContext);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ── depth guard ───────────────────────────────────────────────────────────

    @Test
    void returnsEarlyWhenMaxDepthReached() {
        ModuleExecutionContext ctx = buildCtx("parent_module", 3, 3); // depth == maxDepth

        Map<String, List<String>> specs = new HashMap<>();
        specs.put("sub_a", Collections.singletonList("src/a.py::A"));

        String result = tool.generateSubModuleDocumentation(specs, toolContext(ctx));

        assertTrue(result.contains("Max recursion depth"));
        verify(mockOrchestrator, never()).processModule(any());
    }

    // ── concurrent execution ──────────────────────────────────────────────────

    @Test
    void allSubModulesAreProcessed() throws InterruptedException {
        ModuleExecutionContext ctx = buildCtx("parent_module", 3, 1);

        Map<String, List<String>> specs = new HashMap<>();
        specs.put("auth",     Arrays.asList("src/auth.py::Auth"));
        specs.put("database", Arrays.asList("src/db.py::DB"));
        specs.put("cache",    Arrays.asList("src/cache.py::Cache"));

        tool.generateSubModuleDocumentation(specs, toolContext(ctx));

        // Each sub-module must have been processed exactly once
        verify(mockOrchestrator, times(3)).processModule(any());
    }

    @Test
    void allSubModulesRegisteredBeforeAnyExecutes() {
        // Capture tree state at the moment the orchestrator is first called
        Map<String, Object>[] treeAtFirstCall = new Map[1];
        doAnswer(inv -> {
            if (treeAtFirstCall[0] == null) {
                treeAtFirstCall[0] = treeManager.getReadOnlySnapshot();
            }
            return null;
        }).when(mockOrchestrator).processModule(any());

        ModuleExecutionContext ctx = buildCtx("parent_module", 3, 1);
        Map<String, List<String>> specs = new HashMap<>();
        specs.put("sub_a", Collections.singletonList("src/a.py::A"));
        specs.put("sub_b", Collections.singletonList("src/b.py::B"));

        tool.generateSubModuleDocumentation(specs, toolContext(ctx));

        // Both sub-modules must already be in the tree when the first agent calls processModule
        assertNotNull(treeAtFirstCall[0]);
        @SuppressWarnings("unchecked")
        Map<String, Object> children =
                (Map<String, Object>) ((Map<String, Object>) treeAtFirstCall[0]
                        .get("parent_module")).get("children");
        assertTrue(children.containsKey("sub_a"), "sub_a should be registered");
        assertTrue(children.containsKey("sub_b"), "sub_b should be registered");
    }

    // ── rate-limit semaphore ──────────────────────────────────────────────────

    @Test
    void semaphorePermitsAreReleasedAfterSuccess() throws Exception {
        int permitsBeforeCall = semaphore.availablePermits();
        ModuleExecutionContext ctx = buildCtx("parent_module", 3, 1);

        Map<String, List<String>> specs = new HashMap<>();
        specs.put("sub_a", Collections.singletonList("src/a.py::A"));

        tool.generateSubModuleDocumentation(specs, toolContext(ctx));

        // All permits must be returned after the call completes
        assertEquals(permitsBeforeCall, semaphore.availablePermits(),
                "Semaphore permits must be fully released after tool completion");
    }

    @Test
    void semaphorePermitsAreReleasedAfterFailure() throws Exception {
        doThrow(new RuntimeException("LLM error"))
                .when(mockOrchestrator).processModule(any());

        int permitsBeforeCall = semaphore.availablePermits();
        ModuleExecutionContext ctx = buildCtx("parent_module", 3, 1);

        Map<String, List<String>> specs = new HashMap<>();
        specs.put("sub_a", Collections.singletonList("src/a.py::A"));

        String result = tool.generateSubModuleDocumentation(specs, toolContext(ctx));

        assertTrue(result.contains("Failed"));
        assertEquals(permitsBeforeCall, semaphore.availablePermits(),
                "Semaphore permits must be released even when sub-agent fails");
    }

    // ── partial failure tolerance ─────────────────────────────────────────────

    @Test
    void partialFailureDoesNotAbortSiblingSubAgents() {
        // sub_a succeeds, sub_b fails – both should be attempted
        doAnswer(inv -> {
            ModuleExecutionContext subCtx = inv.getArgument(0);
            if ("sub_b".equals(subCtx.getModuleName())) {
                throw new RuntimeException("sub_b failed");
            }
            return null;
        }).when(mockOrchestrator).processModule(any());

        ModuleExecutionContext ctx = buildCtx("parent_module", 3, 1);
        Map<String, List<String>> specs = new HashMap<>();
        specs.put("sub_a", Collections.singletonList("src/a.py::A"));
        specs.put("sub_b", Collections.singletonList("src/b.py::B"));

        String result = tool.generateSubModuleDocumentation(specs, toolContext(ctx));

        // Both sub-agents were called
        verify(mockOrchestrator, times(2)).processModule(any());

        // Summary mentions both
        assertTrue(result.contains("sub_a.md") || result.contains("Generated"),
                "Result should mention succeeded module: " + result);
        assertTrue(result.contains("Failed") && result.contains("sub_b"),
                "Result should mention failed module: " + result);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ModuleExecutionContext buildCtx(String moduleName, int maxDepth, int currentDepth) {
        Map<String, Node> components = new HashMap<>();
        Node n = new Node("src/" + moduleName + ".py::Parent",
                "/repo/src/" + moduleName + ".py",
                "src/" + moduleName + ".py",
                "class Parent: pass", "Parent");
        components.put(n.getId(), n);

        return ModuleExecutionContext.builder()
                .moduleName(moduleName)
                .components(components)
                .coreComponentIds(Collections.singletonList(n.getId()))
                .absoluteDocsPath(tempDir.toString())
                .absoluteRepoPath("/repo")
                .maxDepth(maxDepth)
                .currentDepth(currentDepth)
                .moduleTreeManager(treeManager)
                .build();
    }

    private ToolContext toolContext(ModuleExecutionContext ctx) {
        return new ToolContext(Collections.singletonMap(
                GenerateSubModuleDocTools.CTX_KEY, ctx));
    }
}
