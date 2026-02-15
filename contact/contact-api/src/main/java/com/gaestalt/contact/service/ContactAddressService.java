/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.contact.service;

import com.gaestalt.contact.entity.AddressKind;
import com.gaestalt.contact.entity.Contact;
import com.gaestalt.contact.entity.ContactAddress;
import com.gaestalt.contact.entity.StreetAddress;
import com.gaestalt.contact.repository.ContactAddressRepository;
import com.gaestalt.contact.repository.ContactRepository;
import com.gaestalt.contact.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactAddressService {

    private final ContactRepository contactRepository;
    private final ContactAddressRepository contactAddressRepository;
    private final AddressRepository addressRepository;

    @Transactional
    public ContactAddress addAddressToContact(Long contactId, Long addressId, AddressKind addressType) {
        log.info("Adding address {} of type {} to contact {}", addressId, addressType, contactId);

        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));

        StreetAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));

        // Check if contact already has this address type
        contactAddressRepository.findByContactIdAndAddressType(contactId, addressType)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Contact already has an address of type " + addressType + ". Use update instead.");
                });

        // First address for this contact becomes preferred
        boolean isFirstAddress = !contactAddressRepository.existsByContactId(contactId);

        ContactAddress contactAddress = ContactAddress.builder()
                .contact(contact)
                .address(address)
                .addressType(addressType)
                .preferred(isFirstAddress)
                .build();

        ContactAddress saved = contactAddressRepository.save(contactAddress);
        log.info("Added address {} to contact {} with type {}", addressId, contactId, addressType);

        return saved;
    }

    @Transactional
    public ContactAddress updateContactAddress(Long contactId, Long addressId, AddressKind addressType) {
        log.info("Updating address type {} for contact {} to address {}", addressType, contactId, addressId);

        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));

        StreetAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));

        ContactAddress contactAddress = contactAddressRepository.findByContactIdAndAddressType(contactId, addressType)
                .orElseGet(() -> ContactAddress.builder()
                        .contact(contact)
                        .addressType(addressType)
                        .build());

        contactAddress.setAddress(address);
        ContactAddress saved = contactAddressRepository.save(contactAddress);

        log.info("Updated contact {} address type {} to address {}", contactId, addressType, addressId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ContactAddress> getContactAddresses(Long contactId) {
        log.debug("Getting addresses for contact {}", contactId);
        return contactAddressRepository.findByContactIdWithAddress(contactId);
    }

    @Transactional
    public void removeContactAddress(Long contactId, AddressKind addressType) {
        log.info("Removing address type {} from contact {}", addressType, contactId);

        contactAddressRepository.findByContactIdAndAddressType(contactId, addressType)
                .ifPresentOrElse(
                        contactAddressRepository::delete,
                        () -> {
                            throw new IllegalArgumentException(
                                    "Contact " + contactId + " does not have an address of type " + addressType);
                        }
                );
    }
}
