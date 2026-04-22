package com.codewiki.tree;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe, in-memory module tree with JSON persistence.
 *
 * Tree structure mirrors the Python dict layout:
 * {
 *   "moduleName": {
 *     "components": ["file.py::Class", ...],
 *     "children": {
 *       "subModuleName": { "components": [...], "children": {} }
 *     }
 *   }
 * }
 *
 * Concurrency model
 * ──────────────────────────────────────────────────────────────────────────
 * A ReentrantReadWriteLock protects the root tree:
 *  - Multiple threads may read concurrently (getReadOnlySnapshot, save).
 *  - Only one thread may write at a time (registerTopLevelModule, registerSubModules).
 *
 * Callers must never hold a reference to the internal map and mutate it directly.
 * All structural changes go through the public write methods on this class.
 */
public class ModuleTreeManager {

    private static final Logger log = LoggerFactory.getLogger(ModuleTreeManager.class);

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, Object> root = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── write operations ──────────────────────────────────────────────────────

    /**
     * Register (or overwrite) a top-level module entry in the tree.
     * Called by the orchestration service before starting an agent.
     */
    public void registerTopLevelModule(String moduleName, List<String> componentIds) {
        lock.writeLock().lock();
        try {
            Map<String, Object> node = newNode(componentIds);
            root.put(moduleName, node);
            log.debug("Registered top-level module: {}", moduleName);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean containsTopLevelModule(String moduleName) {
        lock.readLock().lock();
        try {
            return root.containsKey(moduleName);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Atomically register all sub-module entries under the parent path.
     *
     * This is called once per GenerateSubModuleDocumentation tool invocation so
     * that the entire sibling group appears in the tree before any of the
     * parallel sub-agent tasks begin executing.  This prevents a sub-agent from
     * building its prompt against a partially-populated tree.
     *
     * @param parentPath  path from root to the parent module (empty list = root)
     * @param subModuleSpecs  map of sub-module-name -> component IDs
     */
    public void registerSubModules(List<String> parentPath,
                                    Map<String, List<String>> subModuleSpecs) {
        lock.writeLock().lock();
        try {
            Map<String, Object> parentChildren = navigateToChildren(parentPath);
            for (Map.Entry<String, List<String>> entry : subModuleSpecs.entrySet()) {
                parentChildren.put(entry.getKey(), newNode(entry.getValue()));
                log.debug("Registered sub-module: {} under path: {}", entry.getKey(), parentPath);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ── read operations ───────────────────────────────────────────────────────

    /**
     * Returns the existing children spec (child name -> component IDs) for the
     * module identified by {@code modulePath}, or {@code null} if the module has
     * no children in the tree.
     */
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> getExistingChildrenSpec(List<String> modulePath) {
        lock.readLock().lock();
        try {
            Map<String, Object> children = navigateToChildren(modulePath);
            if (children == null || children.isEmpty()) {
                return null;
            }
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : children.entrySet()) {
                Map<String, Object> childNode = (Map<String, Object>) entry.getValue();
                List<String> components = (List<String>) childNode.get("components");
                result.put(entry.getKey(), components != null ? components : List.of());
            }
            return result;
        } catch (IllegalStateException e) {
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns a deep copy of the whole tree.
     * Safe to pass to prompt builders or serialise to JSON without further locking.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getReadOnlySnapshot() {
        lock.readLock().lock();
        try {
            return deepCopy(root);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ── persistence ───────────────────────────────────────────────────────────

    /**
     * Load a previously persisted tree from a JSON file.
     * If the file does not exist this method is a no-op.
     */
    @SuppressWarnings("unchecked")
    public void loadFromFile(String docsPath, String filename) {
        Path filePath = Paths.get(docsPath, filename);
        if (!Files.exists(filePath)) {
            log.debug("Module tree file not found, starting with empty tree: {}", filePath);
            return;
        }
        lock.writeLock().lock();
        try {
            Map<String, Object> loaded = objectMapper.readValue(filePath.toFile(), Map.class);
            root.clear();
            root.putAll(loaded);
            log.info("Loaded module tree from: {}", filePath);
        } catch (IOException e) {
            log.warn("Failed to load module tree from {}: {}", filePath, e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Persist the current tree to a JSON file (pretty-printed for human readability).
     */
    public void saveToFile(String docsPath, String filename) {
        Path filePath = Paths.get(docsPath, filename);
        lock.readLock().lock();
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), root);
            log.debug("Saved module tree to: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to save module tree to {}: {}", filePath, e.getMessage());
        } finally {
            lock.readLock().unlock();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> newNode(List<String> componentIds) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("components", new ArrayList<>(componentIds));
        node.put("children", new LinkedHashMap<>());
        return node;
    }

    /**
     * Navigate the tree to the "children" map of the node identified by path.
     * An empty path returns the root map itself (treated as top-level children).
     * Caller must hold the write lock.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> navigateToChildren(List<String> path) {
        Map<String, Object> current = root;
        for (String segment : path) {
            Map<String, Object> node = (Map<String, Object>) current.get(segment);
            if (node == null) {
                throw new IllegalStateException(
                    "Module tree path not found at segment '" + segment + "' in path " + path);
            }
            current = (Map<String, Object>) node.get("children");
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> source) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(source);
            return objectMapper.readValue(bytes, Map.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deep-copy module tree", e);
        }
    }
}
