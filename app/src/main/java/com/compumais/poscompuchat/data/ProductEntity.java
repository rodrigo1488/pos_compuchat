package com.compumais.poscompuchat.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pos_products")
public class ProductEntity {
    @PrimaryKey
    public int id;
    public String grupo;
    public String name;
    public String payloadJson;
}
