/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.actions.cvsContext;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * author: lesya
 */
public class CvsContextWrapper implements CvsContext {

  private final VcsContext myVcsContext;
  private final DataContext myContext;

  private CvsContextWrapper(AnActionEvent actionEvent, final VcsContext vcsContext) {
    myContext = actionEvent.getDataContext();
    myVcsContext = vcsContext;
  }

  public static CvsContext createCachedInstance(AnActionEvent event) {
    return new CachedCvsContext(new CvsContextWrapper(event, VcsContextFactory.SERVICE.getInstance().createCachedContextOn(event)));
  }

  public static CvsContext createInstance(AnActionEvent event) {
    return new CvsContextWrapper(event, VcsContextFactory.SERVICE.getInstance().createContextOn(event));
  }

  public boolean cvsIsActive() {
    Project project = getProject();
    if (project == null) return false;
    return ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(CvsVcs2.getInstance(project), getSelectedFiles());
  }

  @Override
  public String getActionName() {
    return myVcsContext.getActionName();
  }

  public Collection<String> getDeletedFileNames() {
    return CvsDataKeys.DELETED_FILE_NAMES.getData(myContext);
  }

  public CvsLightweightFile[] getSelectedLightweightFiles() {
    CvsLightweightFile[] files = CvsDataKeys.CVS_LIGHT_FILES.getData(myContext);
    if (files == null || files.length <= 0) {
      return null;
    }
    return files;
  }

  public Project getProject() {
    return myVcsContext.getProject();
  }

  public VirtualFile getSelectedFile() {
    return myVcsContext.getSelectedFile();
  }

  @NotNull
  public VirtualFile[] getSelectedFiles() {
    return myVcsContext.getSelectedFiles();
  }

  public Editor getEditor() {
    return myVcsContext.getEditor();
  }

  public Collection<VirtualFile> getSelectedFilesCollection() {
    return myVcsContext.getSelectedFilesCollection();
  }

  public File[] getSelectedIOFiles() {
    return myVcsContext.getSelectedIOFiles();
  }

  public int getModifiers() {
    return myVcsContext.getModifiers();
  }

  public Refreshable getRefreshableDialog() {
    return myVcsContext.getRefreshableDialog();
  }

  public String getPlace() {
    return myVcsContext.getPlace();
  }

  public File getSelectedIOFile() {
    return myVcsContext.getSelectedIOFile();
  }

  public FilePath[] getSelectedFilePaths() {
    return myVcsContext.getSelectedFilePaths();
  }

  public FilePath getSelectedFilePath() {
    return myVcsContext.getSelectedFilePath();
  }

  @Nullable
  public ChangeList[] getSelectedChangeLists() {
    return myVcsContext.getSelectedChangeLists();
  }

  @Nullable
  public Change[] getSelectedChanges() {
    return myVcsContext.getSelectedChanges();
  }
}
