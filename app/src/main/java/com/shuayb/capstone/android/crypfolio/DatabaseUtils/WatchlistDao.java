package com.shuayb.capstone.android.crypfolio.DatabaseUtils;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WatchlistDao {

    @Query("SELECT * FROM watchlist")
    LiveData<List<Crypto>> loadAllWatchListItems();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWatchlistItem(Crypto crypto);

    @Delete
    void deleteWatchlistItem(Crypto crypto);

    @Query("SELECT * FROM watchlist WHERE id = :id")
    Crypto getWatchlistItemById(String id);

    @Query("DELETE FROM watchlist")
    void deleteAllWatchlistItems();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWatchlistItemsAsList(List<Crypto> cryptos);

}
