/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.contact.repository;

import com.gaestalt.contact.entity.AddressKind;
import com.gaestalt.contact.entity.ContactPhone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactPhoneRepository extends JpaRepository<ContactPhone, Long> {

    List<ContactPhone> findByContactId(Long contactId);

    Optional<ContactPhone> findByContactIdAndPhoneType(Long contactId, AddressKind phoneType);
}
