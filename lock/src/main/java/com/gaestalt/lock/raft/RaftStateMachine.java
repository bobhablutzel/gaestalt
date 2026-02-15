/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.lock.raft;

import com.gaestalt.lock.model.Lock;
import com.gaestalt.lock.model.LockResult;
import com.gaestalt.lock.service.LockStore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Raft state machine for applying committed log entries to the lock store.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaftStateMachine {

    private final LockStore lockStore;
    @Getter
    private long lastAppliedIndex = 0;

    /**
     * Applies a committed log entry to the state machine.
     *
     * @param entry    The entry to apply
     * @param callback Optional callback with the result
     */
    public void apply(LogEntry entry, Consumer<LockResult<?>> callback) {
        if (entry.index() <= lastAppliedIndex) {
            log.debug("Skipping already applied entry at index {}", entry.index());
            return;
        }

        log.debug("Applying entry {} of type {}", entry.index(), entry.type());

        LockResult<?> result = switch (entry.type()) {
            case ACQUIRE_LOCK -> applyAcquireLock(entry);
            case RELEASE_LOCK -> applyReleaseLock(entry);
            case EXTEND_LOCK -> applyExtendLock(entry);
            case NOOP -> LockResult.success(null);
        };

        lastAppliedIndex = entry.index();

        if (callback != null) {
            callback.accept(result);
        }
    }

    /**
     * Applies an acquire lock command.
     */
    private LockResult<Lock> applyAcquireLock(LogEntry entry) {
        var command = entry.getCommand();
        if (command == null) {
            return LockResult.failure(
                    com.gaestalt.lock.model.LockStatus.ERROR,
                    "Invalid command data"
            );
        }

        return lockStore.acquireWithToken(
                command.lockId(),
                command.clientId(),
                command.regionId(),
                command.fencingToken(),
                command.expiresAt()
        );
    }

    /**
     * Applies a release lock command.
     */
    private LockResult<Void> applyReleaseLock(LogEntry entry) {
        var command = entry.getCommand();
        if (command == null) {
            return LockResult.failure(
                    com.gaestalt.lock.model.LockStatus.ERROR,
                    "Invalid command data"
            );
        }

        return lockStore.releaseByToken(command.lockId(), command.fencingToken());
    }

    /**
     * Applies an extend lock command.
     */
    private LockResult<Void> applyExtendLock(LogEntry entry) {
        // Extension would be implemented similarly
        return LockResult.success(null);
    }

    /**
     * Resets the state machine (for testing).
     */
    public void reset() {
        lastAppliedIndex = 0;
        lockStore.clear();
    }
}
