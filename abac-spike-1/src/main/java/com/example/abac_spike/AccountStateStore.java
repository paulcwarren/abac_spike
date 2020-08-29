package com.example.abac_spike;

import org.springframework.content.commons.renditions.Renderable;
import org.springframework.content.commons.repository.ContentStore;
import org.springframework.content.commons.search.Searchable;
import org.springframework.stereotype.Component;

@Component
public interface AccountStateStore extends ContentStore<AccountState, String>, Renderable<AccountState>, Searchable<String> {
}
