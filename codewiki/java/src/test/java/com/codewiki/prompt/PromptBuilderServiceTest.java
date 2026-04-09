package com.codewiki.prompt;

import com.codewiki.config.PromptContextProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import com.codewiki.summary.ModuleBriefBuilder;
import com.codewiki.summary.SummaryQueryService;
import com.codewiki.summary.dto.ClassSummaryRecord;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptBuilderServiceTest {

    @Test
    void prefersStructuredSummariesOverRawSource() {
        SummaryQueryService summaryQueryService = mock(SummaryQueryService.class);
        ModuleBriefBuilder briefBuilder = mock(ModuleBriefBuilder.class);
        PromptContextProperties properties = new PromptContextProperties();

        when(briefBuilder.build(any(ModuleExecutionContext.class))).thenReturn(
                new ModuleBrief(
                        "fat_module",
                        "Handles core business workflow.",
                        Collections.singletonList("FatService orchestrates requests."),
                        Collections.singletonList("FatService#run validates and dispatches."),
                        Collections.singletonList("RepoClient"),
                        Collections.<String>emptyList(),
                        true
                )
        );
        when(summaryQueryService.findClassSummariesByComponentIds(anyList())).thenReturn(
                Collections.singletonList(
                        new ClassSummaryRecord(
                                "src/huge/FatService.java::FatService",
                                "fat_module",
                                "src/huge/FatService.java",
                                "FatService",
                                "Coordinates module operations.",
                                Collections.<String>emptyList(),
                                Collections.<String>emptyList()
                        )
                )
        );
        when(summaryQueryService.findMethodSummariesByComponentIds(anyList())).thenReturn(
                Collections.singletonList(
                        new MethodSummaryRecord(
                                "src/huge/FatService.java::FatService#run",
                                "src/huge/FatService.java::FatService",
                                "src/huge/FatService.java",
                                "FatService",
                                "run",
                                "Validates inputs and dispatches the workflow.",
                                Collections.<String>emptyList()
                        )
                )
        );

        PromptBuilderService service = new PromptBuilderService(
                new TokenCounter(),
                summaryQueryService,
                briefBuilder,
                properties
        );
        ReflectionTestUtils.setField(
                service, "userPromptTemplate", new ClassPathResource("prompts/user-prompt.st"));

        String prompt = service.buildUserPrompt(buildContext("fat_module", generateLargeContent()));

        assertTrue(prompt.contains("Coordinates module operations."));
        assertTrue(prompt.contains("Validates inputs and dispatches the workflow."));
        assertFalse(prompt.contains("public void method0"));
        assertTrue(prompt.contains("<MODULE_BRIEF>"));
    }

    @Test
    void fallsBackToSourceWhenSummaryMissing() {
        SummaryQueryService summaryQueryService = mock(SummaryQueryService.class);
        ModuleBriefBuilder briefBuilder = mock(ModuleBriefBuilder.class);
        PromptContextProperties properties = new PromptContextProperties();

        when(briefBuilder.build(any(ModuleExecutionContext.class))).thenReturn(
                new ModuleBrief(
                        "fat_module",
                        "Purpose should be inferred from the module tree and source evidence.",
                        Collections.<String>emptyList(),
                        Collections.<String>emptyList(),
                        Collections.<String>emptyList(),
                        Collections.singletonList("No structured summaries were found for this module. Inspect source when behavior details matter."),
                        false
                )
        );
        when(summaryQueryService.findClassSummariesByComponentIds(anyList())).thenReturn(Collections.<ClassSummaryRecord>emptyList());
        when(summaryQueryService.findMethodSummariesByComponentIds(anyList())).thenReturn(Collections.<MethodSummaryRecord>emptyList());

        PromptBuilderService service = new PromptBuilderService(
                new TokenCounter(),
                summaryQueryService,
                briefBuilder,
                properties
        );
        ReflectionTestUtils.setField(
                service, "userPromptTemplate", new ClassPathResource("prompts/user-prompt.st"));

        String prompt = service.buildUserPrompt(buildContext("fat_module", generateLargeContent()));

        assertTrue(prompt.contains("<OPTIONAL_SOURCE_CONTEXT>"));
        assertTrue(prompt.contains("## File Content:"));
    }

    private ModuleExecutionContext buildContext(String moduleName, String content) {
        Node node = new Node(
                "src/huge/FatService.java::FatService",
                "/repo/src/huge/FatService.java",
                "src/huge/FatService.java",
                content,
                "FatService");

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
