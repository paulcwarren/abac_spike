package com.example.abac_spike;

import org.springframework.content.commons.repository.ContentStore;
import org.springframework.stereotype.Component;

@Component
public interface DocumentStore extends ContentStore<Document, String> {
}
