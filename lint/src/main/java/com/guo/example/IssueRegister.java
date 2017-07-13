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
