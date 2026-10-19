package com.compumais.poscompuchat.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pos_mesas")
public class MesaEntity {
    @PrimaryKey
    public int id;
    public String number;
    public String name;
    public String type;
    public String status;
    public String contactName;
    public String contactPhone;
    public boolean askPhone;
    public boolean keywordValidation;
    public double totalValue;
    public int displayOrder;
    public String section;
}
