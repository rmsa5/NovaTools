package de.langerhans.odintools.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenPresetDao {

    /** All presets in priority order, the default preset last. */
    @Query("SELECT * FROM screen_preset ORDER BY isDefault, position")
    fun observeAll(): Flow<List<ScreenPresetEntity>>

    @Query("SELECT * FROM screen_preset ORDER BY isDefault, position")
    suspend fun getAll(): List<ScreenPresetEntity>

    @Query("SELECT COUNT(*) FROM screen_preset WHERE isDefault = 1")
    suspend fun countDefault(): Int

    @Insert
    suspend fun insert(preset: ScreenPresetEntity): Long

    @Query("SELECT * FROM screen_preset WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): ScreenPresetEntity

    @Update
    suspend fun update(preset: ScreenPresetEntity)

    @Update
    suspend fun updateAll(presets: List<ScreenPresetEntity>)

    /** Deletes a preset. The default preset can't be deleted. */
    @Query("DELETE FROM screen_preset WHERE id = :id AND isDefault = 0")
    suspend fun deleteById(id: Long)
}
