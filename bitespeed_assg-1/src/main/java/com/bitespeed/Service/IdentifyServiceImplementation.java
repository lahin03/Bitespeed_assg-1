package com.bitespeed.Service;

import com.bitespeed.Dto.IdentifyRequest;
import com.bitespeed.Dto.IdentifyResponse;
import com.bitespeed.Entity.Contact;
import com.bitespeed.Entity.LinkPrecedence;
import com.bitespeed.Repository.ContactRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IdentifyServiceImplementation implements IdentifyService {

    private final ContactRepository contactRepository;

    public IdentifyServiceImplementation(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @Override
    public IdentifyResponse identifyContact(IdentifyRequest request) {
        String email = request.getEmail();
        String phone = request.getPhoneNumber();

        // Ensure at least one contact detail is provided, as per assignment requirements. [cite: 11]
        if (email == null && phone == null) {
            throw new IllegalArgumentException("Either email or phoneNumber must be provided in the request.");
        }

        // 1. Find initial matches based on provided email or phone.
        List<Contact> initialMatchedContacts = new ArrayList<>();
        if (email != null) {
            initialMatchedContacts.addAll(contactRepository.findByEmail(email));
        }
        if (phone != null) {
            initialMatchedContacts.addAll(contactRepository.findByPhoneNumber(phone));
        }

        // Use a Set to track all unique contacts belonging to the identified cluster.
        Set<Contact> allLinkedContacts = new HashSet<>();
        // Use a Queue for breadth-first traversal to discover all connected contacts.
        Queue<Contact> traversalQueue = new LinkedList<>();

        // Seed the traversal with the initial matches.
        for (Contact contact : initialMatchedContacts) {
            if (allLinkedContacts.add(contact)) {
                traversalQueue.offer(contact);
            }
        }

        // 2. Expand the contact cluster by traversing linked IDs and common contact details.
        // This ensures all indirectly related contacts are found.
        while (!traversalQueue.isEmpty()) {
            Contact current = traversalQueue.poll();

            // Discover contacts linked via the `linkedId` mechanism.
            List<Contact> linkedById = new ArrayList<>();
            if (current.getLinkPrecedence() == LinkPrecedence.PRIMARY) {
                // If current is a primary, add all contacts explicitly linked to it.
                linkedById.addAll(contactRepository.findByLinkedId(current.getId()));
            } else if (current.getLinkedId() != null) {
                // If current is a secondary, find its primary and all contacts linked to that primary.
                Optional<Contact> primaryOfCurrentOpt = contactRepository.findById(current.getLinkedId());
                if (primaryOfCurrentOpt.isPresent()) {
                    Contact primaryOfCurrent = primaryOfCurrentOpt.get();
                    linkedById.add(primaryOfCurrent); // Add the primary itself.
                    linkedById.addAll(contactRepository.findByLinkedId(primaryOfCurrent.getId()));
                }
            }

            for (Contact found : linkedById) {
                if (allLinkedContacts.add(found)) {
                    traversalQueue.offer(found);
                }
            }

            // Discover contacts linked via shared email or phone numbers, bridging distinct primary chains.
            List<Contact> indirectlyLinkedByDetails = new ArrayList<>();
            // Crucially, only search for non-null contact details to avoid unintended global merges.
            if (current.getEmail() != null) {
                indirectlyLinkedByDetails.addAll(contactRepository.findByEmail(current.getEmail()));
            }
            if (current.getPhoneNumber() != null) {
                indirectlyLinkedByDetails.addAll(contactRepository.findByPhoneNumber(current.getPhoneNumber()));
            }

            for (Contact found : indirectlyLinkedByDetails) {
                if (allLinkedContacts.add(found)) {
                    traversalQueue.offer(found);
                }
            }
        }

        // 3. Handle cases where no existing contacts were found.
        // A new primary contact is created for this new customer.
        if (allLinkedContacts.isEmpty()) {
            Contact newContact = new Contact();
            newContact.setEmail(email);
            newContact.setPhoneNumber(phone);
            newContact.setLinkPrecedence(LinkPrecedence.PRIMARY);
            // createdAt and updatedAt are automatically managed by @PrePersist.
            contactRepository.save(newContact);

            // Prepare response with the newly created primary contact and empty secondaries. [cite: 21]
            List<String> responseEmails = new ArrayList<>();
            if (email != null) responseEmails.add(email);
            List<String> responsePhones = new ArrayList<>();
            if (phone != null) responsePhones.add(phone);

            return new IdentifyResponse(new IdentifyResponse.ContactResponse(
                    newContact.getId(),
                    responseEmails,
                    responsePhones,
                    Collections.emptyList()
            ));
        }

        // 4. Determine the true primary contact for the identified cluster.
        // The contact with the earliest `createdAt` timestamp in the cluster is designated as primary. [cite: 16]
        List<Contact> contactsInClusterSorted = new ArrayList<>(allLinkedContacts);
        contactsInClusterSorted.sort(Comparator.comparing(Contact::getCreatedAt));

        Contact truePrimary = contactsInClusterSorted.get(0);

        // Ensure the oldest contact is explicitly marked as PRIMARY with no `linkedId`.
        if (truePrimary.getLinkPrecedence() != LinkPrecedence.PRIMARY || truePrimary.getLinkedId() != null) {
            truePrimary.setLinkPrecedence(LinkPrecedence.PRIMARY);
            truePrimary.setLinkedId(null);
            contactRepository.save(truePrimary); // Persist this change.
        }

        // 5. Downgrade any other contacts previously marked as PRIMARY within the cluster to SECONDARY.
        // This handles merging previously distinct customer identities. [cite: 24]
        for (Contact c : contactsInClusterSorted) {
            if (!c.getId().equals(truePrimary.getId()) && c.getLinkPrecedence() == LinkPrecedence.PRIMARY) {
                c.setLinkPrecedence(LinkPrecedence.SECONDARY);
                c.setLinkedId(truePrimary.getId());
                c.setUpdatedAt(LocalDateTime.now()); // Update timestamp as row is modified. [cite: 15]
                contactRepository.save(c);
            }
        }

        // 6. Ensure all secondary contacts in the cluster correctly point to the `truePrimary`.
        for (Contact c : contactsInClusterSorted) {
            if (c.getLinkPrecedence() == LinkPrecedence.SECONDARY && !truePrimary.getId().equals(c.getLinkedId())) {
                c.setLinkedId(truePrimary.getId());
                c.setUpdatedAt(LocalDateTime.now()); // Update timestamp. [cite: 15]
                contactRepository.save(c);
            }
        }

        // 7. Determine if a new secondary contact needs to be created from the incoming request.
        // A new secondary is created if the request contains new information that connects to an existing cluster, [cite: 22]
        // but does not exactly duplicate an existing contact within that cluster.
        if (email != null && phone != null) {
            boolean exactContactExists = allLinkedContacts.stream()
                    .anyMatch(c -> email.equals(c.getEmail()) && phone.equals(c.getPhoneNumber()));

            if (!exactContactExists) {
                // If the exact email-phone combination doesn't exist, check if either part links to the cluster.
                boolean requestEmailExistsInCluster = allLinkedContacts.stream()
                        .anyMatch(c -> email.equals(c.getEmail()));
                boolean requestPhoneExistsInCluster = allLinkedContacts.stream()
                        .anyMatch(c -> phone.equals(c.getPhoneNumber()));

                if (requestEmailExistsInCluster || requestPhoneExistsInCluster) {
                    // This implies a new combination that bridges to the existing customer's identity.
                    Contact newSecondaryContact = new Contact();
                    newSecondaryContact.setEmail(email);
                    newSecondaryContact.setPhoneNumber(phone);
                    newSecondaryContact.setLinkPrecedence(LinkPrecedence.SECONDARY);
                    newSecondaryContact.setLinkedId(truePrimary.getId());
                    // createdAt and updatedAt are automatically managed by @PrePersist.
                    contactRepository.save(newSecondaryContact);

                    // Add the new contact to the set for aggregation in the response.
                    allLinkedContacts.add(newSecondaryContact);
                    // Re-sort the list to include the newly added contact for correct aggregation.
                    contactsInClusterSorted = new ArrayList<>(allLinkedContacts);
                    contactsInClusterSorted.sort(Comparator.comparing(Contact::getCreatedAt));
                }
            }
        }

        // 8. Aggregate unique emails, phone numbers, and secondary IDs for the response.
        Set<String> uniqueEmails = new LinkedHashSet<>();
        Set<String> uniquePhones = new LinkedHashSet<>();
        List<Long> secondaryContactIds = new ArrayList<>();

        // Ensure the primary contact's details are listed first, as per response format. [cite: 20]
        if (truePrimary.getEmail() != null) {
            uniqueEmails.add(truePrimary.getEmail());
        }
        if (truePrimary.getPhoneNumber() != null) {
            uniquePhones.add(truePrimary.getPhoneNumber());
        }

        // Iterate through all contacts in the cluster to gather unique details and secondary IDs.
        for (Contact c : contactsInClusterSorted) {
            if (!c.getId().equals(truePrimary.getId())) { // Exclude the primary itself.
                if (c.getEmail() != null) {
                    uniqueEmails.add(c.getEmail());
                }
                if (c.getPhoneNumber() != null) {
                    uniquePhones.add(c.getPhoneNumber());
                }
                secondaryContactIds.add(c.getId());
            }
        }

        // 9. Construct and return the consolidated contact response. [cite: 19]
        return new IdentifyResponse(new IdentifyResponse.ContactResponse(
                truePrimary.getId(),
                new ArrayList<>(uniqueEmails),
                new ArrayList<>(uniquePhones),
                secondaryContactIds
        ));
    }
}