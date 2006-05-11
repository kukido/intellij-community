/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.uiDesigner.binding;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/**
 * @author max
 */
public class FormReferencesSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(final ReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    final PsiElement refElement = p.getElementToSearch();
    final PsiFile psiFile = refElement.getContainingFile();
    if (psiFile == null) return true;
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return true;
    Module module = ProjectRootManager.getInstance(refElement.getProject()).getFileIndex().getModuleForFile(virtualFile);
    if (module == null) return true;
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesScope(module);
    if (refElement instanceof PsiPackage) {
      //no need to do anything
      //if (!UIFormUtil.processReferencesInUIForms(consumer, (PsiPackage)refElement, scope)) return false;
    }
    else if (refElement instanceof PsiClass) {
      if (!UIFormUtil.processReferencesInUIForms(consumer, (PsiClass)refElement, scope)) return false;
    }
    else if (refElement instanceof PsiField) {
      if (!UIFormUtil.processReferencesInUIForms(consumer, (PsiField)refElement, scope)) return false;
    }
    else if (refElement instanceof Property) {
      if (!UIFormUtil.processReferencesInUIForms(consumer, (Property)refElement, scope)) return false;
    }
    else if (refElement instanceof PropertiesFile) {
      if (!UIFormUtil.processReferencesInUIForms(consumer, (PropertiesFile)refElement, scope)) return false;
    }

    return true;
  }
}
