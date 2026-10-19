package com.compumais.poscompuchat.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pos_users")
public class UserEntity {
    @PrimaryKey
    public int id;
    public String name;
    public boolean hasPin;
    public String profile;
}
