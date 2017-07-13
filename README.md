# testlint
lint相关知识

主要参考了美团的[Android自定义Lint实践](http://tech.meituan.com/android_custom_lint.html)和[官方源码](https://android.googlesource.com/platform/tools/base/+/studio-master-dev/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks)

主要使用LinkedIn发方案：将jar放到一个aar中：[Writing Custom Lint Checks with Gradle](https://engineering.linkedin.com/android/writing-custom-lint-checks-gradle)。

这里具一个检查在循环体内（for, foreach, while, do-while）进行内存分配（Allocation）的例子。我们知道，在循环体内进行内存分配，如果这个循环次数很多的话，我们这么大频次的内存分配是有性能上的损耗的，甚至会引起内存抖动或者内存泄漏。一个比较合理的方案是，使用**对象池**技术来替代频繁的内存分配（可以了解Message.obtion()就是一种对象池技术）

大体步骤如下：

#### 创建Java工程，配置Gradle

```java
apply plugin: 'java'

dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    //当前最新版本25.3.0，推荐使用最新版，因为参考lint-checks的源码时用到了很多新的api
    compile 'com.android.tools.lint:lint-api:25.3.0'
    compile 'com.android.tools.lint:lint-checks:25.3.0'
}
```

#### 创建Detector
Detector负责扫描代码，发现问题并报告。这里主要参考了studio自带Lint检查的官方源码[JavaPerformanceDetector](https://android.googlesource.com/platform/tools/base/+/studio-master-dev/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/JavaPerformanceDetector.java)


```java
package com.guo.example.detectors;

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
        
        //之后的省略

}

```

核心在于：监听for、foreach、while、do-while的访问节点，在节点中判断是否有new对象的行为(visitNewExpression)，然后再判断是不是“LazilyInitialized”，去做相关告警提示。

#### Issue
可以看到，在GuoJavaPerformanceDetector.java中有一个Issue对象LOOP_ALLOC，其作用是提供给registry，最终体现在Lint report中。

> Issue.create的各参数对应如下：
> 
> id : 唯一值，应该能简短描述当前问题。利用Java注解或者XML属性进行屏蔽时，使用的就是这个id。
>  
> summary : 简短的总结，通常5-6个字符，描述问题而不是修复措施。
> 
> explanation : 完整的问题解释和修复建议。
> 
> category : 问题类别。详见下文详述部分。
> 
> priority : 优先级。1-10的数字，10为最重要/最严重。
> 
> severity : 严重级别：Fatal, Error, Warning, Informational, Ignore。
> 
> Implementation : 为Issue和Detector提供映射关系，Detector就是当前Detector。声明扫描检测的范围Scope，Scope用来描述Detector需要分析时需要考虑的文件集，包括：Resource文件或目录、Java文件、Class文件。

#### IssueRegistry

```java
package com.guo.example;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;
import com.guo.example.detectors.GuoJavaPerformanceDetector;
import com.guo.example.detectors.GuoLogDetector;

import java.util.Arrays;
import java.util.List;

/**
 * Created by guoxiaojiang on 2017/7/11.
 */
public class IssueRegister extends IssueRegistry {

    @Override
    public List<Issue> getIssues() {
        System.out.println("******* start guo issueRegister *******");
        return Arrays.asList(
                GuoLogDetector.ISSUE,
                GuoJavaPerformanceDetector.LOOP_ALLOC
        );
    }
}

```

#### 在build.grade中声明Lint-Registry属性

```java
jar {
    manifest {
        attributes("Lint-Registry": "com.guo.example.IssueRegister")
    }
}

configurations {
    lintJarOutput
}

dependencies {
    lintJarOutput files(jar)
}

```

#### 新建一个android lib moudle lint_aar
lint_aar的build.gradle需要配置如下：

```java
configurations {
    lintJarImport
}
dependencies {
    lintJarImport project(path: ":lint", configuration: "lintJarOutput")
}
task copyLintJar(type: Copy) {
    from(configurations.lintJarImport) {
        rename {
            String fileName ->
                'lint.jar'
        }
    }
    into 'build/intermediates/lint/'
}

project.afterEvaluate {
    def compileLintTask = project.tasks.find { it.name == 'compileLint' }
    compileLintTask.dependsOn(copyLintJar)
}
```

之后我们的待检测moudle依赖这个aar moudle，或者直接使用aar方式依赖即可
