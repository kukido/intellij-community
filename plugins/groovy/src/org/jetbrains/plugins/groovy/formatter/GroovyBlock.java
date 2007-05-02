/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.formatter.processors.GroovySpacingProcessor;

import java.util.List;

/**
 * Black implementation for Groovy formatter
 *
 * @author Ilya.Sergey
 */
public class GroovyBlock implements Block, GroovyElementTypes {

  final protected ASTNode myNode;
  final protected Alignment myAlignment;
  final protected Indent myIndent;
  final protected Wrap myWrap;
  final protected CodeStyleSettings mySettings;

  protected List<Block> mySubBlocks = null;

  public GroovyBlock(@NotNull final ASTNode node, @Nullable final Alignment alignment, @NotNull final Indent indent, @Nullable final Wrap wrap, final CodeStyleSettings settings) {
    myNode = node;
    myAlignment = alignment;
    myIndent = indent;
    myWrap = wrap;
    mySettings = settings;
  }

  @NotNull
  public ASTNode getNode() {
    return myNode;
  }

  @NotNull
  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  @NotNull
  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  @NotNull
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = GroovyBlockGenerator.generateSubBlocks(myNode, myAlignment, myWrap, mySettings, this);
    }
    return mySubBlocks;
  }

  @Nullable
  public Wrap getWrap() {
    return myWrap;
  }

  @Nullable
  public Indent getIndent() {
    return myIndent;
  }

  @Nullable
  public Alignment getAlignment() {
    return myAlignment;
  }

  /**
   * Returns spacing between neighrbour elements
   *
   * @param child1 left element
   * @param child2 right element
   * @return
   */
  @Nullable
  public Spacing getSpacing(Block child1, Block child2) {
    if ((child1 instanceof GroovyBlock) && (child2 instanceof GroovyBlock)) {
      return GroovySpacingProcessor.getSpacing(((GroovyBlock) child1), ((GroovyBlock) child2));
    }
    return null;
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    ASTNode astNode = getNode();
    final PsiElement psiParent = astNode.getPsi();
    if (psiParent instanceof GroovyFile) {
      return new ChildAttributes(Indent.getNoneIndent(), null);
    }
    if (BLOCK_SET.contains(astNode.getElementType())) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    if (psiParent instanceof GrBinaryExpression) {
      return new ChildAttributes(Indent.getContinuationWithoutFirstIndent(), null);
    }
    if (this instanceof LargeGroovyBlock) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    if (psiParent instanceof GrParameterList) {
      return new ChildAttributes(this.getIndent(), this.getAlignment());
    }
    return new ChildAttributes(Indent.getNoneIndent(), null);
  }


  public boolean isIncomplete() {
    return isIncomplete(myNode);
  }

  /**
   * @param node Tree node
   * @return true if node is incomplete
   */
  public boolean isIncomplete(@NotNull final ASTNode node) {
    ASTNode lastChild = node.getLastChildNode();
    while (lastChild != null &&
            (lastChild.getPsi() instanceof PsiWhiteSpace || lastChild.getPsi() instanceof PsiComment)) {
      lastChild = lastChild.getTreePrev();
    }
    if (lastChild == null) {
      return false;
    }
    if (lastChild.getPsi() instanceof PsiErrorElement) {
      return true;
    }
    return isIncomplete(lastChild);
  }

  public boolean isLeaf() {
    return myNode.getFirstChildNode() == null;
  }


}
