package com.guo.example.detectors;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.Sets;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static com.android.tools.lint.detector.api.LintUtils.skipParentheses;

/**
 * Created by guoxiaojiang on 2017/7/11.
 */

public class GuoJavaPerformanceDetector extends Detector implements Detector.JavaPsiScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            GuoJavaPerformanceDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /**
     * Allocating objects in loop body
     */
    public static final Issue LOOP_ALLOC = Issue.create(
            "LoopCarriedAllocation",
            "Memory allocations within loop code",
            "应避免在循环体中进行内存分配",
            Category.PERFORMANCE,
            9,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        List<Class<? extends PsiElement>> types = new ArrayList<>(3);
        types.add(PsiNewExpression.class);
        types.add(PsiForStatement.class);
        types.add(PsiForeachStatement.class);
        types.add(PsiWhileStatement.class);
        types.add(PsiDoWhileStatement.class);
        types.add(PsiMethod.class);
        types.add(PsiMethodCallExpression.class);
        return types;
    }

    @Override
    public JavaElementVisitor createPsiVisitor(@NonNull JavaContext context) {
        return new PerformanceVisitor(context);
    }


    private static class PerformanceVisitor extends JavaElementVisitor {
        private final JavaContext mContext;
        private final boolean mCheckAlloc;

        public PerformanceVisitor(JavaContext context) {
            mContext = context;
            mCheckAlloc = context.isEnabled(LOOP_ALLOC);
        }
        @Override
        public void visitWhileStatement(PsiWhileStatement statement) {
            statement.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitNewExpression(PsiNewExpression node) {
                    if (!(skipParentheses(node.getParent()) instanceof PsiThrowStatement)
                            && mCheckAlloc) {
                        PsiMethod method = PsiTreeUtil.getParentOfType(node, PsiMethod.class);
                        if (method != null && !isLazilyInitialized(node)) {
                            System.out.println("guo allocation in while:" + node.getNavigationElement());
                            reportAllocation(node);
                        }
                    }
                }
            });
        }

        @Override
        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            statement.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitNewExpression(PsiNewExpression node) {
                    if (!(skipParentheses(node.getParent()) instanceof PsiThrowStatement)
                            && mCheckAlloc) {
                        PsiMethod method = PsiTreeUtil.getParentOfType(node, PsiMethod.class);
                        if (method != null && !isLazilyInitialized(node)) {
                            System.out.println("guo allocation in do-while:" + node.getNavigationElement());
                            reportAllocation(node);
                        }
                    }
                }
            });
        }

        @Override
        public void visitForStatement(PsiForStatement statement) {
            statement.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitNewExpression(PsiNewExpression node) {
                    if (!(skipParentheses(node.getParent()) instanceof PsiThrowStatement)
                            && mCheckAlloc) {
                        PsiMethod method = PsiTreeUtil.getParentOfType(node, PsiMethod.class);
                        if (method != null && !isLazilyInitialized(node)) {
                            System.out.println("guo allocation in for:" + node.getNavigationElement());
                            reportAllocation(node);
                        }
                    }
                }
            });
        }

        @Override
        public void visitForeachStatement(PsiForeachStatement statement) {
            statement.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitNewExpression(PsiNewExpression node) {
                    if (!(skipParentheses(node.getParent()) instanceof PsiThrowStatement)
                            && mCheckAlloc) {
                        PsiMethod method = PsiTreeUtil.getParentOfType(node, PsiMethod.class);
                        if (method != null && !isLazilyInitialized(node)) {
                            System.out.println("guo allocation in foreach:" + node.getNavigationElement());
                            reportAllocation(node);
                        }
                    }
                }
            });
        }


        private void reportAllocation(PsiElement node) {
            mContext.report(LOOP_ALLOC, node, mContext.getLocation(node),
                    "Avoid object allocations when looping (preallocate and " +
                            "reuse instead)");
        }

        private static boolean isLazilyInitialized(PsiElement node) {
            PsiElement curr = node.getParent();
            while (curr != null) {
                if (curr instanceof PsiMethod) {
                    return false;
                } else if (curr instanceof PsiIfStatement) {
                    PsiIfStatement ifNode = (PsiIfStatement) curr;
                    // See if the if block represents a lazy initialization:
                    // compute all variable names seen in the condition
                    // (e.g. for "if (foo == null || bar != foo)" the result is "foo,bar"),
                    // and then compute all variables assigned to in the if body,
                    // and if there is an overlap, we'll consider the whole if block
                    // guarded (so lazily initialized and an allocation we won't complain
                    // about.)
                    List<String> assignments = new ArrayList<>();
                    AssignmentTracker visitor = new AssignmentTracker(assignments);
                    if (ifNode.getThenBranch() != null) {
                        ifNode.getThenBranch().accept(visitor);
                    }
                    if (!assignments.isEmpty()) {
                        List<String> references = new ArrayList<>();
                        addReferencedVariables(references, ifNode.getCondition());
                        if (!references.isEmpty()) {
                            Sets.SetView<String> intersection = Sets.intersection(
                                    new HashSet<>(assignments),
                                    new HashSet<>(references));
                            return !intersection.isEmpty();
                        }
                    }
                    return false;
                }
                curr = curr.getParent();
            }
            return false;
        }

        /**
         * Adds any variables referenced in the given expression into the given list
         */
        private static void addReferencedVariables(
                @NonNull Collection<String> variables,
                @Nullable PsiExpression expression) {
            if (expression instanceof PsiBinaryExpression) {
                PsiBinaryExpression binary = (PsiBinaryExpression) expression;
                addReferencedVariables(variables, binary.getLOperand());
                addReferencedVariables(variables, binary.getROperand());
            } else if (expression instanceof PsiPrefixExpression) {
                PsiPrefixExpression unary = (PsiPrefixExpression) expression;
                addReferencedVariables(variables, unary.getOperand());
            } else if (expression instanceof PsiParenthesizedExpression) {
                PsiParenthesizedExpression exp = (PsiParenthesizedExpression) expression;
                addReferencedVariables(variables, exp.getExpression());
            } else if (expression instanceof PsiIdentifier) {
                PsiIdentifier reference = (PsiIdentifier) expression;
                variables.add(reference.getText());
            } else if (expression instanceof PsiReferenceExpression) {
                PsiReferenceExpression ref = (PsiReferenceExpression) expression;
                PsiElement qualifier = ref.getQualifier();
                if (qualifier != null) {
                    if (qualifier instanceof PsiThisExpression ||
                            qualifier instanceof PsiSuperExpression) {
                        variables.add(ref.getReferenceName());
                    }
                } else {
                    variables.add(ref.getReferenceName());
                }
            }
        }

    }

    /**
     * Visitor which records variable names assigned into
     */
    private static class AssignmentTracker extends JavaRecursiveElementVisitor {
        private final Collection<String> mVariables;

        public AssignmentTracker(Collection<String> variables) {
            mVariables = variables;
        }

        @Override
        public void visitAssignmentExpression(PsiAssignmentExpression node) {
            super.visitAssignmentExpression(node);
            PsiExpression left = node.getLExpression();
            if (left instanceof PsiReferenceExpression) {
                PsiReferenceExpression ref = (PsiReferenceExpression) left;
                if (ref.getQualifier() instanceof PsiThisExpression ||
                        ref.getQualifier() instanceof PsiSuperExpression) {
                    mVariables.add(ref.getReferenceName());
                } else {
                    mVariables.add(ref.getText());
                }
            } else if (left instanceof PsiIdentifier) {
                mVariables.add(left.getText());
            }
        }
    }

}
