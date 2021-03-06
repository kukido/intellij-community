package com.jetbrains.python.edu;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.edu.course.Course;
import com.jetbrains.python.edu.course.Task;
import com.jetbrains.python.sdk.PythonSdkType;

import java.io.*;
import java.util.Map;

public class StudyTestRunner {
  public static final String STUDY_PREFIX="#educational_plugin";
  public static final String TEST_OK = "test OK";
  private static final String TEST_FAILED = "FAILED + ";
  private static final String PYTHONPATH = "PYTHONPATH";
  private static final Logger LOG = Logger.getInstance(StudyTestRunner.class);
  private final Task myTask;
  private final VirtualFile myTaskDir;

  public StudyTestRunner(Task task, VirtualFile taskDir) {
    myTask = task;
    myTaskDir = taskDir;
  }

  public Process launchTests(Project project, String executablePath) throws ExecutionException {
    Sdk sdk = PythonSdkType.findPythonSdk(ModuleManager.getInstance(project).getModules()[0]);
    File testRunner = new File(myTaskDir.getPath(), myTask.getTestFile());
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.withWorkDirectory(myTaskDir.getPath());
    final Map<String, String> env = commandLine.getEnvironment();
    final VirtualFile courseDir = project.getBaseDir();
    if (courseDir != null) {
      env.put(PYTHONPATH, courseDir.getPath());
    }
    if (sdk != null) {
      String pythonPath = sdk.getHomePath();
      if (pythonPath != null) {
        commandLine.setExePath(pythonPath);
        commandLine.addParameter(testRunner.getPath());
        final Course course = StudyTaskManager.getInstance(project).getCourse();
        assert course != null;
        File resourceFile = new File(course.getResourcePath());
        String resourceFolder = resourceFile.getParent();
        if (resourceFolder == null) {
          return null;
        }
        commandLine.addParameter(resourceFolder);
        commandLine.addParameter(FileUtil.toSystemDependentName(executablePath));
        return commandLine.createProcess();
      }
    }
    return null;
  }


  public String getPassedTests(Process p) {
    InputStream testOutput = p.getInputStream();
    BufferedReader testOutputReader = new BufferedReader(new InputStreamReader(testOutput));
    String line;
    try {
      while ((line = testOutputReader.readLine()) != null) {
        if (line.contains(STUDY_PREFIX)) {
          if (line.contains(TEST_OK)) {
            continue;
          }
          int messageStart = line.indexOf(TEST_FAILED);
          String res = line.substring(messageStart + TEST_FAILED.length());
          StudyUtils.closeSilently(testOutputReader);
          return res;
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      StudyUtils.closeSilently(testOutputReader);
    }
    return TEST_OK;
  }
}
