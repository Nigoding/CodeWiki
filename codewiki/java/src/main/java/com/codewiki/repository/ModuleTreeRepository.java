package com.codewiki.repository;

import com.codewiki.tree.ModuleTreeManager;
import org.springframework.stereotype.Component;

/**
 * Thin persistence facade for the module tree.
 *
 * Delegates to ModuleTreeManager which owns both the in-memory state and the
 * locking strategy.  Keeping a separate repository class decouples the
 * orchestration service from the storage mechanism – the implementation could
 * later be swapped out for a database-backed store without touching business logic.
 */
@Component
public class ModuleTreeRepository {

    /**
     * Persist the current state of the module tree to the given file.
     *
     * @param docsPath absolute path to the documentation output directory
     * @param filename the JSON file name, e.g. "module_tree.json"
     * @param manager  the module tree manager holding the in-memory state
     */
    public void save(String docsPath, String filename, ModuleTreeManager manager) {
        manager.saveToFile(docsPath, filename);
    }

    /**
     * Load a previously persisted tree into the given manager.
     * No-op if the file does not exist.
     */
    public void load(String docsPath, String filename, ModuleTreeManager manager) {
        manager.loadFromFile(docsPath, filename);
    }
}
