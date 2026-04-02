package com.codewiki;

import com.codewiki.agent.DocumentationOrchestrationService;
import com.codewiki.config.AgentProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import com.codewiki.repository.ModuleTreeRepository;
import com.codewiki.tree.ModuleTreeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application entry point.
 *
 * In production the orchestration service is driven by an HTTP controller
 * or a batch job.  This main class includes a minimal programmatic example
 * showing how to wire the pieces together, which also serves as a smoke test.
 */
@SpringBootApplication
@EnableConfigurationProperties
public class CodeWikiApplication {

    private static final Logger log = LoggerFactory.getLogger(CodeWikiApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(CodeWikiApplication.class, args);

        // ── Example: process a single top-level module ────────────────────────
        // In a real integration this block lives in a REST controller or CLI runner.
        DocumentationOrchestrationService orchestrator =
                ctx.getBean(DocumentationOrchestrationService.class);
        ModuleTreeManager treeManager =
                ctx.getBean(ModuleTreeManager.class);
        ModuleTreeRepository treeRepo =
                ctx.getBean(ModuleTreeRepository.class);
        AgentProperties agentProps =
                ctx.getBean(AgentProperties.class);

        // -- Build sample components map (replace with your actual parser output) --
        Map<String, Node> components = buildSampleComponents();

        // -- Optional: restore a previously saved tree from disk --
        String docsPath  = System.getProperty("codewiki.docsPath",  "/tmp/codewiki-docs");
        String repoPath  = System.getProperty("codewiki.repoPath",  "/tmp/my-repo");
        treeRepo.load(docsPath, agentProps.getModuleTreeFilename(), treeManager);

        // -- Register the top-level module in the tree --
        List<String> coreIds = new ArrayList<>(components.keySet());
        treeManager.registerTopLevelModule("sample_module", coreIds);

        // -- Build execution context --
        ModuleExecutionContext execCtx = ModuleExecutionContext.builder()
                .moduleName("sample_module")
                .components(components)
                .coreComponentIds(coreIds)
                .modulePath(new ArrayList<>())
                .absoluteDocsPath(docsPath)
                .absoluteRepoPath(repoPath)
                .maxDepth(agentProps.getMaxDepth())
                .currentDepth(1)
                .moduleTreeManager(treeManager)
                .build();

        // -- Run --
        try {
            orchestrator.processModule(execCtx);
            log.info("Documentation generation complete. Output: {}", docsPath);
        } catch (Exception e) {
            log.error("Documentation generation failed", e);
        }
    }

    private static Map<String, Node> buildSampleComponents() {
        Map<String, Node> map = new HashMap<>();
        Node n = new Node(
                "src/sample/module.py::SampleClass",
                "/tmp/my-repo/src/sample/module.py",
                "src/sample/module.py",
                "class SampleClass:\n    pass\n",
                "SampleClass");
        map.put(n.getId(), n);
        return map;
    }
}
