package com.example.abac_spike;

import org.springframework.content.commons.repository.ContentStore;
import org.springframework.stereotype.Component;

@Component
public interface AccountStateStore extends ContentStore<AccountState, String> {
}
