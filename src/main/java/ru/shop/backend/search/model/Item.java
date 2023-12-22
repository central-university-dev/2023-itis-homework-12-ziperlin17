package ru.shop.backend.search.model;

import lombok.*;

import javax.persistence.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Getter
@Table(name = "item")
public class Item {
    @Column
    private Integer price;
    @Column
    private String name;
    @Column
    private String url;
    @Column
    private String image;
    @Id
    @Column
    private Integer itemId;
    private String cat;
}
