package com.aichat.guard;

import com.aichat.exception.NotFoundException;

public final class Authz {
    private Authz(){}

    public static void assertOwner(Long currentUserId, Long resourceUserId){
        if (!currentUserId.equals(resourceUserId)) {
            throw new NotFoundException("无权访问该资源");
        }
    }
}
