package com.bitespeed.Repository;

import com.bitespeed.Entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByEmailOrPhoneNumber(String email, String phoneNumber);
    List<Contact> findByLinkedId(Long linkedId);

    // Added methods as per the service implementation
    List<Contact> findByEmail(String email);
    List<Contact> findByPhoneNumber(String phoneNumber);
}