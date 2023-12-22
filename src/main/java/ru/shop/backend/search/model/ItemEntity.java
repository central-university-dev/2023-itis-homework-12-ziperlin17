package ru.shop.backend.search.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ItemEntity {
    private String name;
    private String brand;
    private String catalogue;
    private String type;
    private String description;
    private long brandId;
    private long catalogueId;
    @Id
    private long itemId;
}
