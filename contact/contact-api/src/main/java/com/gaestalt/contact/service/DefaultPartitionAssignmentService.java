/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.contact.service;

import org.springframework.stereotype.Service;

@Service
public class DefaultPartitionAssignmentService implements PartitionAssignmentService {

    @Override
    public int assignPartition(ContactPartitionContext context) {
        if ("humana".equalsIgnoreCase(context.companyName())) {
            return 1;
        }
        return 2;
    }
}
