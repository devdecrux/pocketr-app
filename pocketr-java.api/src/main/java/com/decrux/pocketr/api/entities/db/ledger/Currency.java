package com.decrux.pocketr.api.entities.db.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "currency")
public class Currency {

    @Id
    @Column(length = 3)
    private String code = "";

    @Column(name = "minor_unit", nullable = false)
    private short minorUnit;

    @Column(nullable = false)
    private String name = "";

    public Currency() {
    }

    public Currency(String code, short minorUnit, String name) {
        this.code = code;
        this.minorUnit = minorUnit;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public short getMinorUnit() {
        return minorUnit;
    }

    public void setMinorUnit(short minorUnit) {
        this.minorUnit = minorUnit;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
