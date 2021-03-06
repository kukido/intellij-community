/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.Consumer;
import com.intellij.util.HttpRequests;
import com.intellij.util.PathUtil;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URLConnection;
import java.util.List;

/**
 * @author anna
 * @since 10-Aug-2007
 */
public class PluginDownloader {
  private static final Logger LOG = Logger.getInstance("#" + PluginDownloader.class.getName());

  @NonNls private static final String FILENAME = "filename=";

  private final String myPluginId;
  private String myPluginUrl;
  private String myPluginVersion;

  private String myFileName;
  private String myPluginName;
  private BuildNumber myBuildNumber;

  private File myFile;
  private File myOldFile;
  private String myDescription;
  private List<PluginId> myDepends;
  private IdeaPluginDescriptor myDescriptor;

  public PluginDownloader(String pluginId, String pluginUrl, String pluginVersion) {
    myPluginId = pluginId;
    myPluginUrl = pluginUrl;
    myPluginVersion = pluginVersion;
  }

  public PluginDownloader(String pluginId,
                          String pluginUrl,
                          String pluginVersion,
                          String fileName,
                          String pluginName,
                          BuildNumber buildNumber) {
    myPluginId = pluginId;
    myPluginUrl = pluginUrl;
    myPluginVersion = pluginVersion;
    myFileName = fileName;
    myPluginName = pluginName;
    myBuildNumber = buildNumber;
  }

  public boolean prepareToInstall(@NotNull ProgressIndicator progressIndicator) throws IOException {
    return prepareToInstall(progressIndicator, myBuildNumber);
  }

