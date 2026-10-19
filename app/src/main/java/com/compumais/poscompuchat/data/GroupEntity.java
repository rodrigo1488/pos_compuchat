package com.compumais.poscompuchat.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pos_groups")
public class GroupEntity {
    @PrimaryKey
    public int id;
    public String payloadJson;
}
