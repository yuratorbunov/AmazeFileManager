/*
 * Copyright (C) 2014-2020 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.database.daos;

import static com.amaze.filemanager.database.ExplorerDatabase.COLUMN_TAB_NO;
import static com.amaze.filemanager.database.ExplorerDatabase.TABLE_TAB;

import java.util.List;

import com.amaze.filemanager.database.models.explorer.Tab;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import io.reactivex.Completable;
import io.reactivex.Single;

/**
 * {@link Dao} interface definition for {@link Tab}. Concrete class is generated by Room during
 * build.
 *
 * @see Dao
 * @see Tab
 * @see com.amaze.filemanager.database.ExplorerDatabase
 */
@Dao
public interface TabDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  Completable insertTab(Tab tab);

  @Transaction
  @Query("DELETE FROM " + TABLE_TAB)
  Completable clear();

  @Query("SELECT * FROM " + TABLE_TAB + " WHERE " + COLUMN_TAB_NO + " = :tabNo")
  Single<Tab> find(int tabNo);

  @Update(onConflict = OnConflictStrategy.REPLACE)
  void update(Tab tab);

  @Query("SELECT * FROM " + TABLE_TAB)
  Single<List<Tab>> list();
}
