package com.olehshklyar.lodestar.client;

import com.olehshklyar.lodestar.service.RegionMappingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionNormalizerTest {

    @Mock
    private RegionMappingService regionMappingService;

    @InjectMocks
    private RegionNormalizer normalizer;

    @Test
    @DisplayName("Should delegate normalization calls to RegionMappingService")
    void shouldDelegateToRegionMappingService() {
        when(regionMappingService.normalize("м. Київ")).thenReturn("KYIV_REGION");

        String result = normalizer.normalize("м. Київ");

        assertThat(result).isEqualTo("KYIV_REGION");
        verify(regionMappingService).normalize("м. Київ");
    }
}
