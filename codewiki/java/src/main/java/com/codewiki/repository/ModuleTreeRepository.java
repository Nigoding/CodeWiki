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

    public ModuleTreeManager load(String docsPath, String filename) {
        ModuleTreeManager manager = new ModuleTreeManager();
        manager.loadFromFile(docsPath, filename);
        return manager;
    }

    public void save(String docsPath, String filename, ModuleTreeManager manager) {
        manager.saveToFile(docsPath, filename);
    }
}
