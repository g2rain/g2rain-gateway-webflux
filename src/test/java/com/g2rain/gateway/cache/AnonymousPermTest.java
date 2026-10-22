package com.g2rain.gateway.cache;

import com.g2rain.common.enums.SessionType;
import com.g2rain.gateway.client.BasisServiceClient;
import com.g2rain.gateway.model.route.SessionApiPermissionVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnonymousPermTest {

    private static final Long ORGAN_ID = 20001L;

    private BasisServiceClient basisServiceClient;
    private AnonymousPerm anonymousPerm;

    @BeforeEach
    void setUp() {
        basisServiceClient = mock(BasisServiceClient.class);
        anonymousPerm = new AnonymousPerm(basisServiceClient);
        anonymousPerm.delete(ORGAN_ID);
    }

    @Test
    void hasApiPermission_requiresActiveEntryAndOpenedApi() {
        when(basisServiceClient.getSessionApiPermissions(SessionType.ANONYMOUS.name(), ORGAN_ID))
            .thenReturn(Mono.just(snapshot(List.of(42L), List.of(5L))));

        assertTrue(Boolean.TRUE.equals(anonymousPerm.hasApiPermission(ORGAN_ID, 5L, 42L).block()));
        assertFalse(Boolean.TRUE.equals(anonymousPerm.hasApiPermission(ORGAN_ID, 6L, 42L).block()));
        assertFalse(Boolean.TRUE.equals(anonymousPerm.hasApiPermission(ORGAN_ID, 5L, 99L).block()));
        assertFalse(Boolean.TRUE.equals(anonymousPerm.hasApiPermission(ORGAN_ID, null, 42L).block()));
        verify(basisServiceClient, times(1)).getSessionApiPermissions(SessionType.ANONYMOUS.name(), ORGAN_ID);
    }

    @Test
    void delete_shouldReloadSnapshotSoDisabledEntryIsRejected() {
        when(basisServiceClient.getSessionApiPermissions(SessionType.ANONYMOUS.name(), ORGAN_ID))
            .thenReturn(Mono.just(snapshot(List.of(42L), List.of(5L))))
            .thenReturn(Mono.just(snapshot(List.of(42L), List.of())));

        assertTrue(Boolean.TRUE.equals(anonymousPerm.hasApiPermission(ORGAN_ID, 5L, 42L).block()));

        // 模拟收到 ANONYMOUS_PERM：接入码 5 被停用
        anonymousPerm.delete(ORGAN_ID);

        assertFalse(Boolean.TRUE.equals(anonymousPerm.hasApiPermission(ORGAN_ID, 5L, 42L).block()));
        verify(basisServiceClient, times(2)).getSessionApiPermissions(SessionType.ANONYMOUS.name(), ORGAN_ID);
    }

    private static SessionApiPermissionVo snapshot(List<Long> apiIds, List<Long> entryIds) {
        SessionApiPermissionVo vo = new SessionApiPermissionVo();
        vo.setOrganId(ORGAN_ID);
        vo.setVersion(1L);
        vo.setApiIds(apiIds);
        vo.setEntryIds(entryIds);
        return vo;
    }
}
