package com.example.abac_spike;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class Broker {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

//    private String vat;
//
    private String name;

    @OneToMany(mappedBy = "broker", cascade = CascadeType.ALL)
    private Set<AccountState> accountStates = new HashSet<>();
}