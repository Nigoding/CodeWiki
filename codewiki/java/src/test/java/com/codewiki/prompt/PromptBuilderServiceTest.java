package com.codewiki.prompt;

import com.codewiki.config.PromptContextProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import com.codewiki.summary.ModuleBriefBuilder;
import com.codewiki.summary.ModuleSummaryContext;
import com.codewiki.summary.ModuleSummaryContextLoader;
import com.codewiki.summary.SummaryFormatter;
import com.codewiki.summary.dto.ClassSummary;
import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.MethodSummary;
import com.codewiki.summary.dto.MethodSummaryRecord;
import com.codewiki.summary.dto.ModuleBrief;
import com.codewiki.tree.ModuleTreeManager;
import com.codewiki.util.TokenCounter;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptBuilderServiceTest {

    @Test
    void prefersCoreComponentSummariesOverRawSource() {
        ModuleBriefBuilder briefBuilder = mock(ModuleBriefBuilder.class);
        ModuleSummaryContextLoader summaryContextLoader = mock(ModuleSummaryContextLoader.class);
        PromptContextProperties properties = new PromptContextProperties();
        SummaryFormatter summaryFormatter = new SummaryFormatter();

        when(briefBuilder.build(any(ModuleExecutionContext.class))).thenReturn(
                new ModuleBrief(
                        "fat_module",
                        "Handles core business workflow.",
                        "Manage business workflow execution.",
                        Collections.singletonList("Coordinate workflow execution"),
                        Collections.singletonList("FatService"),
                        Collections.emptyList(),
                        Collections.singletonList("Persist audit log"),
                        Collections.<String>emptyList(),
                        true
                )
        );

        ClassSummary classSummary = new ClassSummary();
        classSummary.setRole("Workflow coordinator");
        classSummary.setKeyFunctionality("Coordinates module operations.");
        classSummary.setPurpose("Ensure the workflow is executed correctly.");

        MethodSummary methodSummary = new MethodSummary();
        methodSummary.setFunctionName("run");
        methodSummary.setPurpose("Execute the workflow.");
        methodSummary.setFinalSummary("Validates inputs and dispatches the workflow.");
        methodSummary.setSideEffects(Collections.singletonList("Persist audit log"));

        ModuleSummaryContext summaryContext = new ModuleSummaryContext(
                Collections.emptyList(),
                Collections.singletonList(
                        new ClassSummaryRecord(
                                "com.example.huge.FatService",
                                "fat_module",
                                "src/huge/FatService.java",
                                "FatService",
                                classSummary,
                                "{\"role\":\"Workflow coordinator\"}"
                        )
                ),
                Collections.singletonList(
                        new MethodSummaryRecord(
                                "com.example.huge.FatService#run()",
                                "com.example.huge.FatService",
                                "src/huge/FatService.java",
                                "FatService",
                                "run",
                                methodSummary,
                                "{\"functionName\":\"run\"}"
                        )
                )
        );
        when(summaryContextLoader.load(any(ModuleExecutionContext.class))).thenReturn(summaryContext);

        PromptBuilderService service = new PromptBuilderService(
                new TokenCounter(),
                summaryContextLoader,
                briefBuilder,
                properties,
                summaryFormatter
        );
        ReflectionTestUtils.setField(
                service, "userPromptTemplate", new ClassPathResource("prompts/user-prompt.st"));

        String prompt = service.buildUserPrompt(buildContext("fat_module", generateLargeContent()));

        assertTrue(prompt.contains("<MODULE_CONTEXT>"));
        assertTrue(prompt.contains("<MODULE_BRIEF>"));
        assertTrue(prompt.contains("<CORE_COMPONENT_SUMMARIES>"));
        assertTrue(prompt.contains("<CONTEXT_GAPS>"));
        assertTrue(prompt.contains("Coordinates module operations."));
        assertTrue(prompt.contains("FQN：com.example.huge.FatService"));
        assertTrue(prompt.contains("文件：src/huge/FatService.java"));
        assertTrue(prompt.contains("可按需召回的方法"));
        assertTrue(prompt.contains("run()"));
        assertFalse(prompt.contains("Validates inputs and dispatches the workflow."));
        assertFalse(prompt.contains("public void method0"));
        assertFalse(prompt.contains("Current summaries cover the main core components."));
    }

    @Test
    void fallsBackToSourceWhenSummaryMissing() {
        ModuleBriefBuilder briefBuilder = mock(ModuleBriefBuilder.class);
        ModuleSummaryContextLoader summaryContextLoader = mock(ModuleSummaryContextLoader.class);
        PromptContextProperties properties = new PromptContextProperties();
        SummaryFormatter summaryFormatter = new SummaryFormatter();

        when(briefBuilder.build(any(ModuleExecutionContext.class))).thenReturn(
                new ModuleBrief(
                        "fat_module",
                        "Module purpose should be inferred from the module tree and source evidence.",
                        "",
                        Collections.<String>emptyList(),
                        Collections.<String>emptyList(),
                        Collections.<String>emptyList(),
                        Collections.<String>emptyList(),
                        Collections.singletonList("No structured summaries were found for this module. Inspect source when behavior details matter."),
                        false
                )
        );
        when(summaryContextLoader.load(any(ModuleExecutionContext.class))).thenReturn(
                new ModuleSummaryContext(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));

        PromptBuilderService service = new PromptBuilderService(
                new TokenCounter(),
                summaryContextLoader,
                briefBuilder,
                properties,
                summaryFormatter
        );
        ReflectionTestUtils.setField(
                service, "userPromptTemplate", new ClassPathResource("prompts/user-prompt.st"));

        String prompt = service.buildUserPrompt(buildContext("fat_module", generateLargeContent()));

        assertTrue(prompt.contains("<OPTIONAL_SOURCE_CONTEXT>"));
        assertTrue(prompt.contains("## 文件内容："));
    }

    private ModuleExecutionContext buildContext(String moduleName, String content) {
        Node node = new Node(
                "src/huge/FatService.java::FatService",
                "/repo/src/huge/FatService.java",
                "src/huge/FatService.java",
                content,
                "FatService");
        node.setClassFqn("com.example.huge.FatService");
        node.setMethodFqns(Collections.singletonList("com.example.huge.FatService#run()"));
        node.setPackageFqns(Collections.singletonList("com.example.huge"));

        Map<String, Node> components = new HashMap<String, Node>();
        components.put(node.getId(), node);

        ModuleTreeManager treeManager = new ModuleTreeManager();
        treeManager.registerTopLevelModule(moduleName, Collections.singletonList(node.getId()));

        return ModuleExecutionContext.builder()
                .moduleName(moduleName)
                .components(components)
                .coreComponentIds(Collections.singletonList(node.getId()))
                .absoluteDocsPath("/tmp/docs")
                .absoluteRepoPath("/repo")
                .maxDepth(3)
                .currentDepth(1)
                .moduleTreeManager(treeManager)
                .build();
    }

    private String generateLargeContent() {
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            largeContent.append("public void method").append(i).append("() { System.out.println(\"x\"); }\n");
        }
        return largeContent.toString();
    }
}
