package com.splitwise.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "expenses")
@Getter
@Setter
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = true)
    private String description;
    @Column(nullable = false)
    private BigDecimal amount;
    @ManyToOne
    @JoinColumn(name = "paid_by",nullable = false)
    private User paidBy;
    @ManyToOne
    @JoinColumn(name="group_id")
    private Group group;
    @Enumerated(EnumType.STRING)
    private SplitType splitType;
}
