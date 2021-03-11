package com.example.demo;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@EqualsAndHashCode(of= {"id"})
public class Broker {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String name;

//    These types of queries don't work with the querydsl approach
//
//    @OneToMany(mappedBy = "broker", fetch = FetchType.EAGER/*, cascade = CascadeType.ALL*/)
//    private Set<AccountState> accountStates = new HashSet<>();
}