package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.*;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AntCallImpl extends AntTaskImpl implements AntCall {

  private AntTarget[] myDependsTargets = null;
  private AntProperty[] myParams = null;

  public AntCallImpl(final AntElement parent, final XmlElement sourceElement) {
    super(parent, sourceElement);
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntCall to ");
      builder.append(getTarget().toString());
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntTarget getTarget() {
    final String target = getSourceElement().getAttributeValue("target");
    final AntProject project = getAntProject();
    if (project != null) {
      AntTarget result = project.getTarget(target);
      if (result != null) {
        result.setDependsTargets(getDependsTargets());
      }
      return result;
    }
    return null;
  }

  public void setTarget(AntTarget target) throws IncorrectOperationException {
    getSourceElement().setAttribute("target", target.getName());
    subtreeChanged();
  }

  @NotNull
  public AntProperty[] getParams() {
    if (myParams == null) {
      List<AntProperty> properties = new ArrayList<AntProperty>();
      for (AntElement element : getChildren()) {
        if (element instanceof AntProperty) {
          properties.add((AntProperty)element);
        }
      }
      myParams = properties.toArray(new AntProperty[properties.size()]);
    }
    return myParams;
  }

  public void clearCaches() {
    myDependsTargets = null;
  }

  @NotNull
  private AntTarget[] getDependsTargets() {
    if (myDependsTargets == null) {
      List<AntTarget> targets = new ArrayList<AntTarget>();
      for (AntElement element : getChildren()) {
        if (element instanceof AntTarget) {
          targets.add((AntTarget)element);
        }
      }
      myDependsTargets = targets.toArray(new AntTarget[targets.size()]);
    }
    return myDependsTargets;
  }
}
