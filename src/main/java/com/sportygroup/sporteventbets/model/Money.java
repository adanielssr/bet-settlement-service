package com.sportygroup.sporteventbets.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class Money {
    private BigDecimal amount;
    private String currency;
}
