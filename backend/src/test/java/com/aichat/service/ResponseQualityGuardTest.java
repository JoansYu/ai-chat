package com.aichat.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseQualityGuardTest {

    @Test
    void detectsRepeatedLines() {
        String text = "同一个技术说明段落需要被识别出来。\n"
                + "同一个技术说明段落需要被识别出来。\n"
                + "同一个技术说明段落需要被识别出来。";

        assertTrue(ResponseQualityGuard.isLikelyRepetitive(text));
    }

    @Test
    void acceptsNormalResponse() {
        assertFalse(ResponseQualityGuard.isLikelyRepetitive(
                "第一部分介绍背景。\n第二部分给出实现步骤。\n最后总结注意事项。"));
    }
}
