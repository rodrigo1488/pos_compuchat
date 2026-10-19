package com.compumais.poscompuchat.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PosDao {
    @Query("DELETE FROM pos_users")
    void clearUsers();

    @Query("DELETE FROM pos_mesas")
    void clearMesas();

    @Query("DELETE FROM pos_products")
    void clearProducts();

    @Query("DELETE FROM pos_groups")
    void clearGroups();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUsers(List<UserEntity> users);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMesas(List<MesaEntity> mesas);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertProducts(List<ProductEntity> products);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertGroups(List<GroupEntity> groups);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertMeta(MetaEntity meta);

    @Query("SELECT * FROM pos_users ORDER BY name COLLATE NOCASE")
    List<UserEntity> users();

    @Query("SELECT * FROM pos_mesas ORDER BY displayOrder, number COLLATE NOCASE")
    List<MesaEntity> mesas();

    @Query("SELECT * FROM pos_mesas WHERE id = :id LIMIT 1")
    MesaEntity mesa(int id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertMesa(MesaEntity mesa);

    @Query("SELECT * FROM pos_products ORDER BY grupo, name COLLATE NOCASE")
    List<ProductEntity> products();

    @Query("SELECT * FROM pos_products WHERE id = :id LIMIT 1")
    ProductEntity product(int id);

    @Query("SELECT * FROM pos_groups")
    List<GroupEntity> groups();

    @Query("SELECT * FROM pos_meta WHERE id = 1 LIMIT 1")
    MetaEntity meta();
}
