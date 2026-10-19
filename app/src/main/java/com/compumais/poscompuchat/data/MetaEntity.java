package com.compumais.poscompuchat.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pos_meta")
public class MetaEntity {
    @PrimaryKey
    public int id = 1;
    public long catalogVersion;
    public String updatedAt;
}
