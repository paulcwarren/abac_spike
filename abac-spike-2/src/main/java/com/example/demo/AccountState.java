package com.example.demo;

import static javax.persistence.GenerationType.AUTO;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Version;

import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.annotations.ContentLength;
import org.springframework.content.commons.annotations.MimeType;
import org.springframework.versions.AncestorId;
import org.springframework.versions.AncestorRootId;
import org.springframework.versions.LockOwner;
import org.springframework.versions.SuccessorId;
import org.springframework.versions.VersionLabel;
import org.springframework.versions.VersionNumber;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of= {"id"})
public class AccountState {

    public AccountState(AccountState state) {
        this.contentId = state.getContentId();
        this.contentLength = state.getContentLength();
        this.mimeType = state.getMimeType();
        this.broker = state.getBroker();
    }

    @Id
    @GeneratedValue(strategy=AUTO)
    private Long id;

    @ContentId
    private String contentId;

    @ContentLength
    private Long contentLength;

    @MimeType
    private String mimeType;

    private String name;
    private String type;

    @Version
    private Long vstamp;

    @LockOwner
    private String lockOwner;

    @AncestorId
    private Long ancestorId;

    @AncestorRootId
    private Long ancestralRootId;

    @SuccessorId
    private Long successorId;

    @VersionNumber
    private String version;

    @VersionLabel
    private String label;

    @ManyToOne
    @JoinColumn
    private Broker broker;
}
