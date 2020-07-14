package com.example.abac_spike;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import static javax.persistence.GenerationType.AUTO;

@Entity
@Data
public class OtherDocument {

    @Id
    @GeneratedValue(strategy=AUTO)
    private Long id;

    private String type;
}
