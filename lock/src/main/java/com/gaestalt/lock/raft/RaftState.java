/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.lock.raft;

/**
 * Represents the state of a Raft node.
 */
public enum RaftState {
    /**
     * Node is a follower, accepting entries from leader.
     */
    FOLLOWER,

    /**
     * Node is a candidate, running for leader election.
     */
    CANDIDATE,

    /**
     * Node is the leader, accepting client requests.
     */
    LEADER
}
