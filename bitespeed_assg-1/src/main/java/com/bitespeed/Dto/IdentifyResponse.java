package com.bitespeed.Dto;

import java.util.List;

public class IdentifyResponse {
    private ContactResponse contact;

    public IdentifyResponse(ContactResponse contact) {
        this.contact = contact;
    }

    public ContactResponse getContact() {
        return contact;
    }

    public void setContact(ContactResponse contact) {
        this.contact = contact;
    }

    public static class ContactResponse {
        private Long primaryContatctId;
        private List<String> emails;
        private List<String> phoneNumbers;
        private List<Long> secondaryContactIds;

        public ContactResponse(Long primaryContatctId, List<String> emails, List<String> phoneNumbers, List<Long> secondaryContactIds) {
            this.primaryContatctId = primaryContatctId;
            this.emails = emails;
            this.phoneNumbers = phoneNumbers;
            this.secondaryContactIds = secondaryContactIds;
        }

        public Long getPrimaryContatctId() {
            return primaryContatctId;
        }

        public void setPrimaryContatctId(Long primaryContatctId) {
            this.primaryContatctId = primaryContatctId;
        }

        public List<String> getEmails() {
            return emails;
        }

        public void setEmails(List<String> emails) {
            this.emails = emails;
        }

        public List<String> getPhoneNumbers() {
            return phoneNumbers;
        }

        public void setPhoneNumbers(List<String> phoneNumbers) {
            this.phoneNumbers = phoneNumbers;
        }

        public List<Long> getSecondaryContactIds() {
            return secondaryContactIds;
        }

        public void setSecondaryContactIds(List<Long> secondaryContactIds) {
            this.secondaryContactIds = secondaryContactIds;
        }
    }
}
