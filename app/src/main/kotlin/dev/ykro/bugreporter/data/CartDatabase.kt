package dev.ykro.bugreporter.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cart_items")
data class CartItemEntity(@PrimaryKey val productId: Int, val quantity: Int)

@Dao
interface CartDao {
  @Query("SELECT * FROM cart_items ORDER BY productId") fun observeAll(): Flow<List<CartItemEntity>>

  @Query("SELECT * FROM cart_items ORDER BY productId") suspend fun all(): List<CartItemEntity>

  @Query("SELECT * FROM cart_items WHERE productId = :productId")
  suspend fun byProduct(productId: Int): CartItemEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: CartItemEntity)

  @Query("DELETE FROM cart_items WHERE productId = :productId") suspend fun delete(productId: Int)

  @Query("DELETE FROM cart_items") suspend fun clear()
}

@Database(entities = [CartItemEntity::class], version = 1, exportSchema = true)
abstract class CartDatabase : RoomDatabase() {
  abstract fun cartDao(): CartDao

  companion object {
    fun create(context: Context): CartDatabase =
      Room.databaseBuilder(context, CartDatabase::class.java, "cart_shop.db").build()
  }
}