  public boolean prepareToInstall(@NotNull ProgressIndicator progressIndicator, @Nullable BuildNumber forBuildNumber) throws IOException {
    if (myFile != null) {
      return true;
    }

    IdeaPluginDescriptor descriptor = null;
    if (!Boolean.getBoolean(StartupActionScriptManager.STARTUP_WIZARD_MODE) && PluginManager.isPluginInstalled(PluginId.getId(myPluginId))) {
      //store old plugins file
      descriptor = PluginManager.getPlugin(PluginId.getId(myPluginId));
      LOG.assertTrue(descriptor != null);
      if (myPluginVersion != null && compareVersionsSkipBroken(descriptor, myPluginVersion) <= 0) {
        LOG.info("Plugin " + myPluginId + ": current version (max) " + myPluginVersion);
        return false;
      }
      myOldFile = descriptor.getPath();
    }

    // download plugin
    String errorMessage = IdeBundle.message("unknown.error");
    try {
      myFile = downloadPlugin(progressIndicator);
    }
    catch (IOException ex) {
      myFile = null;
      errorMessage = ex.getMessage();
    }
    if (myFile == null) {
      if (ApplicationManager.getApplication() != null) {
        final String text = IdeBundle.message("error.plugin.was.not.installed", getPluginName(), errorMessage);
        final String title = IdeBundle.message("title.failed.to.download");
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            Messages.showErrorDialog(text, title);
          }
        });
      }
      return false;
    }

    IdeaPluginDescriptorImpl actualDescriptor = loadDescriptionFromJar(myFile);
    if (actualDescriptor != null) {
      if (InstalledPluginsTableModel.wasUpdated(actualDescriptor.getPluginId())) {
        return false; //already updated
      }

      myPluginVersion = actualDescriptor.getVersion();
      if (descriptor != null && compareVersionsSkipBroken(descriptor, myPluginVersion) <= 0) {
        LOG.info("Plugin " + myPluginId + ": current version (max) " + myPluginVersion);
        return false; //was not updated
      }

      setDescriptor(actualDescriptor);

      if (PluginManagerCore.isIncompatible(actualDescriptor, forBuildNumber)) {
        LOG.info("Plugin " + myPluginId + " is incompatible with current installation (since: " + actualDescriptor.getSinceBuild() + ", until: " + actualDescriptor.getUntilBuild()+ ")");
        return false; //host outdated plugins, no compatible plugin for new version
      }
    }
    return true;
  }

  public static int compareVersionsSkipBroken(IdeaPluginDescriptor descriptor, String newPluginVersion) {
    int state = StringUtil.compareVersionNumbers(newPluginVersion, descriptor.getVersion());
    if (PluginManagerCore.isBrokenPlugin(descriptor) && state < 0) {
      state = 1;
    }
    return state;
  }

  @Nullable
  public static IdeaPluginDescriptorImpl loadDescriptionFromJar(final File file) throws IOException {
    IdeaPluginDescriptorImpl descriptor = PluginManagerCore.loadDescriptorFromJar(file);
    if (descriptor == null) {
      if (file.getName().endsWith(".zip")) {
        final File outputDir = FileUtil.createTempDirectory("plugin", "");
        try {
          ZipUtil.extract(file, outputDir, null);
          final File[] files = outputDir.listFiles();
          if (files != null && files.length == 1) {
            descriptor = PluginManagerCore.loadDescriptor(files[0], PluginManagerCore.PLUGIN_XML);
          }
        }
        finally {
          FileUtil.delete(outputDir);
        }
      }
    }
    return descriptor;
  }

  public void install() throws IOException {
    LOG.assertTrue(myFile != null);
    if (myOldFile != null) {
      // add command to delete the 'action script' file
      StartupActionScriptManager.ActionCommand deleteOld = new StartupActionScriptManager.DeleteCommand(myOldFile);
      StartupActionScriptManager.addActionCommand(deleteOld);
    }
    install(myFile, getPluginName());
  }

  public static void install(final File fromFile, final String pluginName) throws IOException {
    install(fromFile, pluginName, true);
  }

  public static void install(final File fromFile, final String pluginName, boolean deleteFromFile) throws IOException {
    //noinspection HardCodedStringLiteral
    if (fromFile.getName().endsWith(".jar")) {
      // add command to copy file to the IDEA/plugins path
      StartupActionScriptManager.ActionCommand copyPlugin =
        new StartupActionScriptManager.CopyCommand(fromFile, new File(PathManager.getPluginsPath() + File.separator + fromFile.getName()));
      StartupActionScriptManager.addActionCommand(copyPlugin);
    }
    else {
      // add command to unzip file to the IDEA/plugins path
      String unzipPath;
      if (ZipUtil.isZipContainsFolder(fromFile)) {
        unzipPath = PathManager.getPluginsPath();
      }
      else {
        unzipPath = PathManager.getPluginsPath() + File.separator + pluginName;
      }

      StartupActionScriptManager.ActionCommand unzip = new StartupActionScriptManager.UnzipCommand(fromFile, new File(unzipPath));
      StartupActionScriptManager.addActionCommand(unzip);
    }

    // add command to remove temp plugin file
    if (deleteFromFile) {
      StartupActionScriptManager.ActionCommand deleteTemp = new StartupActionScriptManager.DeleteCommand(fromFile);
      StartupActionScriptManager.addActionCommand(deleteTemp);
    }
  }

  @NotNull
  private File downloadPlugin(@NotNull final ProgressIndicator progressIndicator) throws IOException {
    File pluginsTemp = new File(PathManager.getPluginTempPath());
    if (!pluginsTemp.exists() && !pluginsTemp.mkdirs()) {
      throw new IOException(IdeBundle.message("error.cannot.create.temp.dir", pluginsTemp));
    }
    final File file = FileUtil.createTempFile(pluginsTemp, "plugin_", "_download", true, false);

    progressIndicator.checkCanceled();
    progressIndicator.setText(IdeBundle.message("progress.downloading.plugin", getPluginName()));

    return HttpRequests.request(myPluginUrl).supportGzip(false).effectiveUrlConsumer(new Consumer<String>() {
      @Override
      public void consume(String url) {
        myPluginUrl = url;
      }
    }).get(new ThrowableConvertor<URLConnection, File, IOException>() {
      @Override
      public File convert(URLConnection connection) throws IOException {
        progressIndicator.checkCanceled();

        InputStream input = HttpRequests.getInputStream(connection);
        try {
          OutputStream fileOut = new FileOutputStream(file);
          try {
            NetUtils.copyStreamContent(progressIndicator, input, fileOut, connection.getContentLength());
          }
          finally {
            fileOut.close();
          }
        }
        finally {
          input.close();
        }

        if (myFileName == null) {
          myFileName = guessFileName(connection, file);
        }

        File newFile = new File(file.getParentFile(), myFileName);
        FileUtil.rename(file, newFile);
        return newFile;
      }
    });
  }

  @NotNull
  private String guessFileName(@NotNull URLConnection connection, @NotNull File file) throws IOException {
    String fileName = null;

    final String contentDisposition = connection.getHeaderField("Content-Disposition");
    LOG.debug("header: " + contentDisposition);

    if (contentDisposition != null && contentDisposition.contains(FILENAME)) {
      final int startIdx = contentDisposition.indexOf(FILENAME);
      final int endIdx = contentDisposition.indexOf(';', startIdx);
      fileName = contentDisposition.substring(startIdx + FILENAME.length(), endIdx > 0 ? endIdx : contentDisposition.length());

      if (StringUtil.startsWithChar(fileName, '\"') && StringUtil.endsWithChar(fileName, '\"')) {
        fileName = fileName.substring(1, fileName.length() - 1);
      }
    }

    if (fileName == null) {
      // try to find a filename in an URL
      final String usedURL = connection.getURL().toString();
      fileName = usedURL.substring(usedURL.lastIndexOf("/") + 1);
      if (fileName.length() == 0 || fileName.contains("?")) {
        fileName = myPluginUrl.substring(myPluginUrl.lastIndexOf("/") + 1);
      }
    }

    if (!PathUtil.isValidFileName(fileName)) {
      FileUtil.delete(file);
      throw new IOException("Invalid filename returned by a server");
    }

    return fileName;
  }

  public String getPluginId() {
    return myPluginId;
  }

  public String getFileName() {
    if (myFileName == null) {
      myFileName = myPluginUrl.substring(myPluginUrl.lastIndexOf("/") + 1);
    }
    return myFileName;
  }

  public String getPluginName() {
    if (myPluginName == null) {
      myPluginName = FileUtil.getNameWithoutExtension(getFileName());
    }
    return myPluginName;
  }

  public String getPluginVersion() {
    return myPluginVersion;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDepends(List<PluginId> depends) {
    myDepends = depends;
  }

  public List<PluginId> getDepends() {
    return myDepends;
  }

  public static PluginDownloader createDownloader(IdeaPluginDescriptor descriptor) throws UnsupportedEncodingException {
    return createDownloader(descriptor, null);
  }

  public static PluginDownloader createDownloader(IdeaPluginDescriptor descriptor,
                                                  BuildNumber buildNumber) throws UnsupportedEncodingException {
    PluginDownloader downloader = new PluginDownloader(descriptor.getPluginId().getIdString(), 
                                                       UpdateChecker.getDownloadUrl(descriptor, buildNumber), 
                                                       descriptor.getVersion(), null, descriptor.getName(), buildNumber);
    downloader.setDescriptor(descriptor);
    return downloader;
  }

  @Nullable
  public static VirtualFile findPluginFile(String pluginUrl, String host) {
    final VirtualFileManager fileManager = VirtualFileManager.getInstance();
    VirtualFile pluginFile = fileManager.findFileByUrl(pluginUrl);
    if (pluginFile == null) {
      final VirtualFile hostFile = fileManager.findFileByUrl(host);
      if (hostFile == null) {
        LOG.error("can't find file by url '" + host + "'");
        return null;
      }
      pluginFile = findPluginByRelativePath(hostFile.getParent(), pluginUrl, hostFile.getFileSystem());
    }
    if (pluginFile == null) {
      LOG.error("can't find '" + pluginUrl + "' relative to '" + host + "'");
      return null;
    }
    return pluginFile;
  }

  @Nullable
  private static VirtualFile findPluginByRelativePath(@NotNull final VirtualFile hostFile,
                                                     @NotNull @NonNls final String relPath,
                                                     @NotNull final VirtualFileSystem fileSystem) {
    if (relPath.length() == 0) return hostFile;
    int index = relPath.indexOf('/');
    if (index < 0) index = relPath.length();
    String name = relPath.substring(0, index);

    VirtualFile child;
    if (name.equals(".")) {
      child = hostFile;
    }
    else if (name.equals("..")) {
      child = hostFile.getParent();
    }
    else {
      child = fileSystem.findFileByPath(hostFile.getPath() + "/" + name);
    }

    if (child == null) return null;

    if (index < relPath.length()) {
      return findPluginByRelativePath(child, relPath.substring(index + 1), fileSystem);
    }
    else {
      return child;
    }
  }

  @Nullable
  public static PluginNode createPluginNode(String host, PluginDownloader downloader) {
    if (downloader.getDescriptor() instanceof PluginNode) {
      return (PluginNode)downloader.getDescriptor();
    }

    final VirtualFile pluginFile = findPluginFile(downloader.myPluginUrl, host);
    if (pluginFile != null) {
      final PluginNode node = new PluginNode(PluginId.getId(downloader.getPluginId()));
      node.setName(downloader.getPluginName());
      node.setVersion(downloader.getPluginVersion());
      node.setRepositoryName(host);
      node.setDownloadUrl(pluginFile.getUrl());
      node.setDepends(downloader.getDepends(), null);
      node.setDescription(downloader.getDescription());
      return node;
    }
    return null;
  }

  public void setDescriptor(IdeaPluginDescriptor descriptor) {
    myDescriptor = descriptor;
  }

  public IdeaPluginDescriptor getDescriptor() {
    return myDescriptor;
  }
}
