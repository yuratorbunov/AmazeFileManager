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

package com.amaze.filemanager.asynchronous.asynctasks.searchfilesystem;

import static com.amaze.filemanager.ui.fragments.preference_fragments.PreferencesConstants.PREFERENCE_SHOW_HIDDENFILES;

import java.util.regex.Pattern;

import com.amaze.filemanager.asynchronous.asynctasks.StatefulAsyncTask;
import com.amaze.filemanager.file_operations.filesystem.OpenMode;
import com.amaze.filemanager.filesystem.HybridFile;
import com.amaze.filemanager.filesystem.HybridFileParcelable;
import com.amaze.filemanager.ui.fragments.SearchWorkerFragment;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class SearchAsyncTask extends AsyncTask<Void, HybridFileParcelable, Void>
    implements StatefulAsyncTask<SearchWorkerFragment.HelperCallbacks> {

  private static final String TAG = "SearchAsyncTask";

  /** This necessarily leaks the context */
  private final Context applicationContext;

  private SearchWorkerFragment.HelperCallbacks callbacks;
  private final String input;
  private final boolean rootMode;
  private final boolean isRegexEnabled;
  private final boolean isMatchesEnabled;
  private final HybridFile file;

  public SearchAsyncTask(
      Context context,
      String input,
      OpenMode openMode,
      boolean root,
      boolean regex,
      boolean matches,
      String path) {
    this.applicationContext = context.getApplicationContext();
    this.input = input;
    rootMode = root;
    isRegexEnabled = regex;
    isMatchesEnabled = matches;

    this.file = new HybridFile(openMode, path);
  }

  @Override
  protected void onPreExecute() {
    /*
     * Note that we need to check if the callbacks are null in each
     * method in case they are invoked after the Activity's and
     * Fragment's onDestroy() method have been called.
     */
    if (callbacks != null) {
      callbacks.onPreExecute(input);
    }
  }

  // callbacks not checked for null because of possibility of
  // race conditions b/w worker thread main thread
  @Override
  protected Void doInBackground(Void... params) {
    if (file.isSmb()) return null;

    // level 1
    // if regex or not
    if (!isRegexEnabled) {
      search(file, input);
    } else {
      // compile the regular expression in the input
      Pattern pattern = Pattern.compile(bashRegexToJava(input));
      // level 2
      if (!isMatchesEnabled) searchRegExFind(file, pattern);
      else searchRegExMatch(file, pattern);
    }
    return null;
  }

  @Override
  public void onPostExecute(Void c) {
    if (callbacks != null) {
      callbacks.onPostExecute(input);
    }
  }

  @Override
  protected void onCancelled() {
    if (callbacks != null) callbacks.onCancelled();
  }

  @Override
  public void onProgressUpdate(HybridFileParcelable... val) {
    if (!isCancelled() && callbacks != null) {
      callbacks.onProgressUpdate(val[0], input);
    }
  }

  @Override
  public void setCallback(SearchWorkerFragment.HelperCallbacks helperCallbacks) {
    this.callbacks = helperCallbacks;
  }

  /**
   * Recursively search for occurrences of a given text in file names and publish the result
   *
   * @param directory the current path
   */
  private void search(HybridFile directory, final SearchFilter filter) {
    if (directory.isDirectory(
        applicationContext)) { // do you have permission to read this directory?
      directory.forEachChildrenFile(
          applicationContext,
          rootMode,
          file -> {
            boolean showHiddenFiles =
                PreferenceManager.getDefaultSharedPreferences(applicationContext)
                    .getBoolean(PREFERENCE_SHOW_HIDDENFILES, false);

            if ((!isCancelled() && (showHiddenFiles || !file.isHidden()))) {
              if (filter.searchFilter(file.getName(applicationContext))) {
                publishProgress(file);
              }
              if (file.isDirectory() && !isCancelled()) {
                search(file, filter);
              }
            }
          });
    } else {
      Log.w(TAG, "Cannot search " + directory.getPath() + ": Permission Denied");
    }
  }

  /**
   * Recursively search for occurrences of a given text in file names and publish the result
   *
   * @param file the current path
   * @param query the searched text
   */
  private void search(HybridFile file, final String query) {
    search(file, fileName -> fileName.toLowerCase().contains(query.toLowerCase()));
  }

  /**
   * Recursively find a java regex pattern {@link Pattern} in the file names and publish the result
   *
   * @param file the current file
   * @param pattern the compiled java regex
   */
  private void searchRegExFind(HybridFile file, final Pattern pattern) {
    search(file, fileName -> pattern.matcher(fileName).find());
  }

  /**
   * Recursively match a java regex pattern {@link Pattern} with the file names and publish the
   * result
   *
   * @param file the current file
   * @param pattern the compiled java regex
   */
  private void searchRegExMatch(HybridFile file, final Pattern pattern) {
    search(file, fileName -> pattern.matcher(fileName).matches());
  }

  /**
   * method converts bash style regular expression to java. See {@link Pattern}
   *
   * @return converted string
   */
  private String bashRegexToJava(String originalString) {
    StringBuilder stringBuilder = new StringBuilder();

    for (int i = 0; i < originalString.length(); i++) {
      switch (originalString.charAt(i) + "") {
        case "*":
          stringBuilder.append("\\w*");
          break;
        case "?":
          stringBuilder.append("\\w");
          break;
        default:
          stringBuilder.append(originalString.charAt(i));
          break;
      }
    }

    return stringBuilder.toString();
  }

  public interface SearchFilter {
    boolean searchFilter(String fileName);
  }
}
