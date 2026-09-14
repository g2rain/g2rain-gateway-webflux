package com.g2rain.gateway.cache;

import com.g2rain.common.enums.SessionType;
import com.g2rain.gateway.client.BasisServiceClient;
import com.g2rain.gateway.model.cache.MemberOrganPermission;
import com.g2rain.gateway.model.route.SessionApiPermissionVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberPermTest {

    private BasisServiceClient basisServiceClient;
    private MemberPerm memberPerm;

    @BeforeEach
    void setUp() {
        basisServiceClient = mock(BasisServiceClient.class);
        memberPerm = new MemberPerm(basisServiceClient);
    }

    @Test
    void getOrLoad_shouldCacheAndInvalidateByOrgan() {
        SessionApiPermissionVo vo = new SessionApiPermissionVo();
        vo.setOrganId(10001L);
        vo.setVersion(3L);
        vo.setApiIds(List.of(42L, 43L));
        when(basisServiceClient.getSessionApiPermissions(SessionType.MEMBER.name(), 10001L))
            .thenReturn(Mono.just(vo));

        MemberOrganPermission first = memberPerm.getOrLoad(10001L).block();
        MemberOrganPermission second = memberPerm.getOrLoad(10001L).block();
        assertEquals(first, second);
        verify(basisServiceClient, times(1))
            .getSessionApiPermissions(SessionType.MEMBER.name(), 10001L);

        assertTrue(Boolean.TRUE.equals(memberPerm.hasApiPermission(10001L, 42L).block()));
        assertFalse(Boolean.TRUE.equals(memberPerm.hasApiPermission(10001L, 99L).block()));

        memberPerm.delete(10001L);
        when(basisServiceClient.getSessionApiPermissions(SessionType.MEMBER.name(), 10001L))
            .thenReturn(Mono.just(vo));
        memberPerm.getOrLoad(10001L).block();
        verify(basisServiceClient, times(2))
            .getSessionApiPermissions(SessionType.MEMBER.name(), 10001L);
    }
}
